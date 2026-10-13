// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A dragon egg soaking up motes, {@code voidworks:soaking_dragon_egg}: what either egg becomes once a
 * player places it touching a mote store ({@link Hatching}). It is the vanilla egg's shape and
 * neither falls nor teleports. It shows how full it is in four {@link #STAGE stages}, glowing and
 * drawing in the void's sparks the more, and hatches a Dragonling once paid
 * ({@link SoakingEggBlockEntity}). Broken, it drops the egg it was and loses what it soaked. It has
 * no item; an empty hand is told how much of its price is paid.
 */
public final class SoakingEggBlock extends BaseEntityBlock {

    public static final MapCodec<SoakingEggBlock> CODEC = simpleCodec(SoakingEggBlock::new);

    /** Which egg it was, which it drops when broken. */
    public static final EnumProperty<EggKind> EGG = EnumProperty.create("egg", EggKind.class);
    /** Its last stage, of four. */
    public static final int LAST_STAGE = 3;
    /** How full it is: 0 until a quarter of its price is paid, 3 from three quarters. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, LAST_STAGE);

    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);

    public SoakingEggBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(EGG, EggKind.DRAGON_EGG).setValue(STAGE, 0));
    }

    /** @return the stage of an egg paid {@code paid} of {@code price}: a stage per quarter, the last running to the full price */
    static int stage(long paid, long price) {
        return price <= 0 ? LAST_STAGE : (int) Math.min(LAST_STAGE, (double) paid / price * (LAST_STAGE + 1));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EGG, STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoakingEggBlockEntity(pos, state);
    }

    /** An empty hand is told how much of its price is paid; it does not teleport. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SoakingEggBlockEntity egg)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.sendOverlayMessage(egg.dormancy().status());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(state.getValue(EGG).block());
    }

    /** The void's sparks drawn into it, more at each stage. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i <= state.getValue(STAGE); i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != VoidworksBlocks.SOAKING_EGG_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((SoakingEggBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
