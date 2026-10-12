// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import java.util.Locale;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Which way a Void Siphon moves motes while its holder stands on Voidstone, its toggle, which
 * sneak-use flips: it charges from the network, or empties into it. It is the Siphon's
 * {@code voidworks:siphon_mode} data component; a Siphon without one charges.
 */
public enum SiphonMode implements StringRepresentable {
    CHARGE,
    EMPTY;

    public static final Codec<SiphonMode> CODEC = StringRepresentable.fromEnum(SiphonMode::values);

    public static final StreamCodec<ByteBuf, SiphonMode> STREAM_CODEC = ByteBufCodecs.BOOL.map(
            emptying -> emptying ? EMPTY : CHARGE, mode -> mode == EMPTY);

    /** @return the other way */
    public SiphonMode flipped() {
        return this == CHARGE ? EMPTY : CHARGE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
