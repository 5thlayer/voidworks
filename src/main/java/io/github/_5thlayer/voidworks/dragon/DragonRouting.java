// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.network.MoteNetwork;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidNetworks;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Runs the Void Dragons' {@link Routing} on the server, a step every {@link #INTERVAL} server ticks,
 * timed by the Overworld's game time, with no chunk loaded: a Well is found in the server's
 * {@link VoidWells}, and what it holds in its dimension's networks, which are saved data, not blocks
 * (ADR 0005): its whole network's motes (ADR 0006). An owner calls a dragon while they stand in the
 * shaft of a Well of theirs, and is told above the hotbar while one is coming; they ride one while its
 * presence carries them at its stop ({@link DragonRiding}).
 */
public final class DragonRouting {

    /** Ticks between steps, each ending every leg that has ended by then. */
    private static final int INTERVAL = 20;

    private DragonRouting() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % INTERVAL == 0) {
                step(event.getServer());
            }
        });
    }

    /**
     * One step of every dragon's routing, at the Overworld's game time now; an owner calling at a Well a
     * dragon dives to is told it is coming. A dragon that dies of old age no longer counts toward its
     * owner's next egg, and its presence plays its death; nothing else tells its owner.
     */
    public static void step(MinecraftServer server) {
        var dragons = SavedVoidDragons.of(server);
        var owners = new ServerOwners(server);
        for (var died : routing(server, owners).step(dragons, server.overworld().getGameTime())) {
            SavedHatchery.of(server).die(died.owner());
            DragonPresences.die(server, died);
        }
        for (var dragon : dragons.all()) {
            var coming = dragon.trip().filter(trip -> trip.leg() == Trip.Leg.TO_CALL).flatMap(Trip::added);
            if (coming.isPresent() && owners.calling(dragon.owner()).equals(coming)) {
                player(server, dragon.owner()).ifPresent(player -> player.sendOverlayMessage(Component.translatable("voidworks.dragon.coming")));
            }
        }
    }

    /**
     * Its owner, riding the dragon {@code id} at a stop of theirs, picks their Well at {@code picked}:
     * it dives there now, carrying them ({@link Routing#ride}).
     *
     * @return the dragon on its way; empty when it cannot dive there
     */
    public static Optional<VoidDragonRecord> ride(MinecraftServer server, UUID id, Place picked) {
        return routing(server, new ServerOwners(server)).ride(SavedVoidDragons.of(server), id, picked, server.overworld().getGameTime());
    }

    /** @return the places of {@code owner}'s Wells that are whole and awake, the only ones a dragon stops at, in the order they were listed */
    public static List<Place> wellsOf(MinecraftServer server, UUID owner) {
        return new ServerWells(server).of(owner).stream().map(Wells.Well::place).toList();
    }

    /** @return {@code owner}, in whichever of the server's levels they are */
    private static Optional<Player> player(MinecraftServer server, UUID owner) {
        for (var level : server.getAllLevels()) {
            var player = level.getPlayerByUUID(owner);
            if (player != null) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    /** @return the dragons' routing on {@code server}, its Wells read from the server and its owners from {@code owners}, at the configured numbers */
    private static Routing routing(MinecraftServer server, Owners owners) {
        var dive = new Dive(VoidworksConfig.dragonDiveTicks(), VoidworksConfig.dragonDiveBlocksPerSecond(), dimension -> {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            return level == null ? 1.0 : level.dimensionType().coordinateScale();
        });
        return new Routing(new ServerWells(server), owners, dive, harm(server), VoidworksConfig.dragonLoad(), VoidworksConfig.dragonStopTicks(),
                VoidworksConfig.dragonEggMotes(), VoidworksConfig.lifeStages());
    }

    /** A dragon's {@link Harm} on {@code server}: each dimension's pressure, by the {@link VoidPressure} map, against the End's. */
    private static Harm harm(MinecraftServer server) {
        int end = wellPressure(server, Level.END);
        return new Harm(dimension -> Math.max(0, end - wellPressure(server, ResourceKey.create(Registries.DIMENSION, dimension))),
                VoidworksConfig.dragonWeakenTicks(), VoidworksConfig.dragonHealMotes());
    }

    /** @return the Void Pressure where a Well in {@code dimension} stands, loaded or not */
    private static int wellPressure(MinecraftServer server, ResourceKey<Level> dimension) {
        // A core is set in a wall's middle course, with casing beneath it, so never over the open void.
        return VoidPressure.dimension(server.registryAccess(), dimension).pressure(() -> false);
    }

    /**
     * The server's owners: one calls while they stand in the shaft of a Well of theirs, in no vehicle
     * and not spectating, and rides a dragon while its presence carries them at its stop.
     */
    private record ServerOwners(MinecraftServer server) implements Owners {

        @Override
        public Optional<Place> calling(UUID owner) {
            var found = player(server, owner).filter(player -> !player.isSpectator() && !player.isPassenger());
            if (found.isEmpty()) {
                return Optional.empty();
            }
            var level = found.get().level();
            var feet = found.get().blockPosition();
            return SavedVoidWells.of(server).owned(owner).stream()
                    .map(VoidWells.Entry::place)
                    .filter(place -> place.dimension().equals(level.dimension().identifier()) && level.isLoaded(place.pos()))
                    .filter(place -> {
                        var core = level.getBlockState(place.pos());
                        return core.is(VoidworksBlocks.VOID_WELL.get()) && WellStructure.inShaft(place.pos(), core.getValue(VoidWellBlock.FACING), feet);
                    })
                    .findFirst();
        }

        @Override
        public boolean riding(UUID dragonId) {
            return DragonPresences.ridden(server, dragonId);
        }
    }

    /**
     * The server's Wells: an owner's, listed with their targets, each whole and awake while its network
     * holds its store. Each is its whole network, its core and every Voidstone together (ADR 0006).
     */
    private record ServerWells(MinecraftServer server) implements Wells {

        @Override
        public List<Well> of(UUID owner) {
            var wells = new ArrayList<Well>();
            for (var entry : SavedVoidWells.of(server).owned(owner)) {
                var level = entry.place().level(server);
                if (level == null) {
                    continue;
                }
                networks(entry.place()).flatMap(networks -> networks.at(entry.place().pos())).ifPresent(network ->
                        wells.add(new Well(entry.place(), entry.target().motes(network.capacity()), wellPressure(server, level.dimension()),
                                network.capacity(), network.countByGrade(), wellsOn(network, level))));
            }
            return wells;
        }

        /** @return the places of every Well on {@code network} in {@code level}, whoever's */
        private static Set<Place> wellsOn(MoteNetwork network, ServerLevel level) {
            return network.stores().stream()
                    .filter(store -> store.kind() == StoreKind.WELL)
                    .map(store -> Place.of(level, store.pos()))
                    .collect(Collectors.toSet());
        }

        /** Takes out of the Well's core first, then out of the Voidstone of its network nearest it. */
        @Override
        public long take(Place place, int grade, long count) {
            return networks(place).map(networks -> move(count, (amount, transaction) ->
                    networks.draw(place.pos(), MoteResource.of(grade), amount, transaction))).orElse(0L);
        }

        /** Puts into the Well's core, and only once it is full into the Voidstone of its network nearest it. */
        @Override
        public long put(Place place, int grade, long count) {
            return networks(place).map(networks -> move(count, (amount, transaction) ->
                    networks.deposit(place.pos(), MoteResource.of(grade), amount, transaction))).orElse(0L);
        }

        /** Pops the egg out of the middle of the Well's shaft, while its chunk is loaded and ticking. */
        @Override
        public boolean lay(Place place) {
            var level = place.level(server);
            if (level == null || !level.isPositionEntityTicking(place.pos())) {
                return false;
            }
            var core = level.getBlockState(place.pos());
            if (!core.is(VoidworksBlocks.VOID_WELL.get())) {
                return false;
            }
            var shaft = Vec3.atCenterOf(WellStructure.shaft(place.pos(), core.getValue(VoidWellBlock.FACING)));
            var egg = new ItemEntity(level, shaft.x, shaft.y, shaft.z, new ItemStack(VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get()));
            egg.setDefaultPickUpDelay();
            return level.addFreshEntity(egg);
        }

        /** @return the networks of the Well's dimension, while it is whole and awake in one of them */
        private Optional<VoidNetworks> networks(Place place) {
            var level = place.level(server);
            if (level == null) {
                return Optional.empty();
            }
            var networks = VoidstoneNetworks.of(level);
            return networks.store(place.pos()).filter(store -> store.kind() == StoreKind.WELL).map(store -> networks);
        }

        private static long move(long count, Move move) {
            try (var transaction = Transaction.openRoot()) {
                long moved = move.of((int) Math.min(Integer.MAX_VALUE, count), transaction);
                transaction.commit();
                return moved;
            }
        }

        private interface Move {
            int of(int amount, TransactionContext transaction);
        }
    }
}
