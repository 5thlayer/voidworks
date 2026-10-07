// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/** The spend rule: what a mote releases by how far its grade exceeds the Void Pressure. */
class VoidEnergyTest {

    @Test
    void oneStepAbovePressureReleasesBase() {
        assertEquals(OptionalLong.of(VoidEnergy.BASE), VoidEnergy.release(3, 2));
    }

    @Test
    void eachFurtherStepDoublesTheRelease() {
        assertEquals(OptionalLong.of(2 * VoidEnergy.BASE), VoidEnergy.release(4, 2));
        assertEquals(OptionalLong.of(4 * VoidEnergy.BASE), VoidEnergy.release(5, 2));
    }

    @Test
    void aGradeEqualToThePressureIsRefused() {
        assertEquals(OptionalLong.empty(), VoidEnergy.release(2, 2));
    }

    @Test
    void aGradeBelowThePressureIsRefused() {
        assertEquals(OptionalLong.empty(), VoidEnergy.release(1, 2));
        assertEquals(OptionalLong.empty(), VoidEnergy.release(0, 3));
    }
}
