// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * A Void Dragon's Line, fixed by its egg and carried from the soaking egg through the Dragonling to
 * the grown dragon's record (ADR 0011): what it carries, never both. A dragon or Dragonling saved
 * before Lines is of the Mote line.
 */
public enum Line implements StringRepresentable {
    /** Carries motes; founded by the vanilla egg, and hatched from a Drained Dragon Egg. */
    MOTE("mote"),
    /** Carries items; hatched from a Matter Dragon Egg. */
    MATTER("matter");

    public static final Codec<Line> CODEC = StringRepresentable.fromEnum(Line::values);

    private final String name;

    Line(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
