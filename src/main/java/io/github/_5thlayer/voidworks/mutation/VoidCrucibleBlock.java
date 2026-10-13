// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/**
 * The Void Crucible's block: never Dormant, it works as soon as it is placed, glimmering while it
 * does, and its look, {@link #WORKING}, shows whether it is upgrading now. Its hand is every
 * Mutation block's ({@link MutationBlock}): a hand holding an item with no upgrade is told so
 * ({@link VoidCrucibleBlockEntity}).
 */
public final class VoidCrucibleBlock extends MutationBlock {

    public static final MapCodec<VoidCrucibleBlock> CODEC = simpleCodec(VoidCrucibleBlock::new);

    /** Whether it is upgrading now, which lights it; false while it is empty, stalled or waits, done. */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    /** How widely the glimmer spreads about the floating result, in block units. */
    private static final double GLIMMER_SPREAD = 0.6;
    /** How fast a glimmer drifts up, in blocks a tick. */
    private static final double GLIMMER_RISE = 0.01;

    public VoidCrucibleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected Component refusal(ItemStack stack) {
        return Component.translatable("voidworks.mutation.no_upgrade", stack.getHoverName());
    }

    /** While it works, a glimmer of particles about the result floating above it ({@link VoidCrucibleBlockEntity#glimmers}). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof VoidCrucibleBlockEntity crucible && crucible.glimmers()) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * GLIMMER_SPREAD,
                    pos.getY() + VoidCrucibleBlockEntity.FLOAT_HEIGHT + (random.nextDouble() - 0.5) * GLIMMER_SPREAD,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * GLIMMER_SPREAD,
                    0, GLIMMER_RISE, 0);
        }
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
