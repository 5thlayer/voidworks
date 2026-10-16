// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.function.ToDoubleFunction;

import net.minecraft.resources.Identifier;

/**
 * How long a Void Dragon's dive through the void between two Wells takes: a time per block of
 * distance across the ground in Overworld blocks, where a dimension's block counts its coordinate
 * scale, the Nether's eight and the End's one, and a fixed time more between dimensions. Height
 * counts nothing. A dive takes at least {@link #MINIMUM_TICKS}, so even between near Wells its
 * presence is seen diving into one shaft and emerging from the other. A stop at the Well it is
 * already at is no dive. A ridden dive takes no more than {@link #riddenTicks}, nor less than the least.
 *
 * @param betweenDimensionsTicks what a dive between dimensions takes more
 * @param blocksPerSecond how many Overworld blocks of distance add a second
 * @param riddenTicks the most a dive with a rider aboard takes
 * @param scale each dimension's Overworld blocks to one of its own, by its id
 */
public record Dive(int betweenDimensionsTicks, int blocksPerSecond, int riddenTicks, ToDoubleFunction<Identifier> scale) {

    /** A dive whose rider makes it no shorter. */
    public Dive(int betweenDimensionsTicks, int blocksPerSecond, ToDoubleFunction<Identifier> scale) {
        this(betweenDimensionsTicks, blocksPerSecond, Integer.MAX_VALUE, scale);
    }

    private static final int TICKS_PER_SECOND = 20;

    /** The least a dive takes: time for a presence to dive away and the next to emerge ({@link DragonPresences}). */
    public static final int MINIMUM_TICKS = 2 * TICKS_PER_SECOND;

    /** @return the ticks a dive from {@code from} to {@code to} takes; none from a place to itself */
    public long ticks(Place from, Place to) {
        if (from.equals(to)) {
            return 0;
        }
        long distance = (long) Math.ceil(blocks(from, to) * TICKS_PER_SECOND / blocksPerSecond);
        long ticks = from.dimension().equals(to.dimension()) ? distance : betweenDimensionsTicks + distance;
        return Math.max(MINIMUM_TICKS, ticks);
    }

    /** @return the ticks a dive that would take {@code ticks} unridden takes with a rider aboard */
    public long ridden(long ticks) {
        return Math.min(ticks, Math.max(MINIMUM_TICKS, riddenTicks));
    }

    /** @return how far apart {@code from} and {@code to} are across the ground, in Overworld blocks */
    double blocks(Place from, Place to) {
        double fromScale = scale.applyAsDouble(from.dimension());
        double toScale = scale.applyAsDouble(to.dimension());
        double dx = from.pos().getX() * fromScale - to.pos().getX() * toScale;
        double dz = from.pos().getZ() * fromScale - to.pos().getZ() * toScale;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
