// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import io.github._5thlayer.voidworks.dormant.Dormancy;
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
 * What every Void Displacer's block shares: once placed it cannot be broken or moved, and it ends as
 * Voidstone ({@link DisplacerBlockEntity}). An empty hand takes its output, or, with that empty, its
 * fed blocks back, or, with none, is told how much of a Dormant one's cost is paid; sneak-using an
 * awake one with an empty hand buys a Zone upgrade ({@link DisplacerBlockEntity#sneakUse}); a hand
 * holding something, a Void Siphon paying it above all, passes to its item.
 */
public abstract class DisplacerBlock extends BaseEntityBlock {

    protected DisplacerBlock(Properties properties) {
        super(properties);
    }

    /** @return the type of its block entity, the one it ticks */
    protected abstract BlockEntityType<? extends DisplacerBlockEntity> entityType();

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof DisplacerBlockEntity displacer)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        var dormancy = displacer instanceof Dormancy.Holder machine && !machine.dormancy().awake() ? machine.dormancy() : null;
        if (player.isSecondaryUseActive() && dormancy == null) {
            player.sendOverlayMessage(displacer.sneakUse((ServerLevel) level, player));
            return InteractionResult.SUCCESS;
        }
        displacer.lapseQuote();
        if (displacer.giveOutput(player) || displacer.giveBackFed(player)) {
            return InteractionResult.SUCCESS;
        }
        if (dormancy != null) {
            player.sendOverlayMessage(dormancy.status());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != entityType()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((DisplacerBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
