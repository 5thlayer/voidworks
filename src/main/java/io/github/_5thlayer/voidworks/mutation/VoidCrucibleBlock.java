// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Void Crucible's block: never Dormant, it works as soon as it is placed. Its hand is every
 * Mutation block's ({@link MutationBlock}): a hand holding an item with no upgrade is told so
 * ({@link VoidCrucibleBlockEntity}).
 */
public final class VoidCrucibleBlock extends MutationBlock {

    public static final MapCodec<VoidCrucibleBlock> CODEC = simpleCodec(VoidCrucibleBlock::new);

    public VoidCrucibleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected Component refusal(ItemStack stack) {
        return Component.translatable("voidworks.mutation.no_upgrade", stack.getHoverName());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidCrucibleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != VoidworksBlocks.VOID_CRUCIBLE_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((VoidCrucibleBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
