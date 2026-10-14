// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.function.ToDoubleFunction;

import net.minecraft.resources.Identifier;

/**
 * How long a Void Dragon's dive through the void between two Wells takes: a fixed time, and a time
 * per block of distance across the ground in Overworld blocks, where a dimension's block counts its
 * coordinate scale, the Nether's eight and the End's one. Height counts nothing. A stop at the Well
 * it is already at is no dive.
 *
 * @param fixedTicks what any dive takes
 * @param blocksPerSecond how many Overworld blocks of distance add a second
 * @param scale each dimension's Overworld blocks to one of its own, by its id
 */
public record Dive(int fixedTicks, int blocksPerSecond, ToDoubleFunction<Identifier> scale) {

    private static final int TICKS_PER_SECOND = 20;

    /** @return the ticks a dive from {@code from} to {@code to} takes; none from a place to itself */
    public long ticks(Place from, Place to) {
        if (from.equals(to)) {
            return 0;
        }
        double fromScale = scale.applyAsDouble(from.dimension());
        double toScale = scale.applyAsDouble(to.dimension());
        double dx = from.pos().getX() * fromScale - to.pos().getX() * toScale;
        double dz = from.pos().getZ() * fromScale - to.pos().getZ() * toScale;
        return fixedTicks + (long) Math.ceil(Math.sqrt(dx * dx + dz * dz) * TICKS_PER_SECOND / blocksPerSecond);
    }
}
