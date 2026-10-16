// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

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
 * left in its Zone, its Zone upgrades, and what stalls it, if anything. Read on the server, where its next position and
 * its network are, and sent to the client, which words it with {@link #lines}. A Dormant one's
 * payment is {@link io.github._5thlayer.voidworks.dormant.Dormancy#status}, shown beside it.
 *
 * @param fed what it holds to feed in, empty for nothing
 * @param levels how many Zone upgrades it has bought
 * @param nextPrice the void energy its next Zone upgrade costs, or empty when its block buys none
 * @param upgrade what the Zone upgrade it is soaking has paid, or empty while none is
 */
public record DisplacerReport(ItemStack fed, int harvestsLeft, int levels, OptionalLong nextPrice,
        Optional<DisplacerBlockEntity.Payment> upgrade, Optional<DisplacerBlockEntity.Stall> stall) {

    private static final StreamCodec<ByteBuf, DisplacerBlockEntity.Stall> STALL_CODEC =
            ByteBufCodecs.idMapper(ordinal -> DisplacerBlockEntity.Stall.values()[ordinal], DisplacerBlockEntity.Stall::ordinal);

    private static final StreamCodec<ByteBuf, OptionalLong> PRICE_CODEC = ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG).map(
            price -> price.map(OptionalLong::of).orElseGet(OptionalLong::empty),
            price -> price.isPresent() ? Optional.of(price.getAsLong()) : Optional.empty());

    private static final StreamCodec<ByteBuf, DisplacerBlockEntity.Payment> PAYMENT_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DisplacerBlockEntity.Payment::paid,
            ByteBufCodecs.VAR_LONG, DisplacerBlockEntity.Payment::price,
            DisplacerBlockEntity.Payment::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplacerReport> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, DisplacerReport::fed,
            ByteBufCodecs.VAR_INT, DisplacerReport::harvestsLeft,
            ByteBufCodecs.VAR_INT, DisplacerReport::levels,
            PRICE_CODEC, DisplacerReport::nextPrice,
            ByteBufCodecs.optional(PAYMENT_CODEC), DisplacerReport::upgrade,
            ByteBufCodecs.optional(STALL_CODEC), DisplacerReport::stall,
            DisplacerReport::new);

    /** @return what {@code displacer}, standing in {@code level}, shows */
    public static DisplacerReport of(DisplacerBlockEntity displacer, ServerLevel level) {
        return new DisplacerReport(displacer.fed(), displacer.harvestsLeft(), displacer.upgradeLevels(), displacer.nextUpgradePrice(),
                displacer.upgradePayment(), displacer.stall(level));
    }

    /**
     * @return its fed block, or that it has none, then the harvests left, then its Zone upgrades:
     *         what one soaking has paid, else those bought and the next one's price; then what stalls it in red
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        lines.add(fed.isEmpty()
                ? Component.translatable("voidworks.jade.displacer.nothing_fed").withStyle(ChatFormatting.GRAY)
                : Component.translatable("voidworks.jade.displacer.fed", fed.getCount(), fed.getHoverName()));
        lines.add(Component.translatable("voidworks.jade.displacer.harvests_left", harvestsLeft));
        if (upgrade.isPresent()) {
            lines.add(Component.translatable("voidworks.jade.displacer.upgrading", upgrade.get().paid(), upgrade.get().price()));
        } else if (nextPrice.isPresent()) {
            lines.add(Component.translatable("voidworks.jade.displacer.upgrades", levels, nextPrice.getAsLong()).withStyle(ChatFormatting.GRAY));
        }
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
            case UPGRADE_UNPAID -> "voidworks.jade.displacer.stall.upgrade_unpaid";
        };
    }
}
