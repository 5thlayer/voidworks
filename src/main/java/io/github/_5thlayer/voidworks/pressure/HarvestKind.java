// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * How a dimension is harvested by hand, which the Void Siphon follows: the dimension's entry in the
 * Void Pressure data map names it. {@link #NONE} is a dimension the Void Siphon refuses.
 */
public enum HarvestKind {
    END,
    OVERWORLD,
    NETHER,
    NONE;

    /** The name a data map entry spells it by. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the kind spelled {@code name}, or empty when no kind is */
    public static Optional<HarvestKind> byName(String name) {
        return Arrays.stream(values()).filter(kind -> kind.serializedName().equals(name)).findFirst();
    }
}
