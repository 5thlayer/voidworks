// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * What a position of a network is, which sets what it holds and how its network pressure rises as
 * it fills.
 *
 * <p>Network pressure is the motes' own and only steers flow: from 0, empty, to 1, full, it never
 * reaches a place's Void Pressure, so a full network never stands in for the End.
 */
public enum StoreKind implements StringRepresentable {

    /** Voidstone: its pressure rises as it fills. */
    VOIDSTONE("voidstone") {
        @Override
        double pressure(double fill) {
            return fill;
        }
    },

    /**
     * The Void Well: its pressure stays low until it is nearly full, so it soaks up a network's
     * surplus and gives it back as the network drains. Provisional, as every number is.
     */
    WELL("well") {
        @Override
        double pressure(double fill) {
            return Math.pow(fill, WELL_STEEPNESS);
        }
    };

    /** How steeply a Void Well's pressure rises near full: its fill to this power, a tenth at about three-quarters full. */
    static final int WELL_STEEPNESS = 8;

    public static final Codec<StoreKind> CODEC = StringRepresentable.fromEnum(StoreKind::values);

    private final String name;

    StoreKind(String name) {
        this.name = name;
    }

    /** @return its network pressure at {@code fill}, from 0, empty, to 1, full; rising with it */
    abstract double pressure(double fill);

    @Override
    public String getSerializedName() {
        return name;
    }
}
