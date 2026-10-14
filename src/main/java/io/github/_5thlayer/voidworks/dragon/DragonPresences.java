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
 * Well's shaft as its dragon stops there, flies about the Well while it loads, unloads and rests,
 * and dives into the shaft and is gone as its dragon sets off. A dragon that rests where it grew up
 * is shown there. A presence is never saved, so a chunk unloading takes it away and loses nothing,
 * and it comes back once the place is loaded again, mid-stop or resting; one dragon never shows two.
 */
public final class DragonPresences {

    /** Ticks between looks at what each dragon's presence should show. */
    private static final int INTERVAL = 20;

    /** How far above the middle of a Well's shaft its dragon's presence flies about: in the open air over its rim, with room for its body. */
    public static final int OVER_SHAFT = 4;

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
     * ticking and which shows none, its last gone, is shown there.
     */
    public static void show(MinecraftServer server) {
        var saved = SavedVoidDragons.saved(server);
        long now = server.overworld().getGameTime();
        for (var dragon : SavedVoidDragons.of(server).all()) {
            var showing = Showing.of(dragon, now, VoidworksConfig.dragonStopTicks());
            var shown = presence(server, dragon.id());
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
            if (level == null) {
                continue;
            }
            var spot = spot(level, dragon.rest().pos());
            if (!level.isPositionEntityTicking(showing == Showing.EMERGING ? spot.under() : spot.over())) {
                continue;
            }
            var presence = VoidworksEntities.VOID_DRAGON.get().create(level, EntitySpawnReason.TRIGGERED);
            if (presence != null) {
                presence.showPresenceOf(dragon, showing, spot.over(), spot.under());
                level.addFreshEntity(presence);
                saved.presences.put(dragon.id(), presence);
            }
        }
    }

    /** @return where a presence of a dragon at {@code pos} flies about, and where it emerges from and dives to: over a Well's shaft and into it, or where it rests elsewhere and below */
    private static Spot spot(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.is(VoidworksBlocks.VOID_WELL.get())) {
            // A dragon at a Well is at its core, set in its wall: its presence is over the shaft, never in the wall.
            var shaft = WellStructure.shaft(pos, state.getValue(VoidWellBlock.FACING));
            return new Spot(shaft.above(OVER_SHAFT), shaft.below());
        }
        return new Spot(pos, pos.below(DIVE_DEPTH));
    }

    /**
     * Where a dragon's presence is shown.
     *
     * @param over where it flies about
     * @param under where it emerges from and dives to
     */
    private record Spot(BlockPos over, BlockPos under) {
    }

    /** @return the presence the dragon with {@code id} shows now, if it shows one */
    public static Optional<VoidDragon> presence(MinecraftServer server, UUID id) {
        var presence = SavedVoidDragons.saved(server).presences.get(id);
        return presence == null || presence.isRemoved() ? Optional.empty() : Optional.of(presence);
    }
}
