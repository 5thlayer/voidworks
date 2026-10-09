// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.butterfly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Each grade tints its butterflies its own colour, from the palette in issue #21. */
class GradeColorTest {

    @Test
    void theLowestGradeIsRed() {
        assertEquals(0xF75036, GradeColor.of(2));
    }

    @Test
    void aMiddleGradeIsGreen() {
        assertEquals(0x36F78A, GradeColor.of(8));
    }

    @Test
    void theHighestGradeIsPink() {
        assertEquals(0xF73697, GradeColor.of(16));
    }

    @Test
    void aGradeNoMoteHasIsWhite() {
        assertEquals(0xFFFFFF, GradeColor.of(1));
        assertEquals(0xFFFFFF, GradeColor.of(17));
    }
}
