// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.butterfly;

import io.github._5thlayer.voidworks.energy.VoidEnergy;

/** The colour each grade tints its butterflies, going round the hue wheel from red at the lowest grade. */
public final class GradeColor {

    /** The colour of a grade no mote has. */
    public static final int FALLBACK = 0xFFFFFF;

    /** RGB per grade, from {@link VoidEnergy#MIN_GRADE} up. */
    private static final int[] PALETTE = {
        0xF75036, 0xC77C2C, 0xF7E436, 0x9BC72C, 0x77F736, 0x2CC734, 0x36F78A, 0x2CC7AA,
        0x36D1F7, 0x2C6CC7, 0x363DF7, 0x622CC7, 0xC436F7, 0xC72CB5, 0xF73697,
    };

    static {
        if (PALETTE.length != VoidEnergy.MAX_GRADE - VoidEnergy.MIN_GRADE + 1) {
            throw new IllegalStateException("the palette must give every grade a colour");
        }
    }

    private GradeColor() {
    }

    /** @return the RGB that tints butterflies of {@code grade}, or {@link #FALLBACK} for a grade no mote has */
    public static int of(int grade) {
        return VoidEnergy.isGrade(grade) ? PALETTE[grade - VoidEnergy.MIN_GRADE] : FALLBACK;
    }
}
