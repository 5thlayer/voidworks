// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The Mutation Chamber's block: placed Dormant until it has soaked its cost, the
 * {@code voidworks:dormant_cost} entry for its block, and its look shows whether it is. It has no
 * screen. A hand holding an item puts it into the input, if it has an upgrade, and is told so if not;
 * a Void Siphon passes to its item, to pay a Dormant one. An empty hand takes what upgraded, or with
 * none takes back what waits to, or with nothing held is told how much of a Dormant one's cost is
 * paid ({@link MutationChamberBlockEntity}).
 */
public final class MutationChamberBlock extends BaseEntityBlock {

    public static final MapCodec<MutationChamberBlock> CODEC = simpleCodec(MutationChamberBlock::new);

    public MutationChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Dormancy.DORMANT, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Dormancy.DORMANT);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.isEmpty() || stack.getItem() instanceof VoidSiphonItem) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // Families are the server's, so the client only says the use went through.
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof MutationChamberBlockEntity chamber)) {
            return InteractionResult.SUCCESS;
        }
        if (!MutationChamberBlockEntity.upgrades(ItemResource.of(stack))) {
            player.sendOverlayMessage(Component.translatable("voidworks.mutation.no_upgrade", stack.getHoverName()));
            return InteractionResult.SUCCESS;
        }
        chamber.insertFromHand(stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof MutationChamberBlockEntity chamber)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (chamber.giveTo(player)) {
            return InteractionResult.SUCCESS;
        }
        if (!chamber.dormancy().awake()) {
            player.sendOverlayMessage(chamber.dormancy().status());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MutationChamberBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != VoidworksBlocks.MUTATION_CHAMBER_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((MutationChamberBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
