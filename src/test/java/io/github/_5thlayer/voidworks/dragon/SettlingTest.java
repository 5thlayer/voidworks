// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * How far a Void Dragon's presence has settled onto its seat, which its shadow narrows by: wholly
 * sitting, not at all on the wing, evenly by how near its seat it is as it lands or takes off, and
 * never by more than a step a tick, so it never jumps.
 */
class SettlingTest {

    private static final double NO_SEAT = Double.POSITIVE_INFINITY;

    @Test
    void itIsWhollySettledSittingAndNotAtAllOnTheWing() {
        assertEquals(1.0F, Settling.toward(VoidDragon.Perch.SITTING, 0.0));
        assertEquals(0.0F, Settling.toward(VoidDragon.Perch.FLYING, NO_SEAT));
    }

    @Test
    void landingOrTakingOffItSettlesEvenlyByHowNearItsSeatItIs() {
        assertEquals(1.0F, Settling.toward(VoidDragon.Perch.LANDING, 0.0));
        assertEquals(0.5F, Settling.toward(VoidDragon.Perch.LANDING, Settling.NEAR_SEAT / 2.0), 1.0E-6F);
        assertEquals(0.0F, Settling.toward(VoidDragon.Perch.TAKING_OFF, Settling.NEAR_SEAT));
        assertEquals(0.0F, Settling.toward(VoidDragon.Perch.TAKING_OFF, DragonPresences.RISE));
    }

    @Test
    void landingOrTakingOffWithNoSeatItIsOnTheWing() {
        assertEquals(0.0F, Settling.toward(VoidDragon.Perch.LANDING, NO_SEAT));
    }

    @Test
    void itChangesByAStepATickAtMost() {
        // A landing begun right over its seat settles over half a second, not at once.
        assertEquals(Settling.MOST_A_TICK, Settling.settle(0.0F, VoidDragon.Perch.LANDING, 0.1));
        // A landing given up close to its seat rises back onto the wing just as gently.
        assertEquals(0.8F - Settling.MOST_A_TICK, Settling.settle(0.8F, VoidDragon.Perch.FLYING, NO_SEAT), 1.0E-6F);
        // Near where it is going, it goes the rest of the way.
        assertEquals(1.0F, Settling.settle(0.98F, VoidDragon.Perch.SITTING, 0.0));
    }

    @Test
    void aTakeOffIsOnTheWingBeforeItFliesOn() {
        // It climbs a take-off's blocks at 0.3 a tick, from its seat, and flies on once up.
        float settled = 1.0F;
        double climbed = 0.0;
        while (DragonPresences.RISE - climbed > 0.3) {
            climbed += 0.3;
            settled = Settling.settle(settled, VoidDragon.Perch.TAKING_OFF, climbed);
        }
        assertEquals(0.0F, settled);
    }
}
