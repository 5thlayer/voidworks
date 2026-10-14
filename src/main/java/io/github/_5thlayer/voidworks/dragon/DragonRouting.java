// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.network.MoteNetwork;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidNetworks;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Runs the Void Dragons' {@link Routing} on the server, a step every {@link #INTERVAL} server ticks,
 * timed by the Overworld's game time, with no chunk loaded: a Well is found in the server's
 * {@link io.github._5thlayer.voidworks.well.VoidWells}, and what it holds in its dimension's
 * networks, which are saved data, not blocks (ADR 0005): its whole network's motes (ADR 0006).
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

    /** One step of every dragon's routing, at the Overworld's game time now. */
    public static void step(MinecraftServer server) {
        var dive = new Dive(VoidworksConfig.dragonDiveTicks(), VoidworksConfig.dragonDiveBlocksPerSecond(), dimension -> {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            return level == null ? 1.0 : level.dimensionType().coordinateScale();
        });
        new Routing(new ServerWells(server), dive, harm(server), VoidworksConfig.dragonLoad(), VoidworksConfig.dragonStopTicks())
                .step(SavedVoidDragons.of(server), server.overworld().getGameTime());
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
