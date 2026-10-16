// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * What a Void Well's shaft shows of its network: a level in sixteenths of its capacity and the share
 * of each grade it holds, so the client is told of a change only when the shaft would look different.
 */
class ShaftFillTest {

    private static TreeMap<Integer, Long> grades(Map<Integer, Long> counts) {
        return new TreeMap<>(counts);
    }

    @Test
    void anEmptyNetworkShowsAnEmptyShaft() {
        assertEquals(ShaftFill.EMPTY, ShaftFill.of(65_536, grades(Map.of())));
    }

    @Test
    void aFullNetworkFillsTheShaftToTheTop() {
        assertEquals(ShaftFill.STEPS, ShaftFill.of(65_536, grades(Map.of(3, 65_536L))).step());
    }

    @Test
    void theLevelIsTheNetworksShareOfItsCapacityInSixteenthsRoundedUp() {
        assertEquals(8, ShaftFill.of(65_536, grades(Map.of(3, 32_768L))).step());
        assertEquals(9, ShaftFill.of(65_536, grades(Map.of(3, 32_769L))).step());
        assertEquals(15, ShaftFill.of(65_536, grades(Map.of(3, 65_535L))).step());
    }

    @Test
    void aSingleMoteStillShowsTheLowestLevel() {
        assertEquals(1, ShaftFill.of(65_536, grades(Map.of(3, 1L))).step());
    }

    @Test
    void theLevelCountsEveryGradeTogether() {
        assertEquals(8, ShaftFill.of(65_536, grades(Map.of(2, 16_384L, 4, 16_384L))).step());
    }

    @Test
    void eachGradeHeldHasItsShareInSixteenths() {
        var fill = ShaftFill.of(65_536, grades(Map.of(2, 12_000L, 4, 36_000L)));
        assertEquals(Map.of(2, 4, 4, 12), fill.shares());
    }

    @Test
    void aGradeHeldButFarOutnumberedStillHasAShare() {
        var fill = ShaftFill.of(65_536, grades(Map.of(2, 1L, 4, 60_000L)));
        assertEquals(1, fill.shares().get(2));
        assertEquals(16, fill.shares().get(4));
    }

    @Test
    void motesComingAndGoingWithinAStepAndTheMixLookTheSame() {
        assertEquals(ShaftFill.of(65_536, grades(Map.of(3, 30_000L))), ShaftFill.of(65_536, grades(Map.of(3, 30_100L))));
    }

    @Test
    void moreMotesShowMoreButterfliesUpToADozen() {
        assertEquals(0, ShaftFill.EMPTY.butterflies());
        assertEquals(1, ShaftFill.of(65_536, grades(Map.of(3, 1L))).butterflies());
        assertEquals(6, ShaftFill.of(65_536, grades(Map.of(3, 32_768L))).butterflies());
        assertEquals(ShaftFill.MOST_BUTTERFLIES, ShaftFill.of(65_536, grades(Map.of(3, 65_536L))).butterflies());
    }

    @Test
    void aButterflysGradeIsPickedInProportionToTheShares() {
        var fill = ShaftFill.of(65_536, grades(Map.of(2, 12_000L, 4, 36_000L)));
        var picked = new TreeMap<Integer, Integer>();
        for (int roll = 0; roll < 16; roll++) {
            picked.merge(fill.grade(roll), 1, Integer::sum);
        }
        assertEquals(Map.of(2, 4, 4, 12), picked);
    }

    @Test
    void theLevelIsAShareOfTheShaftsHeight() {
        assertEquals(0, ShaftFill.EMPTY.level());
        assertEquals(0.5, ShaftFill.of(65_536, grades(Map.of(3, 32_768L))).level());
        assertTrue(ShaftFill.of(65_536, grades(Map.of(3, 65_536L))).level() == 1);
    }
}
