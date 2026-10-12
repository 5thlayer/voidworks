// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Nether Void Displacer: Nether materials, placed Dormant until it has soaked its cost, the
 * {@code voidworks:dormant_cost} entry for its block. Its look shows whether it is Dormant. An empty
 * bucket, or any fluid item with room for lava, used on it takes lava from its tank
 * ({@link NetherDisplacerBlockEntity}).
 */
public final class NetherDisplacerBlock extends DisplacerBlock {

    public static final MapCodec<NetherDisplacerBlock> CODEC = simpleCodec(NetherDisplacerBlock::new);

    public NetherDisplacerBlock(Properties properties) {
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

    /**
     * A fluid item with room for lava fills from its tank; anything else, a full bucket or a Void
     * Siphon above all, goes on as for any Displacer.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!takesLava(player, hand)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        // The tank is the server's, so the client only says the use went through.
        if (level.isClientSide() || FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection(), null)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /** @return whether the item in {@code hand} is a fluid item with room for a bucket of lava, tried and rolled back */
    private static boolean takesLava(Player player, InteractionHand hand) {
        var item = ItemAccess.forPlayerInteraction(player, hand).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (item == null) {
            return false;
        }
        try (var transaction = Transaction.openRoot()) {
            return item.insert(FluidResource.of(Fluids.LAVA), FluidType.BUCKET_VOLUME, transaction) == FluidType.BUCKET_VOLUME;
        }
    }

    @Override
    protected BlockEntityType<? extends DisplacerBlockEntity> entityType() {
        return VoidworksBlocks.NETHER_DISPLACER_ENTITY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetherDisplacerBlockEntity(pos, state);
    }
}
