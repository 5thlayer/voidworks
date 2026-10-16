// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/**
 * The butterflies drifting in a Void Well's shaft, under its fluid, while a player is near: as many as
 * its fill shows ({@link io.github._5thlayer.voidworks.well.ShaftFill#butterflies}), each of a grade
 * picked by the shares of the grades its network holds. Each drifts a while and fades, and new ones
 * appear as often as keeps about that many in the shaft. A look, not a count.
 */
final class ShaftButterflies {

    /** How near a player must be, in blocks, to see a shaft's butterflies. */
    private static final double SEEN_WITHIN = 32;

    private ShaftButterflies() {
    }

    /** Lets {@code well}'s butterflies drift for a tick, with {@code sprites}' frames. */
    static void tick(VoidWellBlockEntity well, SpriteSet sprites) {
        var minecraft = Minecraft.getInstance();
        var shown = well.shaft();
        int butterflies = shown.butterflies();
        if (butterflies == 0 || !(well.getLevel() instanceof ClientLevel level) || minecraft.player == null
                || !minecraft.player.position().closerThan(well.getBlockPos().getCenter(), SEEN_WITHIN)) {
            return;
        }
        // One appears each tick by the chance that keeps as many as it shows alive at once.
        var random = level.getRandom();
        if (random.nextInt(ButterflyParticle.DRIFT_LIFETIME) >= butterflies) {
            return;
        }
        var room = WellStructure.fluid(well.getBlockPos(), well.getBlockState().getValue(VoidWellBlock.FACING));
        ButterflyParticle.drift(level, room, () -> room.minY + room.getYsize() * well.shownLevel(level.getGameTime(), 0),
                shown.grade(random.nextInt(Integer.MAX_VALUE)), sprites, minecraft.particleEngine::add);
    }
}
