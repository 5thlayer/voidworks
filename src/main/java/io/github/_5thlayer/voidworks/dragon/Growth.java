// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.math.RoundingMode;

import com.google.common.math.LongMath;

/**
 * How feeding speeds a Dragonling's growing, as plain logic with no Minecraft types: the void energy
 * a Void Siphon empties into it takes a minute off for each configured amount, its share of one for
 * less.
 */
public final class Growth {

    /** A minute of game time, at twenty ticks a second. */
    public static final long TICKS_PER_MINUTE = 1200;

    private Growth() {
    }

    /**
     * @param energyPerMinute the void energy that takes a minute off, above 0
     * @return the ticks {@code energy} takes off its growing, rounded down, at most {@link Long#MAX_VALUE}
     */
    public static long ticksOff(long energy, long energyPerMinute) {
        return LongMath.saturatedMultiply(energy, TICKS_PER_MINUTE) / energyPerMinute;
    }

    /** @return the least void energy that takes {@code ticksLeft} off its growing: what feeding it to the end takes */
    public static long energyToFinish(long ticksLeft, long energyPerMinute) {
        return LongMath.divide(LongMath.saturatedMultiply(ticksLeft, energyPerMinute), TICKS_PER_MINUTE, RoundingMode.CEILING);
    }
}
