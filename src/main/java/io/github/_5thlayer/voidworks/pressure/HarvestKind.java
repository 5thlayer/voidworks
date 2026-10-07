// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * How a dimension is harvested by hand, which the Void Siphon follows: the dimension's entry in the
 * Void Pressure data map names it, in lower case. {@link #NONE} is a dimension the Void Siphon
 * refuses.
 */
public enum HarvestKind implements StringRepresentable {
    END,
    OVERWORLD,
    NETHER,
    NONE;

    /** The name a data map entry spells it by. */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
