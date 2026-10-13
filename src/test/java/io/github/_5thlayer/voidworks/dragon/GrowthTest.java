// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Feeding a Dragonling: the void energy a Void Siphon empties into it takes time off its growing, a
 * minute for each configured amount, and it is never fed more than finishing its growing takes.
 */
class GrowthTest {

    private static final long PER_MINUTE = 1000;

    @Test
    void eachMinutesWorthOfEnergyTakesAMinuteOff() {
        assertEquals(Growth.TICKS_PER_MINUTE, Growth.ticksOff(PER_MINUTE, PER_MINUTE));
        assertEquals(3 * Growth.TICKS_PER_MINUTE, Growth.ticksOff(3 * PER_MINUTE, PER_MINUTE));
    }

    @Test
    void energyShortOfAMinuteTakesItsShareOfOne() {
        assertEquals(Growth.TICKS_PER_MINUTE / 2, Growth.ticksOff(PER_MINUTE / 2, PER_MINUTE));
        assertEquals(1, Growth.ticksOff(1, PER_MINUTE), "a part of a tick is no tick: 1 energy is 1.2 ticks");
        assertEquals(0, Growth.ticksOff(0, PER_MINUTE));
    }

    @Test
    void theEnergyToFinishGrowingTakesAllTheTicksLeftOffAndNoMore() {
        for (long ticksLeft : new long[] {1, 7, 1199, 1200, 1201, 48_000}) {
            long energy = Growth.energyToFinish(ticksLeft, PER_MINUTE);
            assertEquals(true, Growth.ticksOff(energy, PER_MINUTE) >= ticksLeft, ticksLeft + " ticks are not finished by " + energy);
            assertEquals(true, Growth.ticksOff(energy - 1, PER_MINUTE) < ticksLeft, ticksLeft + " ticks are finished by less than " + energy);
        }
        assertEquals(0, Growth.energyToFinish(0, PER_MINUTE));
    }

    @Test
    void aHugeFeedingStaysWithinALong() {
        assertEquals(Long.MAX_VALUE, Growth.ticksOff(Long.MAX_VALUE, 1));
    }
}
