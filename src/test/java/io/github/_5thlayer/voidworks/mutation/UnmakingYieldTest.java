// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;

/** The Unmaking Chamber's yield, rising with what it has spent, and the roll of each ingredient item at it. */
class UnmakingYieldTest {

    private static final UnmakingYield YIELD = new UnmakingYield(0.25, 0.75, 25_600);

    @Test
    void itStartsAtAQuarter() {
        assertEquals(0.25, YIELD.at(0), 1e-12);
    }

    @Test
    void eachHalvingSpentClosesHalfTheGapToTheCap() {
        assertEquals(0.5, YIELD.at(25_600), 1e-12);
        assertEquals(0.625, YIELD.at(51_200), 1e-12);
        assertEquals(0.6875, YIELD.at(76_800), 1e-12);
    }

    @Test
    void itRisesBetweenHalvingsAndNeverReachesTheCap() {
        assertTrue(YIELD.at(100) > 0.25 && YIELD.at(100) < YIELD.at(200));
        assertTrue(YIELD.at(Long.MAX_VALUE / 4) <= 0.75);
        assertTrue(YIELD.at(25_600L * 40) < 0.75);
    }

    @Test
    void eachIngredientItemComesBackAloneWhenItsRollFallsBelowTheYield() {
        var breakdown = new Breakdown<>(1, Map.of("coal", 2, "stick", 1));
        // coal, coal, stick: the first coal and the stick come back.
        assertEquals(Map.of("coal", 1, "stick", 1), UnmakingYield.roll(breakdown, 0.5, rolls(0.1, 0.9, 0.4)));
    }

    @Test
    void nothingComesBackThatDidNotGoIn() {
        var breakdown = new Breakdown<>(1, Map.of("iron_ingot", 9));
        assertEquals(Map.of("iron_ingot", 9), UnmakingYield.roll(breakdown, 1.0, rolls(0.99, 0.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)));
        assertEquals(Map.of(), UnmakingYield.roll(breakdown, 0.0, rolls(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)));
    }

    private static DoubleSupplier rolls(Double... values) {
        Iterator<Double> next = List.of(values).iterator();
        return () -> next.next();
    }
}
