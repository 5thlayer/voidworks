// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The Overworld Void Displacer: crying obsidian, ender pearls and a sticky piston, costing no motes. */
public final class OverworldDisplacerBlock extends DisplacerBlock {

    public static final MapCodec<OverworldDisplacerBlock> CODEC = simpleCodec(OverworldDisplacerBlock::new);

    public OverworldDisplacerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends DisplacerBlockEntity> entityType() {
        return VoidworksBlocks.OVERWORLD_DISPLACER_ENTITY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OverworldDisplacerBlockEntity(pos, state);
    }
}
