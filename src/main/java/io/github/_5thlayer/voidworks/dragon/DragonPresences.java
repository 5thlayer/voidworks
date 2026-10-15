// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * What a player sees of the Void Dragons: each shows one presence, a {@link VoidDragon} that holds
 * no state of its own, where its dragon is while that place is loaded and ticking (ADR 0005). The
 * presence follows what the record shows ({@link Showing}) and never drives it: it emerges from a
 * Well's shaft, first seen at its mouth, as its dragon stops there, flies about the Well while it
 * loads and unloads, lands and sits astride its rim while it rests, and takes off and dives into the
 * shaft and is gone as its dragon sets off. A dragon that rests where it grew up
 * is shown there. A presence is never saved, so a chunk unloading takes it away and loses nothing,
 * and it comes back once the place is loaded again, mid-stop or resting; one dragon never shows two.
 * A presence that carried its rider through a dive is gone once its dragon has stopped, and the one
 * that emerges at the Well it stopped at brings the rider up out of the shaft on its back, wherever
 * that Well is, loaded or not, and sets them down on its rim once out ({@link DragonRiding}).
 */
public final class DragonPresences {

    /** Ticks between looks at what each dragon's presence should show. */
    private static final int INTERVAL = 20;

    /** How far above the middle of a Well's shaft its dragon's presence flies about: in the open air over its rim, with room for its body. */
    public static final int OVER_SHAFT = 4;

    /**
     * How far above the middle of a Well's shaft its mouth is: the first open air over its rim, where
     * an emerging presence is first seen, since its body, far wider than the shaft, would be in the
     * walls lower down.
     */
    public static final int MOUTH = 3;

    /** How far above a Well's shaft's mouth an emerging presence climbs before it flies about at work. */
    public static final int RISE = 4;

    /** How far below where a dragon rests away from any Well its presence dives before it is gone. */
    private static final int DIVE_DEPTH = 8;

    private DragonPresences() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % INTERVAL == 0) {
                show(event.getServer());
            }
        });
    }

    /**
     * Has each dragon's presence follow its record: one shown follows what the record shows, and dives
     * away once its dragon dives or is somewhere else; a dragon not diving, whose place is loaded and
     * ticking and which shows none, its last gone, is shown there; a presence whose dragon is gone
     * is taken away.
     */
    public static void show(MinecraftServer server) {
        var saved = SavedVoidDragons.saved(server);
        long now = server.overworld().getGameTime();
        var dragons = SavedVoidDragons.of(server);
        // A dragon is gone only when a command takes it out; a rider it carried is set down where it was.
        saved.presences.entrySet().removeIf(shown -> {
            if (dragons.get(shown.getKey()).isPresent()) {
                return false;
            }
            var presence = shown.getValue();
            if (presence.carrying() && presence.shownAt().isPresent()) {
                DragonRiding.setDown(server, presence, presence.shownAt().get());
            } else {
                presence.release();
                presence.ejectPassengers();
            }
            presence.discard();
            return true;
        });
        for (var dragon : dragons.all()) {
            var showing = Showing.of(dragon, now, VoidworksConfig.dragonStopTicks());
            var shown = presence(server, dragon.id());
            if (shown.isPresent() && shown.get().carrying() && showing != Showing.DIVING) {
                bringOut(server, saved, dragon, shown.get());
                continue;
            }
            if (shown.isPresent()) {
                if (showing != Showing.DIVING && shown.get().shows(dragon.rest())) {
                    shown.get().follow(showing);
                } else {
                    // It is its dragon's presence until it is gone, so a new one shows only once it has dived away.
                    shown.get().follow(Showing.DIVING);
                }
                continue;
            }
            if (showing == Showing.DIVING) {
                continue;
            }
            var level = dragon.rest().level(server);
            // Reading the Well there would load its chunk, which a dragon never does (ADR 0005).
            if (level == null || !level.isLoaded(dragon.rest().pos())) {
                continue;
            }
            var spot = spot(level, dragon.rest().pos());
            if (!level.isPositionEntityTicking(showing == Showing.EMERGING ? spot.emerges() : spot.over())) {
                continue;
            }
            var presence = VoidworksEntities.VOID_DRAGON.get().create(level, EntitySpawnReason.TRIGGERED);
            if (presence != null) {
                presence.showPresenceOf(dragon, showing, spot);
                level.addFreshEntity(presence);
                saved.presences.put(dragon.id(), presence);
            }
        }
    }

    /**
     * The dive over, the {@code carried} presence is gone, and a new one emerges from the shaft of the
     * Well its {@code dragon} stopped at, its chunk loaded for it if it is not, with the rider on its back;
     * with no such dimension, the rider is set down there as they are.
     */
    private static void bringOut(MinecraftServer server, SavedVoidDragons saved, VoidDragonRecord dragon, VoidDragon carried) {
        var level = dragon.rest().level(server);
        var presence = level == null ? null : VoidworksEntities.VOID_DRAGON.get().create(level, EntitySpawnReason.TRIGGERED);
        if (presence == null) {
            DragonRiding.setDown(server, carried, dragon.rest());
            return;
        }
        var spot = spot(level, dragon.rest().pos());
        presence.showPresenceOf(dragon, Showing.EMERGING, spot);
        level.addFreshEntity(presence);
        saved.presences.put(dragon.id(), presence);
        DragonRiding.handOver(carried, presence, level, spot.emerges());
    }

    /**
     * @return where a presence of a dragon at {@code pos} flies about, emerges, dives and sits: over a Well's shaft, up out of its mouth and down
     *         into it, and astride its rim, or where it rests elsewhere and below, with nowhere to sit
     */
    private static Spot spot(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.is(VoidworksBlocks.VOID_WELL.get())) {
            // A dragon at a Well is at its core, set in its wall: its presence is over the shaft, never in the wall.
            var facing = state.getValue(VoidWellBlock.FACING);
            var shaft = WellStructure.shaft(pos, facing);
            return new Spot(shaft.above(OVER_SHAFT), shaft.above(MOUTH), shaft.above(MOUTH + RISE), shaft.below(), Optional.of(Seat.of(pos, facing)));
        }
        var below = pos.below(DIVE_DEPTH);
        return new Spot(pos, below, pos, below, Optional.empty());
    }

    /**
     * Where a dragon's presence is shown.
     *
     * @param over where it flies about
     * @param emerges where it emerges, first seen
     * @param risen where it has emerged to, and starts to fly about
     * @param under where it dives to
     * @param seat where it sits while its dragon rests, at a Well only
     */
    record Spot(BlockPos over, BlockPos emerges, BlockPos risen, BlockPos under, Optional<Seat> seat) {

        /** Where a presence is shown before it is the presence of a dragon: a Dragonling's, never read. */
        static final Spot NOWHERE = new Spot(BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, Optional.empty());
    }

    /** @return whether the dragon with {@code id} shows a presence its owner rides at its stop, before any dive */
    public static boolean ridden(MinecraftServer server, UUID id) {
        return presence(server, id).filter(VoidDragon::ridden).isPresent();
    }

    /** @return the presence the dragon with {@code id} shows now, if it shows one */
    public static Optional<VoidDragon> presence(MinecraftServer server, UUID id) {
        var presence = SavedVoidDragons.saved(server).presences.get(id);
        return presence == null || presence.isRemoved() ? Optional.empty() : Optional.of(presence);
    }
}
