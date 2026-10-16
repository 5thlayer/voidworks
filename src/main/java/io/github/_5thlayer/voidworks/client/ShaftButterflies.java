// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.github._5thlayer.voidworks.well.ShaftFill;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;

/**
 * The butterflies drifting in a Void Well's shaft, under its fluid, while a player is near: never
 * more than its fill shows ({@link ShaftFill#butterflies}), each of a grade picked by the shares of
 * the grades its network holds. Each drifts a while and fades, and new ones appear, now and then, in
 * their place. A look, not a count.
 */
final class ShaftButterflies {

    /** How near a player must be, in blocks, to see a shaft's butterflies. */
    private static final double SEEN_WITHIN = 32;
    /** The chance, in ticks, that a shaft short of butterflies gains one each tick, so they come one by one. */
    private static final int APPEARS_EVERY = 8;

    /** Each Well's butterflies still drifting, forgotten with the Well. */
    private static final Map<VoidWellBlockEntity, List<Particle>> DRIFTING = new WeakHashMap<>();

    private ShaftButterflies() {
    }

    /** Lets {@code well}'s butterflies drift for a tick, with {@code sprites}' frames. */
    static void tick(VoidWellBlockEntity well, SpriteSet sprites) {
        var drifting = DRIFTING.computeIfAbsent(well, ignored -> new ArrayList<>());
        drifting.removeIf(butterfly -> !butterfly.isAlive());
        var minecraft = Minecraft.getInstance();
        var shown = well.shaft();
        if (drifting.size() >= shown.butterflies() || !(well.getLevel() instanceof ClientLevel level) || minecraft.player == null
                || !minecraft.player.position().closerThan(well.getBlockPos().getCenter(), SEEN_WITHIN)) {
            return;
        }
        var random = level.getRandom();
        if (random.nextInt(APPEARS_EVERY) != 0) {
            return;
        }
        var room = well.fluidRoom();
        var butterfly = ButterflyParticle.drift(level, room, () -> room.minY + room.getYsize() * well.shownLevel(level.getGameTime(), 0),
                shown.grade(random.nextInt(Integer.MAX_VALUE)), sprites);
        if (butterfly != null) {
            drifting.add(butterfly);
            minecraft.particleEngine.add(butterfly);
        }
    }
}
