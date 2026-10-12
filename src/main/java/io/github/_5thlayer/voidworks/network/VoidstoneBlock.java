// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Voidstone: what every harvested position becomes, and a spent Void Displacer too. It cannot be
 * broken or moved and is never an item, so harvesting permanently consumes the world. However it is
 * placed, it joins the network of the Voidstone it touches, and it holds its share of that network's
 * motes, reached through {@code voidworks:motes}.
 */
public final class VoidstoneBlock extends Block {

    public static final MapCodec<VoidstoneBlock> CODEC = simpleCodec(VoidstoneBlock::new);

    public VoidstoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            VoidstoneNetworks.of(server).join(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        VoidstoneNetworks.of(level).leave(pos);
    }
}
