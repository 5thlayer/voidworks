// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.voidworks.dragon.DragonRouting;
import io.github._5thlayer.voidworks.dragon.Payable;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;

/**
 * What a Well hatch shows a player looking at it, beside what it holds, which Jade shows from its item
 * face: what a requester asks for, and how much of what a provider holds its network can pay the Toll to
 * lift. Whether it is a provider or a requester its name says, the item it is picked as. Read on the
 * server, where the hatch is, and sent to the client, which words it with {@link #lines} and draws a
 * request after them as its item with the stacks asked for on it.
 *
 * @param mode    whether it is a provider or a requester
 * @param request what a requester asks for; empty for a provider, and a requester asking for nothing
 * @param payable for a provider on a Well of its owner's, how many stacks it holds and how many of them its
 *                network can pay the Toll to lift; empty otherwise
 */
public record HatchReport(WellHatches.Mode mode, Optional<WellHatches.Request> request, Optional<Payable> payable) {

    private static final StreamCodec<ByteBuf, WellHatches.Mode> MODE_CODEC = ByteBufCodecs.idMapper(ordinal -> WellHatches.Mode.values()[ordinal],
            WellHatches.Mode::ordinal);

    private static final StreamCodec<RegistryFriendlyByteBuf, WellHatches.Request> REQUEST_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), WellHatches.Request::item,
            ByteBufCodecs.VAR_INT, WellHatches.Request::stacks,
            WellHatches.Request::new);

    private static final StreamCodec<ByteBuf, Payable> PAYABLE_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Payable::holds,
            ByteBufCodecs.VAR_LONG, Payable::payable,
            Payable::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, HatchReport> STREAM_CODEC = StreamCodec.composite(
            MODE_CODEC, HatchReport::mode,
            ByteBufCodecs.optional(REQUEST_CODEC), HatchReport::request,
            ByteBufCodecs.optional(PAYABLE_CODEC), HatchReport::payable,
            HatchReport::new);

    /** @return what {@code hatch} on {@code server} shows */
    public static HatchReport of(WellHatches.Hatch hatch, MinecraftServer server) {
        return new HatchReport(hatch.mode(), hatch.request(), DragonRouting.payable(server, hatch));
    }

    /**
     * @return for a provider holding something, how many stacks it holds and for how many its network can
     *         pay the Toll, or that it stalls, able to pay for none; for a requester, that it is requesting,
     *         which its request follows, or that it requests nothing
     */
    public List<Component> lines() {
        if (mode == WellHatches.Mode.PROVIDER) {
            return payable.filter(lifting -> lifting.holds() > 0)
                    .map(lifting -> lifting.payable() == 0
                            ? List.<Component>of(Component.translatable("voidworks.jade.hatch.stall.toll"))
                            : List.<Component>of(Component.translatable("voidworks.jade.hatch.payable", lifting.holds(), lifting.payable())))
                    .orElse(List.of());
        }
        return List.of(Component.translatable(request.isPresent() ? "voidworks.jade.hatch.requesting" : "voidworks.jade.hatch.requesting_nothing"));
    }
}
