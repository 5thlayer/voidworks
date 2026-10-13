// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.Nullable;

/**
 * The Unmaking Chamber's block: placed Dormant until it has soaked its cost, the
 * {@code voidworks:dormant_cost} entry for its block, and its look shows whether it is. Its hand is
 * every Mutation block's ({@link MutationBlock}): a hand holding an item with no breakdown is told so
 * ({@link UnmakingChamberBlockEntity}).
 */
public final class UnmakingChamberBlock extends MutationBlock {

    public static final MapCodec<UnmakingChamberBlock> CODEC = simpleCodec(UnmakingChamberBlock::new);

    public UnmakingChamberBlock(Properties properties) {
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
    protected Component refusal(ItemStack stack) {
        return Component.translatable("voidworks.mutation.no_breakdown", stack.getHoverName());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UnmakingChamberBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != VoidworksBlocks.UNMAKING_CHAMBER_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((UnmakingChamberBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
