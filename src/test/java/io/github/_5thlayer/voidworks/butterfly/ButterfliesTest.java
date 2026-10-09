// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.butterfly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** A count of motes splits into butterflies of 64, 8 and 1, as few as add up to it exactly. */
class ButterfliesTest {

    @Test
    void noMotesIsNoButterflies() {
        assertEquals(List.of(), Butterflies.sizes(0));
    }

    @Test
    void oneMoteIsOneSmallButterfly() {
        assertEquals(List.of(1), Butterflies.sizes(1));
    }

    @Test
    void sevenMotesAreSevenSmallButterflies() {
        assertEquals(List.of(1, 1, 1, 1, 1, 1, 1), Butterflies.sizes(7));
    }

    @Test
    void eightMotesAreOneMiddleButterfly() {
        assertEquals(List.of(8), Butterflies.sizes(8));
    }

    @Test
    void largerSizesComeFirst() {
        // 64 + 64 + 8 + 8 + 8 + 1 + 1 = 154
        assertEquals(List.of(64, 64, 8, 8, 8, 1, 1), Butterflies.sizes(154));
    }

    @Test
    void sixtyThreeMotesUseTheFewestButterflies() {
        assertEquals(List.of(8, 8, 8, 8, 8, 8, 8, 1, 1, 1, 1, 1, 1, 1), Butterflies.sizes(63));
    }

    @Test
    void sizesAlwaysSumToTheCount() {
        for (int count = 0; count <= 1000; count++) {
            assertEquals(count, Butterflies.sizes(count).stream().mapToInt(Integer::intValue).sum(), "count " + count);
        }
    }

    @Test
    void aNegativeCountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Butterflies.sizes(-1));
    }
}
