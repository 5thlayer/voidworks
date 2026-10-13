// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Void Well: one block, a large store of motes in its network. It is placed Dormant until it has
 * soaked its cost, the {@code voidworks:dormant_cost} entry for its block, and then joins the network
 * of the Voidstone it touches ({@link VoidWellBlockEntity}). Broken, it loses the motes it held. An
 * empty hand is told how much of its cost is paid while it is Dormant.
 */
public final class VoidWellBlock extends BaseEntityBlock {

    public static final MapCodec<VoidWellBlock> CODEC = simpleCodec(VoidWellBlock::new);

    public VoidWellBlock(Properties properties) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidWellBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof VoidWellBlockEntity well) || well.dormancy().awake()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.sendOverlayMessage(well.dormancy().status());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        VoidstoneNetworks.of(level).leave(pos);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != VoidworksBlocks.VOID_WELL_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((VoidWellBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
