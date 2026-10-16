// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** An egg's price grows by its growth for each dragon its owner has, and stops at the largest price. */
class EggPriceTest {

    @Test
    void thePriceGrowsByItsGrowthPerDragon() {
        var doubling = new EggPrice(25_600, 2);
        assertEquals(25_600, doubling.of(0));
        assertEquals(8 * 25_600, doubling.of(3));
        assertEquals(1_000 * 27, new EggPrice(1_000, 3).of(3));
    }

    @Test
    void aGrowthOfOneKeepsEveryEggAtTheFirstPrice() {
        assertEquals(500, new EggPrice(500, 1).of(40));
    }

    @Test
    void thePriceStopsAtTheLargestPrice() {
        assertEquals(1L << 62, new EggPrice(1, 2).of(62));
        assertEquals(Long.MAX_VALUE, new EggPrice(1, 2).of(63));
        assertEquals(Long.MAX_VALUE, new EggPrice(25_600, 2).of(Integer.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, new EggPrice(Long.MAX_VALUE, 3).of(1));
    }
}
