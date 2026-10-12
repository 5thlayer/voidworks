// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What every Mutation block's block shares, the hand of its {@link MutationItems}: it has no screen.
 * A hand holding an item puts it into the input, if the block's rule accepts it and the input has
 * room, and is told why if not; a Void Siphon passes to its item, to pay a Dormant one. An empty hand
 * takes every output, or with none takes back the input, or with nothing held is told how much of a
 * Dormant one's cost is paid.
 */
public abstract class MutationBlock extends BaseEntityBlock {

    protected MutationBlock(Properties properties) {
        super(properties);
    }

    /** @return what a hand holding {@code stack}, which the input does not accept, is told */
    protected abstract Component refusal(ItemStack stack);

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.isEmpty() || stack.getItem() instanceof VoidSiphonItem) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // What the input accepts is the server's to say, so the client only says the use went through.
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof MutationItems.Holder holder)) {
            return InteractionResult.SUCCESS;
        }
        var items = holder.items();
        if (!items.accepts(ItemResource.of(stack))) {
            player.sendOverlayMessage(refusal(stack));
        } else if (items.insertFromHand(stack) == 0) {
            player.sendOverlayMessage(Component.translatable("voidworks.mutation.input_full", items.held(MutationItems.INPUT).getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof MutationItems.Holder holder)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (holder.items().giveTo(player)) {
            return InteractionResult.SUCCESS;
        }
        if (holder instanceof Dormancy.Holder machine && !machine.dormancy().awake()) {
            player.sendOverlayMessage(machine.dormancy().status());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
