// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * What a Void Displacer shows a player looking at it, any dimension's: its fed block, the harvests
 * left in its Zone, and what stalls it, if anything. Read on the server, where its next position and
 * its network are, and sent to the client, which words it with {@link #lines}. A Dormant one's
 * payment is {@link io.github._5thlayer.voidworks.dormant.Dormancy#status}, shown beside it.
 *
 * @param fed what it holds to feed in, empty for nothing
 */
public record DisplacerReport(ItemStack fed, int harvestsLeft, Optional<DisplacerBlockEntity.Stall> stall) {

    private static final StreamCodec<ByteBuf, DisplacerBlockEntity.Stall> STALL_CODEC =
            ByteBufCodecs.idMapper(ordinal -> DisplacerBlockEntity.Stall.values()[ordinal], DisplacerBlockEntity.Stall::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplacerReport> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, DisplacerReport::fed,
            ByteBufCodecs.VAR_INT, DisplacerReport::harvestsLeft,
            ByteBufCodecs.optional(STALL_CODEC), DisplacerReport::stall,
            DisplacerReport::new);

    /** @return what {@code displacer}, standing in {@code level}, shows */
    public static DisplacerReport of(DisplacerBlockEntity displacer, ServerLevel level) {
        return new DisplacerReport(displacer.fed(), displacer.harvestsLeft(), displacer.stall(level));
    }

    /** @return its fed block, or that it has none, then the harvests left, then what stalls it in red */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        lines.add(fed.isEmpty()
                ? Component.translatable("voidworks.jade.displacer.nothing_fed").withStyle(ChatFormatting.GRAY)
                : Component.translatable("voidworks.jade.displacer.fed", fed.getCount(), fed.getHoverName()));
        lines.add(Component.translatable("voidworks.jade.displacer.harvests_left", harvestsLeft));
        stall.ifPresent(reason -> lines.add(Component.translatable(stallKey(reason)).withStyle(ChatFormatting.RED)));
        return lines;
    }

    private static String stallKey(DisplacerBlockEntity.Stall stall) {
        return switch (stall) {
            case PRESSURE_TOO_HIGH -> "voidworks.jade.displacer.stall.pressure_too_high";
            case NETWORK_FULL -> "voidworks.jade.displacer.stall.network_full";
            case OUTPUT_FULL -> "voidworks.jade.displacer.stall.output_full";
            case TANK_FULL -> "voidworks.jade.displacer.stall.tank_full";
            case OUTPUT_UNEMPTIED -> "voidworks.jade.displacer.stall.output_unemptied";
        };
    }
}
