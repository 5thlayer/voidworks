// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Well hatch shows a player looking at it, beside what it holds, which Jade shows from its item
 * face: what a requester asks for. Whether it is a provider or a requester its name says, the item it is
 * picked as, so a provider shows nothing more. Read on the server, where the hatch is, and sent to the
 * client, which words it with {@link #lines} and draws a request after them as its item with the stacks
 * asked for on it.
 *
 * @param mode    whether it is a provider or a requester
 * @param request what a requester asks for; empty for a provider, and a requester asking for nothing
 */
public record HatchReport(WellHatches.Mode mode, Optional<WellHatches.Request> request) {

    private static final StreamCodec<ByteBuf, WellHatches.Mode> MODE_CODEC = ByteBufCodecs.idMapper(ordinal -> WellHatches.Mode.values()[ordinal],
            WellHatches.Mode::ordinal);

    private static final StreamCodec<RegistryFriendlyByteBuf, WellHatches.Request> REQUEST_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), WellHatches.Request::item,
            ByteBufCodecs.VAR_INT, WellHatches.Request::stacks,
            WellHatches.Request::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, HatchReport> STREAM_CODEC = StreamCodec.composite(
            MODE_CODEC, HatchReport::mode,
            ByteBufCodecs.optional(REQUEST_CODEC), HatchReport::request,
            HatchReport::new);

    /** @return what {@code hatch} shows */
    public static HatchReport of(WellHatches.Hatch hatch) {
        return new HatchReport(hatch.mode(), hatch.request());
    }

    /** @return nothing for a provider; for a requester, that it is requesting, which its request follows, or that it requests nothing */
    public List<Component> lines() {
        if (mode == WellHatches.Mode.PROVIDER) {
            return List.of();
        }
        return List.of(Component.translatable(request.isPresent() ? "voidworks.jade.hatch.requesting" : "voidworks.jade.hatch.requesting_nothing"));
    }
}
