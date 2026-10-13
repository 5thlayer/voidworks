// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;

/**
 * What a Void Crucible shows a player looking at it, beside Jade's own view of what it holds: that
 * its input is empty, the upgrade it climbs to with its count and cost, and either how far along it
 * is with the time left where it stands and how many working Crucibles share its rate, or what
 * stalls it. Read on the server, where Families and Void Pressure are, and sent to the client, which
 * words it with {@link #lines}.
 *
 * @param input     what waits to upgrade, empty for nothing
 * @param next      the upgrade it climbs to, empty for an empty input or an item with none
 * @param progress  how much of one result is done, from 0 to 1
 * @param ticksLeft the ticks left before the result is done, at the rate it works now; 0 when done or idle
 * @param sharing   the working Crucibles sharing its rate, itself included
 */
public record CrucibleReport(ItemStack input, Optional<Next> next, double progress, long ticksLeft, int sharing,
        Optional<VoidCrucibleBlockEntity.Stall> stall) {

    /** An upgrade as a player is shown it: the result, and the count and cost that set how long it takes. */
    public record Next(ItemStack result, int count, long cost) {

        static final StreamCodec<RegistryFriendlyByteBuf, Next> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Next::result,
                ByteBufCodecs.VAR_INT, Next::count,
                ByteBufCodecs.VAR_LONG, Next::cost,
                Next::new);
    }

    private static final StreamCodec<ByteBuf, VoidCrucibleBlockEntity.Stall> STALL_CODEC =
            ByteBufCodecs.idMapper(ordinal -> VoidCrucibleBlockEntity.Stall.values()[ordinal], VoidCrucibleBlockEntity.Stall::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleReport> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, CrucibleReport::input,
            ByteBufCodecs.optional(Next.STREAM_CODEC), CrucibleReport::next,
            ByteBufCodecs.DOUBLE, CrucibleReport::progress,
            ByteBufCodecs.VAR_LONG, CrucibleReport::ticksLeft,
            ByteBufCodecs.VAR_INT, CrucibleReport::sharing,
            ByteBufCodecs.optional(STALL_CODEC), CrucibleReport::stall,
            CrucibleReport::new);

    /** @return what {@code crucible}, standing in {@code level}, shows */
    public static CrucibleReport of(VoidCrucibleBlockEntity crucible, ServerLevel level) {
        var next = crucible.next().map(upgrade -> new Next(new ItemStack(upgrade.result()), upgrade.count(), upgrade.cost()));
        return new CrucibleReport(crucible.input(), next, crucible.progress(), crucible.ticksLeft(level), crucible.sharing(level),
                crucible.stall());
    }

    /**
     * @return that it holds nothing, if so; the upgrade; then in green how far along it is and the
     *         time left, with how many working Crucibles share its rate, or in red what stalls it
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        // What it holds is Jade's own item view, from its item capability.
        if (input.isEmpty()) {
            lines.add(Component.translatable("voidworks.jade.crucible.empty").withStyle(ChatFormatting.GRAY));
        }
        next.ifPresent(upgrade -> lines.add(Component.translatable("voidworks.jade.crucible.upgrade", upgrade.count(),
                upgrade.result().getHoverName(), upgrade.cost())));
        stall.ifPresentOrElse(reason -> lines.add(stallLine(reason).withStyle(ChatFormatting.RED)), () -> {
            if (!input.isEmpty()) {
                lines.add(Component.translatable("voidworks.jade.crucible.upgrading", Math.round(progress * 100),
                        StringUtil.formatTickDuration((int) Math.min(ticksLeft, Integer.MAX_VALUE), 20.0F)).withStyle(ChatFormatting.GREEN));
                if (sharing > 1) {
                    lines.add(Component.translatable("voidworks.jade.crucible.sharing", sharing).withStyle(ChatFormatting.GOLD));
                }
            }
        });
        return lines;
    }

    private MutableComponent stallLine(VoidCrucibleBlockEntity.Stall stall) {
        return switch (stall) {
            case NO_UPGRADE -> Component.translatable("voidworks.jade.crucible.stall.no_upgrade");
            case TOO_FEW -> Component.translatable("voidworks.jade.crucible.stall.too_few",
                    next.map(upgrade -> upgrade.count() - input.getCount()).orElse(0));
            case OUTPUT_FULL -> Component.translatable("voidworks.jade.crucible.stall.output_full");
        };
    }
}
