// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Voidstone: what every harvested position becomes, and a spent Void Displacer too. It cannot be
 * broken or moved and is never an item, so harvesting permanently consumes the world. However it is
 * placed, it joins the network of the Voidstone it touches, and it holds its share of that network's
 * motes, reached through {@code voidworks:motes}.
 *
 * <p>It looks like the dimension it was placed in, by that dimension's harvest kind ({@link #KIND}):
 * the End Void Displacer's casing yellows where it is {@code end}, the Nether Void Displacer's casing
 * reds where it is {@code nether}, the void's purples elsewhere. The look is all that differs; it is one block, and its networks
 * are the same whatever it looks like.
 */
public final class VoidstoneBlock extends Block {

    public static final MapCodec<VoidstoneBlock> CODEC = simpleCodec(VoidstoneBlock::new);

    /** The harvest kind of the dimension it was placed in, which picks its look; the Overworld's by default. */
    public static final EnumProperty<HarvestKind> KIND = EnumProperty.create("kind", HarvestKind.class);

    public VoidstoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, HarvestKind.OVERWORLD));
    }

    /** @return Voidstone as it looks in {@code level}'s dimension, which every harvest places */
    public BlockState in(ServerLevel level) {
        return defaultBlockState().setValue(KIND, VoidPressure.harvestKind(level));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
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
