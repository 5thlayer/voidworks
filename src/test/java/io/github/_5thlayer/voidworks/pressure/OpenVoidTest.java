// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** The open void: a column with no block beneath a position down to the bottom of the world. */
class OpenVoidTest {

    private static final int MIN_Y = 0;

    @Test
    void anEmptyColumnBelowIsOpen() {
        assertTrue(OpenVoid.isOpen(MIN_Y, 60, y -> false));
    }

    @Test
    void anyBlockBelowClosesIt() {
        assertFalse(OpenVoid.isOpen(MIN_Y, 60, y -> y == 30));
    }

    @Test
    void aBlockJustBelowClosesIt() {
        assertFalse(OpenVoid.isOpen(MIN_Y, 60, y -> y == 59));
    }

    @Test
    void aBlockAtTheBottomOfTheWorldClosesIt() {
        assertFalse(OpenVoid.isOpen(MIN_Y, 60, y -> y == MIN_Y));
    }

    @Test
    void thePositionsOwnBlockDoesNotCount() {
        assertTrue(OpenVoid.isOpen(MIN_Y, 60, y -> y == 60));
    }

    @Test
    void blocksAboveThePositionDoNotCount() {
        assertTrue(OpenVoid.isOpen(MIN_Y, 60, y -> y > 60));
    }

    @Test
    void aColumnIsOnlyLookedAtDownToTheBottomOfTheWorld() {
        assertTrue(OpenVoid.isOpen(-64, 0, y -> Set.of(-65, -100).contains(y)));
    }

    @Test
    void aPositionAtTheBottomOfTheWorldHasNothingBeneathItAndIsOpen() {
        assertTrue(OpenVoid.isOpen(MIN_Y, MIN_Y, y -> true));
    }
}
