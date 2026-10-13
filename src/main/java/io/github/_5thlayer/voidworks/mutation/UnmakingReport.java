// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * What an Unmaking Chamber shows a player looking at it, beside Jade's own view of what it holds: that
 * its input is empty, the breakdown with its count and cost, its current yield, and, once awake, what
 * the mote stores touching it are worth there together and whether it is breaking down or what stalls
 * it. Read on the server, where recipes, mote stores and Void Pressure are, and sent to the client,
 * which words it with {@link #lines}. As on the Mutation Chamber ({@link ChamberReport}), a Dormant
 * one's payment is shown beside it, so a Dormant one shows only what stalls its items.
 *
 * @param input what waits to break down, empty for nothing
 * @param breakdown its breakdown, empty for an empty input or an item with none
 * @param yield the chance each ingredient item comes back
 * @param worth what the stores touching it are worth there together, in void energy; empty while Dormant or with none touching it
 */
public record UnmakingReport(ItemStack input, Optional<Shown> breakdown, double yield, boolean awake, Optional<Long> worth,
        Optional<UnmakingChamberBlockEntity.Stall> stall) {

    /** A breakdown as a player is shown it: the count it takes, each ingredient that may come back, and its cost. */
    public record Shown(int count, List<ItemStack> returns, long cost) {

        static final StreamCodec<RegistryFriendlyByteBuf, Shown> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Shown::count,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Shown::returns,
                ByteBufCodecs.VAR_LONG, Shown::cost,
                Shown::new);
    }

    private static final StreamCodec<ByteBuf, UnmakingChamberBlockEntity.Stall> STALL_CODEC =
            ByteBufCodecs.idMapper(ordinal -> UnmakingChamberBlockEntity.Stall.values()[ordinal], UnmakingChamberBlockEntity.Stall::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, UnmakingReport> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, UnmakingReport::input,
            ByteBufCodecs.optional(Shown.STREAM_CODEC), UnmakingReport::breakdown,
            ByteBufCodecs.DOUBLE, UnmakingReport::yield,
            ByteBufCodecs.BOOL, UnmakingReport::awake,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG), UnmakingReport::worth,
            ByteBufCodecs.optional(STALL_CODEC), UnmakingReport::stall,
            UnmakingReport::new);

    /** @return what {@code chamber}, standing in {@code level}, shows */
    public static UnmakingReport of(UnmakingChamberBlockEntity chamber, ServerLevel level) {
        var breakdown = chamber.breakdown().map(next -> new Shown(next.count(),
                next.returns().entrySet().stream().map(entry -> new ItemStack(entry.getKey(), entry.getValue())).toList(), next.cost()));
        boolean awake = chamber.dormancy().awake();
        // While it soaks its cost, its stores are the Dormant payment's lines.
        var worth = awake ? TouchingMotes.totalWorth(level, chamber.getBlockPos()) : Optional.<Long>empty();
        return new UnmakingReport(chamber.input(), breakdown, chamber.currentYield(), awake, worth, chamber.stall(level));
    }

    /**
     * @return that it holds nothing, if so; the breakdown; its yield; then, awake, what its touching
     *         stores are worth and that it is breaking down in green, or what stalls it in red
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        // What it holds is Jade's own item view, from its item capability.
        if (input.isEmpty()) {
            lines.add(Component.translatable("voidworks.jade.unmaking.empty").withStyle(ChatFormatting.GRAY));
        }
        breakdown.ifPresent(shown -> lines.add(Component.translatable("voidworks.jade.unmaking.breakdown", shown.count(), returns(shown), shown.cost())));
        lines.add(Component.translatable("voidworks.jade.unmaking.yield", Math.round(yield * 100)));
        boolean noStore = stall.equals(Optional.of(UnmakingChamberBlockEntity.Stall.NO_MOTE_STORE));
        if (awake && !noStore) {
            lines.add(TouchingMotes.describe(worth));
        }
        stall.ifPresentOrElse(reason -> lines.add(stallLine(reason).withStyle(ChatFormatting.RED)), () -> {
            if (awake && !input.isEmpty()) {
                lines.add(Component.translatable("voidworks.jade.unmaking.breaking_down").withStyle(ChatFormatting.GREEN));
            }
        });
        return lines;
    }

    /** @return each ingredient that may come back, as "2 Oak Planks, 1 Stick" */
    private static Component returns(Shown shown) {
        var joined = Component.empty();
        for (int i = 0; i < shown.returns().size(); i++) {
            var stack = shown.returns().get(i);
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(Component.translatable("voidworks.jade.unmaking.ingredient", stack.getCount(), stack.getHoverName()));
        }
        return joined;
    }

    private MutableComponent stallLine(UnmakingChamberBlockEntity.Stall stall) {
        return switch (stall) {
            case NO_BREAKDOWN -> Component.translatable("voidworks.jade.unmaking.stall.no_breakdown");
            case TOO_FEW -> UpgradeLines.tooFew(breakdown.map(shown -> shown.count() - input.getCount()).orElse(0));
            case OUTPUT_FULL -> Component.translatable("voidworks.jade.unmaking.stall.output_full");
            case NO_MOTE_STORE -> Component.translatable("voidworks.motes.none_touching");
            case CANNOT_AFFORD -> Component.translatable("voidworks.jade.chamber.stall.cannot_afford");
        };
    }
}
