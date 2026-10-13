// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github._5thlayer.voidworks.pressure.DimensionPressure;
import org.junit.jupiter.api.Test;

/** How long a Void Crucible takes over one result: from the Rung's cost, the Void Pressure and the Crucibles sharing its rate. */
class CrucibleTimeTest {

    /** The spec's provisional ticks per void energy at the End's pressure. */
    private static final CrucibleTime TIME = new CrucibleTime(30);

    @Test
    void aHundredCostRungTakesTwoAndAHalfMinutesAtTheEndsPressure() {
        assertEquals(3000, TIME.ticks(100, 3, 1));
    }

    @Test
    void eachStepOfPressureBelowTheEndsDoublesTheTime() {
        assertEquals(6000, TIME.ticks(100, 2, 1), "five minutes in the Overworld");
        assertEquals(12000, TIME.ticks(100, 1, 1), "ten minutes in the Nether");
    }

    @Test
    void eachStepAboveTheEndsHalvesItAgain() {
        assertEquals(1500, TIME.ticks(100, 4, 1), "over the End's open void");
    }

    @Test
    void theTimeIsTheCostTimesTheTicksPerVoidEnergy() {
        assertEquals(30 * 7, TIME.ticks(7, 3, 1));
        assertEquals(60, new CrucibleTime(20).ticks(3, 3, 1));
    }

    @Test
    void eachWorkingCrucibleSharingTheRateMultipliesTheTime() {
        assertEquals(12000, TIME.ticks(100, 2, 2));
        assertEquals(3 * 6000, TIME.ticks(100, 2, 3));
    }

    @Test
    void aFreeRungStillTakesATick() {
        assertEquals(1, TIME.ticks(0, 3, 1));
        assertEquals(1, TIME.ticks(1, 9, 1));
    }

    @Test
    void theReferenceIsTheEndsShippedPressure() {
        assertEquals(DimensionPressure.shippedEntries().get("minecraft:the_end").base(), CrucibleTime.END_PRESSURE);
    }
}
