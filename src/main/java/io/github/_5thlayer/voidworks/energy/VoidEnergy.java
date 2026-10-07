// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.OptionalLong;

/**
 * The spend rule every spend in Voidworks follows, as plain logic with no Minecraft types. A mote's
 * grade and the Void Pressure where it is spent are small integers on one scale; the further the
 * grade exceeds the pressure, the more void energy the mote releases, doubling with each step.
 */
public final class VoidEnergy {

    /**
     * The void energy one mote releases one step above the Void Pressure. Provisional: the numbers
     * are still open (docs/spec/void-energy.md), and this is the one place that holds it.
     */
    public static final long BASE = 100;

    private VoidEnergy() {
    }

    /**
     * The void energy one mote of {@code grade} releases when spent at {@code pressure}.
     *
     * @return {@code BASE × 2^(grade − pressure − 1)}, or empty when the grade is not above the
     *         pressure: the spend is refused
     */
    public static OptionalLong release(int grade, int pressure) {
        if (grade <= pressure) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(BASE << (grade - pressure - 1));
    }
}
