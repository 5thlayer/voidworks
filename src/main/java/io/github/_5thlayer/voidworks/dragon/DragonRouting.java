// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellHatches;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Runs the Void Dragons' {@link Routing} on the server, a step every {@link #INTERVAL} server ticks,
 * timed by the Overworld's game time, with no chunk loaded: a Well is found in the server's
 * {@link VoidWells}, and what it holds in its dimension's networks, which are saved data, not blocks
 * (ADR 0005): its whole network's motes (ADR 0006). An owner calls a dragon once they have stood on
 * the rim of a Well of theirs for a step, and is told above the hotbar while one is coming, with its
 * dive shown as a bar ({@link CallPayload}), and once one waits there for them; they ride
 * one while its presence carries them at its stop ({@link DragonRiding}).
 */
public final class DragonRouting {

    /** Ticks between steps, each ending every leg that has ended by then. */
    private static final int INTERVAL = 20;

    /**
     * Where each owner stood on the rim of a Well of theirs at the last step: still there at the next,
     * a second on, they call a dragon there. Kept only while the server runs.
     */
    private static Map<UUID, Place> onRimLastStep = Map.of();

    /** The game time each dragon on its way to its owner's call set off, which its owner's bar fills from. Kept only while the server runs. */
    private static Map<UUID, Long> callSetOff = Map.of();

    private DragonRouting() {
    }

    public static void register(IEventBus modBus) {
        // Optional, so a client without Voidworks can still join; it shows nothing it was not sent.
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").optional()
                .playToClient(CallPayload.TYPE, CallPayload.STREAM_CODEC)
                .playToClient(PassingShown.TYPE, PassingShown.STREAM_CODEC));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % INTERVAL == 0) {
                step(event.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            onRimLastStep = Map.of();
            callSetOff = Map.of();
        });
    }

    /**
     * One step of every dragon's routing, at the Overworld's game time now; an owner calling at a Well a
     * dragon dives to is told it is coming, and shown its dive as a bar, and one a dragon waits at is
     * told it waits. A dragon that dies of old age no longer counts toward its
     * owner's next egg, and its presence plays its death; nothing else tells its owner.
     */
    public static void step(MinecraftServer server) {
        var dragons = SavedVoidDragons.of(server);
        var standing = standingOnRims(server);
        var owners = new ServerOwners(server, stillStanding(standing));
        onRimLastStep = standing;
        for (var died : routing(server, owners).step(dragons, server.overworld().getGameTime())) {
            SavedHatchery.of(server).lose(died.owner());
            DragonPresences.die(server, died);
        }
        long now = server.overworld().getGameTime();
        var setOff = new HashMap<UUID, Long>();
        for (var dragon : dragons.all()) {
            var waiting = dragon.trip().filter(leg -> leg.leg() == Trip.Leg.WAITING).flatMap(Trip::added);
            if (waiting.isPresent() && owners.calling(dragon.owner()).equals(waiting)) {
                player(server, dragon.owner()).ifPresent(player -> player.sendOverlayMessage(Component.translatable("voidworks.dragon.waits")));
            }
            var trip = dragon.trip().filter(leg -> leg.leg() == Trip.Leg.TO_CALL);
            if (trip.isEmpty()) {
                continue;
            }
            long from = callSetOff.getOrDefault(dragon.id(), now);
            setOff.put(dragon.id(), from);
            var coming = trip.get().added().orElseThrow();
            if (owners.calling(dragon.owner()).equals(Optional.of(coming))) {
                player(server, dragon.owner()).ifPresent(player -> {
                    player.sendOverlayMessage(Component.translatable("voidworks.dragon.coming"));
                    if (player instanceof ServerPlayer caller) {
                        PacketDistributor.sendToPlayer(caller, new CallPayload(from, trip.get().until(), DiveColour.of(server, coming.dimension())));
                    }
                });
            }
        }
        callSetOff = setOff;
    }

    /**
     * Its owner, riding the dragon {@code id} at a stop of theirs, picks their Well at {@code picked}:
     * it dives there now, carrying them ({@link Routing#ride}).
     *
     * @return what came of it
     */
    public static Routing.Ride ride(MinecraftServer server, UUID id, Place picked) {
        // A ride reads no one's call: picking a Well is the rider's alone.
        return routing(server, new ServerOwners(server, Map.of())).ride(SavedVoidDragons.of(server), id, picked, server.overworld().getGameTime());
    }

    /** @return the places of {@code owner}'s Wells that are whole and awake, the only ones a dragon stops at, in the order they were listed */
    public static List<Place> wellsOf(MinecraftServer server, UUID owner) {
        return new ServerWells(server).of(owner).stream().map(Wells.Well::place).toList();
    }

    /**
     * @return how many stacks the provider {@code hatch} holds, and for how many of them its network can
     *         pay the Toll ({@link Routing#payable}); empty for a requester, or while its Well is not whole
     *         and awake or not its owner's
     */
    public static Optional<Payable> payable(MinecraftServer server, WellHatches.Hatch hatch) {
        return hatch.well().flatMap(well -> SavedVoidWells.of(server).at(well)
                .filter(entry -> hatch.owner().map(NameAndId::id).filter(entry.owner()::equals).isPresent())
                .flatMap(entry -> routing(server, new ServerOwners(server, Map.of())).payable(entry.owner(), well, hatch.place())));
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
        var dive = new Dive(VoidworksConfig.dragonDiveTicks(), VoidworksConfig.dragonDiveBlocksPerSecond(), VoidworksConfig.dragonRiddenDiveTicks(), dimension -> {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            return level == null ? 1.0 : level.dimensionType().coordinateScale();
        });
        return new Routing(new ServerWells(server), new ServerHatches(server), owners, dive, harm(server), VoidworksConfig.dragonLoad(),
                VoidworksConfig.dragonMatterLoad(), Toll.of(VoidworksBlocks.WELL_HATCH.get().defaultBlockState()), VoidworksConfig.dragonStopTicks(),
                VoidworksConfig.life(Line.MOTE), VoidworksConfig.life(Line.MATTER));
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

    /** @return of {@code standing}, those who stood on the same rim at the last step too, a second ago: who calls where */
    private static Map<UUID, Place> stillStanding(Map<UUID, Place> standing) {
        var calling = new HashMap<UUID, Place>();
        standing.forEach((owner, place) -> {
            if (place.equals(onRimLastStep.get(owner))) {
                calling.put(owner, place);
            }
        });
        return calling;
    }

    /**
     * @return where each player on {@code server} stands on the rim of a Well of theirs, in no vehicle
     *         and not spectating
     */
    private static Map<UUID, Place> standingOnRims(MinecraftServer server) {
        var standing = new HashMap<UUID, Place>();
        var wells = SavedVoidWells.of(server);
        for (var level : server.getAllLevels()) {
            for (var player : level.players()) {
                if (player.isSpectator() || player.isPassenger()) {
                    continue;
                }
                var feet = player.blockPosition();
                wells.owned(player.getUUID()).stream()
                        .map(VoidWells.Entry::place)
                        .filter(place -> place.dimension().equals(level.dimension().identifier()) && level.isLoaded(place.pos()))
                        .filter(place -> {
                            var core = level.getBlockState(place.pos());
                            return core.is(VoidworksBlocks.VOID_WELL.get()) && WellStructure.onRim(place.pos(), core.getValue(VoidWellBlock.FACING), feet);
                        })
                        .findFirst()
                        .ifPresent(place -> standing.put(player.getUUID(), place));
            }
        }
        return standing;
    }

    /**
     * The server's owners: one calls at the Well whose rim they have stood on since the last step, as
     * {@code calling} holds, and rides a dragon while its presence carries them at its stop.
     */
    private record ServerOwners(MinecraftServer server, Map<UUID, Place> calling) implements Owners {

        @Override
        public Optional<Place> calling(UUID owner) {
            return Optional.ofNullable(calling.get(owner));
        }

        @Override
        public boolean riding(UUID dragonId) {
            return DragonPresences.ridden(server, dragonId);
        }
    }

    /**
     * The server's Well hatches, from its saved data: a Well's are those its owner placed against it, so
     * a dragon loads and unloads them with no chunk loaded.
     */
    private record ServerHatches(MinecraftServer server) implements Hatches {

        @Override
        public List<Hatch> on(Place well) {
            var owner = SavedVoidWells.of(server).at(well).map(VoidWells.Entry::owner);
            if (owner.isEmpty()) {
                return List.of();
            }
            return SavedWellHatches.of(server).on(well).stream()
                    .filter(hatch -> hatch.owner().map(NameAndId::id).equals(owner))
                    .map(this::seen)
                    .toList();
        }

        @Override
        public long take(Place hatch, Ware ware) {
            return SavedWellHatches.of(server).at(hatch).map(found -> move(ware, (resource, amount, transaction) ->
                    found.contents().extract(resource, amount, transaction))).orElse(0L);
        }

        @Override
        public long put(Place hatch, Ware ware) {
            return SavedWellHatches.of(server).at(hatch).map(found -> move(ware, (resource, amount, transaction) ->
                    found.contents().insert(resource, amount, transaction))).orElse(0L);
        }

        /** @return {@code hatch} as a dragon sees it: what it asks for and what it holds, items alike together */
        private Hatch seen(WellHatches.Hatch hatch) {
            var registries = server.registryAccess();
            var contents = new ArrayList<Ware>();
            int empty = 0;
            for (var stack : hatch.contents().copyToList()) {
                if (stack.isEmpty()) {
                    empty++;
                } else {
                    contents.add(StackWares.of(stack, registries));
                }
            }
            var request = hatch.request().map(asked -> new Request(BuiltInRegistries.ITEM.getKey(asked.item()),
                    new ItemStack(asked.item()).getMaxStackSize(), asked.stacks()));
            return new Hatch(hatch.place(), hatch.mode() == WellHatches.Mode.REQUESTER, request, new MatterLoad(contents).wares(), empty);
        }

        private long move(Ware ware, ItemMove move) {
            if (ware.count() == 0) {
                return 0;
            }
            try (var transaction = Transaction.openRoot()) {
                long moved = move.of(StackWares.resource(ware, server.registryAccess()), (int) Math.min(Integer.MAX_VALUE, ware.count()), transaction);
                transaction.commit();
                return moved;
            }
        }

        private interface ItemMove {
            int of(ItemResource resource, int amount, TransactionContext transaction);
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

        /** Loaded while its core's chunk is loaded and ticking entities. */
        @Override
        public boolean loaded(Place place) {
            var level = place.level(server);
            // Where its death shows too, so a dragon never dies unseen.
            return level != null && level.isPositionEntityTicking(place.pos()) && DragonPresences.showsAt(level, place.pos());
        }

        /** Pops the egg out of the middle of the Well's shaft, while its chunk is loaded and ticking. */
        @Override
        public boolean lay(Place place, Line line) {
            var level = place.level(server);
            if (level == null || !level.isPositionEntityTicking(place.pos())) {
                return false;
            }
            var core = level.getBlockState(place.pos());
            if (!core.is(VoidworksBlocks.VOID_WELL.get())) {
                return false;
            }
            var shaft = Vec3.atCenterOf(WellStructure.shaft(place.pos(), core.getValue(VoidWellBlock.FACING)));
            var egg = new ItemEntity(level, shaft.x, shaft.y, shaft.z, new ItemStack(EggKind.laidBy(line).block()));
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
