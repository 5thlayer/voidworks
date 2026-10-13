// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Void Well's core: the store of the well built around it ({@link WellStructure}), set in the
 * middle of one wall facing out, the way its placer looked from outside. It is placed Dormant, and
 * once the well is whole soaks its cost, the {@code voidworks:dormant_cost} entry for its block; then
 * it joins the network of the Voidstone it touches ({@link VoidWellBlockEntity}). Broken, it loses
 * the motes it held.
 *
 * <p>The player who places it owns it. Every control is in the world: its owner sneak-using it with
 * an empty hand cycles its {@link FillTarget}, and a name tag its owner uses on it names it and is
 * used up, as naming a mob is; anyone else is told whose it is. Used plainly, by anyone, it shows
 * whether the well is whole or how much of its cost is paid, and its target.
 */
public final class VoidWellBlock extends BaseEntityBlock {

    public static final MapCodec<VoidWellBlock> CODEC = simpleCodec(VoidWellBlock::new);

    /** The way it faces, out of its wall. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public VoidWellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Dormancy.DORMANT, true).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Dormancy.DORMANT, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidWellBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof VoidWellBlockEntity well) {
            well.claim(player);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        var name = stack.get(DataComponents.CUSTOM_NAME);
        if (!stack.is(Items.NAME_TAG) || name == null || !(level.getBlockEntity(pos) instanceof VoidWellBlockEntity well)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (!level.isClientSide()) {
            if (well.ownsOrClaims(player)) {
                well.rename(name);
                stack.shrink(1);
            } else {
                player.sendOverlayMessage(well.ownedBy());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof VoidWellBlockEntity well)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) {
                player.sendOverlayMessage(Component.translatable("voidworks.well.use", well.status(level), well.target().label()));
            } else if (well.ownsOrClaims(player)) {
                well.cycleTarget();
                player.sendOverlayMessage(Component.translatable("voidworks.well.target_set", well.target().label()));
            } else {
                player.sendOverlayMessage(well.ownedBy());
            }
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
