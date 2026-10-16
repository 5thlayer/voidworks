// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Each Zone upgrade a Displacer buys costs its growth times the last, with no last level, and stops at the largest price. */
class ZoneUpgradeTest {

    @Test
    void theFirstLevelCostsTheFirstPriceAndEachAfterItGrows() {
        var doubling = new ZoneUpgrade(16, 1_600, 2);
        assertEquals(1_600, doubling.price(0));
        assertEquals(3_200, doubling.price(1));
        assertEquals(8 * 1_600, doubling.price(3));
        assertEquals(1_000 * 27, new ZoneUpgrade(16, 1_000, 3).price(3));
    }

    @Test
    void aGrowthOfOneKeepsEveryLevelAtTheFirstPrice() {
        assertEquals(500, new ZoneUpgrade(16, 500, 1).price(1_000));
    }

    @Test
    void thePriceStopsAtTheLargestPrice() {
        assertEquals(1L << 62, new ZoneUpgrade(16, 1, 2).price(62));
        assertEquals(Long.MAX_VALUE, new ZoneUpgrade(16, 1, 2).price(63));
        assertEquals(Long.MAX_VALUE, new ZoneUpgrade(16, 1_600, 2).price(Integer.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, new ZoneUpgrade(16, Long.MAX_VALUE, 3).price(1));
    }

    @Test
    void theZoneGrowsByItsStepPerLevelAndStopsAtTheLargestZone() {
        var upgrade = new ZoneUpgrade(16, 1_600, 2);
        assertEquals(80, upgrade.raise(64));
        assertEquals(Integer.MAX_VALUE, upgrade.raise(Integer.MAX_VALUE - 3));
    }
}
