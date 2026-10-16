// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import io.github._5thlayer.voidworks.Voidworks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * What an owner calling a Void Dragon is shown of its dive to them, sent to their client each step
 * while it dives there: how far through the dive it is, in the colour of the dimension it comes to
 * ({@link DiveColour}), just over the action bar's word that one is coming.
 *
 * @param from the game time the dive set off
 * @param until the game time it arrives, if it keeps its speed now
 * @param colour the bar's colour, opaque
 */
public record CallPayload(long from, long until, int colour) implements CustomPacketPayload {

    public static final Type<CallPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "call"));

    public static final StreamCodec<ByteBuf, CallPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CallPayload::from,
            ByteBufCodecs.VAR_LONG, CallPayload::until,
            ByteBufCodecs.INT, CallPayload::colour,
            CallPayload::new);

    /** @return the dive as a bar shows it, with no label of its own */
    public DiveShown shown() {
        return new DiveShown(Component.empty(), from, until, colour);
    }

    @Override
    public Type<CallPayload> type() {
        return TYPE;
    }
}
