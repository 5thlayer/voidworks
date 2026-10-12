// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import io.github._5thlayer.voidworks.energy.TouchingMotes;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * What a Mutation Chamber shows a player looking at it: what its input holds, the upgrade it climbs
 * to with its count, cost and chance, and, once awake, what the best mote store touching it is worth
 * there and whether it is upgrading or what stalls it. Read on the server, where Families, mote
 * stores and Void Pressure are, and sent to the client, which words it with {@link #lines}. A Dormant
 * one's payment is {@link io.github._5thlayer.voidworks.dormant.Dormancy#status}, shown beside it with
 * its own touching stores, so a Dormant one shows only what stalls its items.
 *
 * @param input what waits to upgrade, empty for nothing
 * @param next the upgrade it climbs to, empty for an empty input or an item with none
 * @param bestWorth the most one touching store is worth there, in void energy; empty while Dormant or with none touching it
 */
public record ChamberReport(ItemStack input, Optional<Next> next, boolean awake, Optional<Long> bestWorth,
        Optional<MutationChamberBlockEntity.Stall> stall) {

    /** An upgrade as a player is shown it: the result, and the count, cost and chance it takes. */
    public record Next(ItemStack result, int count, long cost, double chance) {

        static final StreamCodec<RegistryFriendlyByteBuf, Next> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Next::result,
                ByteBufCodecs.VAR_INT, Next::count,
                ByteBufCodecs.VAR_LONG, Next::cost,
                ByteBufCodecs.DOUBLE, Next::chance,
                Next::new);
    }

    private static final StreamCodec<ByteBuf, MutationChamberBlockEntity.Stall> STALL_CODEC =
            ByteBufCodecs.idMapper(ordinal -> MutationChamberBlockEntity.Stall.values()[ordinal], MutationChamberBlockEntity.Stall::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, ChamberReport> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, ChamberReport::input,
            ByteBufCodecs.optional(Next.STREAM_CODEC), ChamberReport::next,
            ByteBufCodecs.BOOL, ChamberReport::awake,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG), ChamberReport::bestWorth,
            ByteBufCodecs.optional(STALL_CODEC), ChamberReport::stall,
            ChamberReport::new);

    /** @return what {@code chamber}, standing in {@code level}, shows */
    public static ChamberReport of(MutationChamberBlockEntity chamber, ServerLevel level) {
        var next = chamber.next().map(upgrade -> new Next(new ItemStack(upgrade.result()), upgrade.count(), upgrade.cost(), upgrade.chance()));
        boolean awake = chamber.dormancy().awake();
        OptionalLong best = awake ? TouchingMotes.bestWorth(level, chamber.getBlockPos()) : OptionalLong.empty();
        var stall = chamber.stall(level);
        if (!awake) {
            // Its stores are the Dormant payment's lines while it soaks its cost.
            stall = stall.filter(reason -> reason != MutationChamberBlockEntity.Stall.NO_MOTE_STORE
                    && reason != MutationChamberBlockEntity.Stall.CANNOT_AFFORD);
        }
        return new ChamberReport(chamber.input(), next, awake, best.isPresent() ? Optional.of(best.getAsLong()) : Optional.empty(), stall);
    }

    /**
     * @return what its input holds, or that it holds nothing; the upgrade; then, awake, its best
     *         touching store and that it is upgrading in green, or what stalls it in red
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        if (input.isEmpty()) {
            lines.add(Component.translatable("voidworks.jade.chamber.empty").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("voidworks.jade.chamber.holds", input.getCount(), input.getHoverName()));
        }
        next.ifPresent(upgrade -> lines.add(Component.translatable("voidworks.jade.chamber.upgrade", upgrade.count(),
                upgrade.result().getHoverName(), upgrade.cost(), Math.round(upgrade.chance() * 100))));
        boolean noStore = stall.equals(Optional.of(MutationChamberBlockEntity.Stall.NO_MOTE_STORE));
        if (awake && !noStore) {
            lines.add(TouchingMotes.describe(bestWorth.map(OptionalLong::of).orElseGet(OptionalLong::empty)));
        }
        stall.ifPresentOrElse(reason -> lines.add(stallLine(reason).withStyle(ChatFormatting.RED)), () -> {
            if (awake && !input.isEmpty()) {
                lines.add(Component.translatable("voidworks.jade.chamber.upgrading").withStyle(ChatFormatting.GREEN));
            }
        });
        return lines;
    }

    private MutableComponent stallLine(MutationChamberBlockEntity.Stall stall) {
        return switch (stall) {
            case NO_UPGRADE -> Component.translatable("voidworks.jade.chamber.stall.no_upgrade");
            case TOO_FEW -> Component.translatable("voidworks.jade.chamber.stall.too_few",
                    next.map(upgrade -> upgrade.count() - input.getCount()).orElse(0));
            case OUTPUT_FULL -> Component.translatable("voidworks.jade.chamber.stall.output_full");
            case NO_MOTE_STORE -> Component.translatable("voidworks.motes.none_touching");
            case CANNOT_AFFORD -> Component.translatable("voidworks.jade.chamber.stall.cannot_afford");
        };
    }
}
