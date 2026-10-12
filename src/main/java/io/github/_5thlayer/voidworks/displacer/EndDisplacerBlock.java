// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The End Void Displacer: End materials, placed Dormant until it has soaked its cost, the
 * {@code voidworks:dormant_cost} entry for its block. Its look shows whether it is Dormant.
 */
public final class EndDisplacerBlock extends DisplacerBlock {

    public static final MapCodec<EndDisplacerBlock> CODEC = simpleCodec(EndDisplacerBlock::new);

    public EndDisplacerBlock(Properties properties) {
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
    protected BlockEntityType<? extends DisplacerBlockEntity> entityType() {
        return VoidworksBlocks.END_DISPLACER_ENTITY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndDisplacerBlockEntity(pos, state);
    }
}
