// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.OptionalLong;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * How full a Void Well asks to be kept, as a share of its capacity: one total over every grade. Void
 * Dragons deliver to a Well below its target and take from one above it; a Well with none is a
 * reservoir, which only gives. Its owner cycles it in the order declared here, back to none after full.
 */
public enum FillTarget implements StringRepresentable {

    NONE("none", 0),
    QUARTER("quarter", 1),
    HALF("half", 2),
    THREE_QUARTERS("three_quarters", 3),
    FULL("full", 4);

    public static final Codec<FillTarget> CODEC = StringRepresentable.fromEnum(FillTarget::values);

    private final String name;
    /** Its share, in quarters of the capacity. */
    private final int quarters;

    FillTarget(String name, int quarters) {
        this.name = name;
        this.quarters = quarters;
    }

    /** @return the target after it as its owner cycles it, none after full */
    public FillTarget next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** @return how many motes it asks a Well holding {@code capacity} to keep, rounded down; empty for none */
    public OptionalLong motes(long capacity) {
        return this == NONE ? OptionalLong.empty() : OptionalLong.of(capacity * quarters / 4);
    }

    /** @return its name, for a player */
    public Component label() {
        return Component.translatable("voidworks.well.target." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
