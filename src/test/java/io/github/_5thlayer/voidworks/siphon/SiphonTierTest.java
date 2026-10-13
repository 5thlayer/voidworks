// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The Void Siphon's tiers: what each reads from a per-tier config list, and their order. */
class SiphonTierTest {

    @Test
    void theTiersClimbFromTheSiphonThroughResonantToDraconic() {
        assertEquals(List.of(SiphonTier.BASE, SiphonTier.RESONANT, SiphonTier.DRACONIC), List.of(SiphonTier.values()));
    }

    @Test
    void aTierReadsItsOwnEntryOfAPerTierList() {
        var capacities = List.of(256, 1024, 4096);
        assertEquals(256, SiphonTier.BASE.of(capacities));
        assertEquals(1024, SiphonTier.RESONANT.of(capacities));
        assertEquals(4096, SiphonTier.DRACONIC.of(capacities));
    }

    @Test
    void aTierPastAShortListReadsItsLastEntry() {
        assertEquals(512, SiphonTier.DRACONIC.of(List.of(256, 512)));
    }

    @Test
    void theShippedNumbersRiseWithTheTier() {
        for (var numbers : List.of(SiphonTier.DEFAULT_CAPACITIES, SiphonTier.DEFAULT_RATES)) {
            for (int i = 1; i < numbers.size(); i++) {
                assertTrue(numbers.get(i) > numbers.get(i - 1), numbers + " does not rise");
            }
            assertEquals(SiphonTier.values().length, numbers.size());
        }
    }
}
