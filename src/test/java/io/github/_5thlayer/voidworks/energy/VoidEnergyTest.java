// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.OptionalInt;
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

    @Test
    void theLowestGradeAboveThePressureIsChosen() {
        var stacks = Map.of(1, 5, 2, 5, 3, 5, 4, 5);
        assertEquals(OptionalInt.of(3), VoidEnergy.chooseGrade(stacks, 2));
    }

    @Test
    void aGradeAtOrBelowThePressureIsNeverChosen() {
        var stacks = Map.of(1, 9, 2, 9, 4, 1);
        assertEquals(OptionalInt.of(4), VoidEnergy.chooseGrade(stacks, 2));
    }

    @Test
    void nothingIsChosenWhenNoGradeIsAboveThePressure() {
        var stacks = Map.of(1, 3, 2, 3);
        assertEquals(OptionalInt.empty(), VoidEnergy.chooseGrade(stacks, 2));
        assertEquals(OptionalInt.empty(), VoidEnergy.chooseGrade(Map.of(), 0));
    }

    @Test
    void anEmptyStackIsNotChosen() {
        var stacks = Map.of(3, 0, 4, 2);
        assertEquals(OptionalInt.of(4), VoidEnergy.chooseGrade(stacks, 2));
    }
}
