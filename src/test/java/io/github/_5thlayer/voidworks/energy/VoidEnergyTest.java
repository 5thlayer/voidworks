// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
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

    @Test
    void aSpendTakesTheLowestWorthwhileGradeFirst() {
        // Pressure 2: grade 3 releases BASE, grade 4 releases 2 x BASE. Grade 1 is worth nothing.
        var stacks = Map.of(1, 50, 3, 10, 4, 10);
        var spend = VoidEnergy.plan(stacks, 2, 3 * VoidEnergy.BASE).orElseThrow();
        assertEquals(Map.of(3, 3), spend.motes());
        assertEquals(3 * VoidEnergy.BASE, spend.released());
    }

    @Test
    void aSpendMovesUpAGradeOnlyWhenTheLowerRunsOut() {
        var stacks = Map.of(3, 4, 4, 10);
        // All four grade 3 motes release 4 x BASE; the other 6 x BASE is three grade 4 motes at 2 x BASE.
        var spend = VoidEnergy.plan(stacks, 2, 10 * VoidEnergy.BASE).orElseThrow();
        assertEquals(Map.of(3, 4, 4, 3), spend.motes());
        assertEquals(10 * VoidEnergy.BASE, spend.released());
    }

    @Test
    void aSpendReleasesAtLeastWhatWasAskedWhenNoWholeMotesMatch() {
        var stacks = Map.of(5, 3);
        // One grade 5 mote at pressure 2 releases 4 x BASE; 1 x BASE + 1 asks for a second.
        var spend = VoidEnergy.plan(stacks, 2, VoidEnergy.BASE + 1).orElseThrow();
        assertEquals(Map.of(5, 1), spend.motes());
        assertEquals(4 * VoidEnergy.BASE, spend.released());
    }

    @Test
    void aSpendThatCannotBeAffordedIsRefused() {
        // 2 x BASE from grade 3 and 2 x BASE from grade 4: 4 x BASE in all, grade 2 is worth nothing.
        var stacks = Map.of(2, 99, 3, 2, 4, 1);
        assertEquals(Optional.empty(), VoidEnergy.plan(stacks, 2, 4 * VoidEnergy.BASE + 1));
    }

    @Test
    void motesNotAboveThePressureAreRefusedWhateverTheirNumber() {
        assertEquals(Optional.empty(), VoidEnergy.plan(Map.of(1, 500, 2, 500), 2, 1));
    }

    @Test
    void aSpendOfNothingTakesNothing() {
        var spend = VoidEnergy.plan(Map.of(3, 1), 2, 0).orElseThrow();
        assertEquals(Map.of(), spend.motes());
        assertEquals(0, spend.released());
    }
}
