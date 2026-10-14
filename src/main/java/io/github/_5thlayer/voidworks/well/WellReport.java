// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.network.MoteNetwork;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;

/**
 * What a Void Well's core shows a player looking at it, beside its network's motes and its Dormant
 * payment, which Jade shows for it as for Voidstone and any Dormant machine: its name, that the well
 * around it is not whole when so, its fill target in motes, and its owner. Read on the server, where
 * the core's state is, and sent to the client, which words it with {@link #lines}.
 *
 * @param label    its name, or unnamed, its dimension and position
 * @param owner    the name of the player who owns it, empty for a core from before owners
 * @param whole    whether the well around it is whole
 * @param target   how full it asks to be kept
 * @param capacity how many motes its network holds at most, which its target is a share of; its
 *        core's own while it is in none
 */
public record WellReport(Component label, Optional<String> owner, boolean whole, FillTarget target, long capacity) {

    private static final StreamCodec<ByteBuf, FillTarget> TARGET_CODEC = ByteBufCodecs.idMapper(ordinal -> FillTarget.values()[ordinal],
            FillTarget::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, WellReport> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, WellReport::label,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), WellReport::owner,
            ByteBufCodecs.BOOL, WellReport::whole,
            TARGET_CODEC, WellReport::target,
            ByteBufCodecs.VAR_LONG, WellReport::capacity,
            WellReport::new);

    /** @return what {@code well}, standing in {@code level}, shows */
    public static WellReport of(VoidWellBlockEntity well, ServerLevel level) {
        long capacity = VoidstoneNetworks.of(level).at(well.getBlockPos()).map(MoteNetwork::capacity).orElse((long) VoidworksConfig.wellCapacity());
        return new WellReport(well.label(), well.owner().map(NameAndId::name), well.whole(level), well.target(), capacity);
    }

    /**
     * @return its name; in red that the well is not whole, if so; its target with the motes it asks
     *         for, or that it is a reservoir; and whose it is, or that no one owns it
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        lines.add(label);
        if (!whole) {
            lines.add(Component.translatable("voidworks.jade.well.not_whole").withStyle(ChatFormatting.RED));
        }
        var motes = target.motes(capacity);
        lines.add(motes.isPresent()
                ? Component.translatable("voidworks.jade.well.target", target.label(), motes.getAsLong())
                : Component.translatable("voidworks.jade.well.reservoir"));
        lines.add(owner.<Component>map(name -> Component.translatable("voidworks.jade.well.owner", name))
                .orElseGet(() -> Component.translatable("voidworks.jade.well.no_owner")));
        return lines;
    }
}
