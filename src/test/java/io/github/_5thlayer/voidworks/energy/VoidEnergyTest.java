// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
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
        assertEquals(OptionalLong.empty(), VoidEnergy.release(2, 3));
        assertEquals(OptionalLong.empty(), VoidEnergy.release(3, 5));
    }

    @Test
    void theHighestGradeReleasesItsFullDoubling() {
        long expected = BigInteger.valueOf(VoidEnergy.BASE).shiftLeft(VoidEnergy.MAX_GRADE - 1).longValueExact();
        assertEquals(OptionalLong.of(expected), VoidEnergy.release(VoidEnergy.MAX_GRADE, 0));
    }

    @Test
    void aGradeNoMoteHasIsRefusedOutright() {
        assertThrows(IllegalArgumentException.class, () -> VoidEnergy.release(VoidEnergy.MAX_GRADE + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> VoidEnergy.release(VoidEnergy.MIN_GRADE - 1, 0));
        // Far enough above the maximum that a long shift would wrap around to a small number.
        assertThrows(IllegalArgumentException.class, () -> VoidEnergy.release(66, 0));
    }

    @Test
    void aNegativePressureIsRefusedOutright() {
        assertThrows(IllegalArgumentException.class, () -> VoidEnergy.release(VoidEnergy.MIN_GRADE, -1));
        assertThrows(IllegalArgumentException.class, () -> VoidEnergy.plan(Map.of(3, 1), -1, 1));
    }

    @Test
    void theLowestGradeAboveThePressureIsChosen() {
        var stacks = Map.of(2, 5, 3, 5, 4, 5, 5, 5);
        assertEquals(OptionalInt.of(4), VoidEnergy.chooseGrade(stacks, 3));
    }

    @Test
    void aGradeAtOrBelowThePressureIsNeverChosen() {
        var stacks = Map.of(2, 9, 3, 9, 5, 1);
        assertEquals(OptionalInt.of(5), VoidEnergy.chooseGrade(stacks, 3));
    }

    @Test
    void nothingIsChosenWhenNoGradeIsAboveThePressure() {
        var stacks = Map.of(2, 3, 3, 3);
        assertEquals(OptionalInt.empty(), VoidEnergy.chooseGrade(stacks, 3));
        assertEquals(OptionalInt.empty(), VoidEnergy.chooseGrade(Map.of(), 0));
    }

    @Test
    void anEmptyStackIsNotChosen() {
        var stacks = Map.of(3, 0, 4, 2);
        assertEquals(OptionalInt.of(4), VoidEnergy.chooseGrade(stacks, 2));
    }

    @Test
    void aSpendTakesTheLowestWorthwhileGradeFirst() {
        // Pressure 2: grade 3 releases BASE, grade 4 releases 2 x BASE. Grade 2 is worth nothing.
        var stacks = Map.of(2, 50, 3, 10, 4, 10);
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
        assertEquals(Optional.empty(), VoidEnergy.plan(Map.of(2, 500, 3, 500), 3, 1));
    }

    @Test
    void aGradeNoMoteHasIsNeverTaken() {
        // Grade 1 would be worth something at pressure 0, and grade 17 more than any other, if motes had them.
        var stacks = Map.of(VoidEnergy.MIN_GRADE - 1, 500, VoidEnergy.MAX_GRADE + 1, 500, 64, 500, 66, 500);
        assertEquals(OptionalInt.empty(), VoidEnergy.chooseGrade(stacks, 0));
        assertEquals(Optional.empty(), VoidEnergy.plan(stacks, 0, 1));
    }

    @Test
    void theHighestGradeIsTaken() {
        var spend = VoidEnergy.plan(Map.of(VoidEnergy.MAX_GRADE, 1), 0, 1).orElseThrow();
        assertEquals(Map.of(VoidEnergy.MAX_GRADE, 1), spend.motes());
        assertEquals(VoidEnergy.release(VoidEnergy.MAX_GRADE, 0).orElseThrow(), spend.released());
    }

    @Test
    void theMostMotesOfTheHighestGradesAddUpExactly() {
        // As many motes as an int counts of the two highest grades, at the lowest pressure: every
        // grade-15 mote and a thousand grade-16 ones, with nothing wrapping around a long.
        int all = Integer.MAX_VALUE;
        long each15 = VoidEnergy.release(VoidEnergy.MAX_GRADE - 1, 0).orElseThrow();
        long each16 = VoidEnergy.release(VoidEnergy.MAX_GRADE, 0).orElseThrow();
        long required = all * each15 + 1000 * each16;
        var stacks = Map.of(VoidEnergy.MAX_GRADE - 1, all, VoidEnergy.MAX_GRADE, all);

        var spend = VoidEnergy.plan(stacks, 0, required).orElseThrow();

        assertEquals(Map.of(VoidEnergy.MAX_GRADE - 1, all, VoidEnergy.MAX_GRADE, 1000), spend.motes());
        assertEquals(required, spend.released());
    }

    @Test
    void theLargestSpendIsRefusedWithoutOverflow() {
        var stacks = Map.of(VoidEnergy.MAX_GRADE, Integer.MAX_VALUE);
        assertEquals(Optional.empty(), VoidEnergy.plan(stacks, 0, Long.MAX_VALUE));
    }

    @Test
    void aSpendOfNothingTakesNothing() {
        var spend = VoidEnergy.plan(Map.of(3, 1), 2, 0).orElseThrow();
        assertEquals(Map.of(), spend.motes());
        assertEquals(0, spend.released());
    }
}
