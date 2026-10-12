// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Overworld Void Displacer: crying obsidian, ender pearls and a sticky piston, costing no motes.
 * Once placed it cannot be broken or moved; it ends as Voidstone ({@link OverworldDisplacerBlockEntity}).
 * An empty hand takes back its fed blocks.
 */
public final class OverworldDisplacerBlock extends BaseEntityBlock {

    public static final MapCodec<OverworldDisplacerBlock> CODEC = simpleCodec(OverworldDisplacerBlock::new);

    public OverworldDisplacerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OverworldDisplacerBlockEntity(pos, state);
    }

    /** An empty hand takes back the fed blocks. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof OverworldDisplacerBlockEntity displacer) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return displacer.giveBackFed(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != VoidworksBlocks.OVERWORLD_DISPLACER_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((OverworldDisplacerBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
