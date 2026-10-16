// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.inventory.ItemFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A Well hatch: a container a Matter-line dragon loads and unloads, placed against the outer face of
 * one of a Void Well's wall casing ({@link WellStructure#hatchFace}), facing away from it. It joins
 * that Well without being part of its structure, and a Well takes any number. Only the Well's owner
 * places one, and only they use it by hand; anywhere else, and against another's Well, it is refused.
 *
 * <p>What it holds is in the server's {@link WellHatches} ({@link WellHatchBlockEntity}), filled and
 * emptied through every face, never filled by hand, as befits mass transport. It is placed a provider,
 * and its owner switches it between provider and requester by sneak-using it with an empty hand. A
 * provider gives its owner's empty hand its first stack; a requester gives no hand anything, but
 * takes its request from its owner's: an item sets it to that item's kind and adds a stack, and an empty
 * hand clears it, each told above the hotbar. Its Well no longer whole, it is idle and keeps what it
 * holds; its core broken, it is a plain container until a new core there makes the Well whole again.
 * Broken, it drops what it holds.
 */
public final class WellHatchBlock extends BaseEntityBlock {

    public static final MapCodec<WellHatchBlock> CODEC = simpleCodec(WellHatchBlock::new);

    /** The way it faces, away from the wall casing it was placed against. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** How far from the casing a hatch is placed against the core of its Well may stand: two across the shaft, two to the shaft. */
    private static final int REACH = 4;

    public WellHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Refused, telling its placer why, anywhere but against a wall casing's outer face, and against another player's Well. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        var level = context.getLevel();
        var player = context.getPlayer();
        var well = context.replacingClickedOnBlock() ? Optional.<VoidWellBlockEntity>empty()
                : wellAgainst(level, context.getClickedPos().relative(face.getOpposite()), face);
        if (well.isEmpty()) {
            tell(level, player, Component.translatable("voidworks.hatch.not_on_wall"));
            return null;
        }
        if (!level.isClientSide() && (player == null || !well.get().owner().map(owner -> owner.id().equals(player.getUUID())).orElse(true))) {
            tell(level, player, well.get().ownedBy());
            return null;
        }
        return defaultBlockState().setValue(FACING, face);
    }

    private static void tell(Level level, @Nullable Player player, Component message) {
        if (!level.isClientSide() && player != null) {
            player.sendOverlayMessage(message);
        }
    }

    /** @return the core of the Well whose wall casing at {@code against} has {@code face} as its outer face, if one does */
    static Optional<VoidWellBlockEntity> wellAgainst(Level level, BlockPos against, Direction face) {
        if (!level.getBlockState(against).is(VoidworksBlocks.WELL_CASING.get())) {
            return Optional.empty();
        }
        for (var pos : BlockPos.betweenClosed(against.offset(-REACH, -1, -REACH), against.offset(REACH, 1, REACH))) {
            var state = level.getBlockState(pos);
            if (state.is(VoidworksBlocks.VOID_WELL.get()) && WellStructure.hatchFace(pos, state.getValue(VoidWellBlock.FACING), against, face)
                    && level.getBlockEntity(pos) instanceof VoidWellBlockEntity well) {
                return Optional.of(well);
            }
        }
        return Optional.empty();
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
        return new WellHatchBlockEntity(pos, state);
    }

    /**
     * Joins the Well it was placed against, as its owner's, holding nothing; a Well from before owners
     * becomes its placer's, as naming it does. What a hatch removed with no side effects left here is
     * dropped, not lost.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) {
            var facing = state.getValue(FACING);
            var well = wellAgainst(level, pos.relative(facing.getOpposite()), facing);
            if (placer instanceof Player player) {
                well.ifPresent(core -> core.ownsOrClaims(player));
            }
            var hatches = SavedWellHatches.of(server.getServer());
            hatches.remove(Place.of(server, pos)).ifPresent(stale -> ItemFace.drop(server, pos, stale.contents()));
            hatches.put(Place.of(server, pos), well.map(core -> Place.of(server, core.getBlockPos())),
                    well.flatMap(VoidWellBlockEntity::owner), VoidworksConfig.wellHatchSlots());
        }
    }

    /**
     * Used with an item it takes nothing, as it fills only through its faces: its owner sets a
     * requester's request to that item's kind, a stack more, or is told a provider fills through its
     * faces; anyone else is told whose it is. The item's own use never runs, so a block is not placed
     * against it but by sneaking, as against a chest.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof WellHatchBlockEntity entity)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        entity.hatch().filter(hatch -> usableBy(hatch, player)).ifPresent(hatch -> {
            if (hatch.mode() == WellHatches.Mode.REQUESTER) {
                var asked = hatch.ask(stack.getItem());
                player.sendOverlayMessage(Component.translatable("voidworks.hatch.requesting", asked.stacks(), asked.itemName()));
            } else {
                player.sendOverlayMessage(Component.translatable("voidworks.hatch.faces_only"));
            }
        });
        return InteractionResult.SUCCESS;
    }

    /**
     * Its owner sneak-using it with an empty hand switches it between provider and requester; using it
     * with one takes a provider's first stack, or clears a requester's request. Anyone else is told whose
     * it is.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof WellHatchBlockEntity entity)) {
            return InteractionResult.PASS;
        }
        entity.hatch().filter(hatch -> usableBy(hatch, player)).ifPresent(hatch -> {
            if (player.isSecondaryUseActive()) {
                player.sendOverlayMessage(Component.translatable(hatch.switchMode() == WellHatches.Mode.REQUESTER
                        ? "voidworks.hatch.now_requester" : "voidworks.hatch.now_provider"));
            } else if (hatch.mode() == WellHatches.Mode.REQUESTER) {
                hatch.clearRequest();
                player.sendOverlayMessage(Component.translatable("voidworks.hatch.requesting_nothing"));
            } else {
                giveFirstStack(hatch, player);
            }
        });
        return InteractionResult.SUCCESS;
    }

    private static void giveFirstStack(WellHatches.Hatch hatch, Player player) {
        var contents = hatch.contents();
        for (int slot = 0; slot < contents.size(); slot++) {
            var resource = contents.getResource(slot);
            if (!resource.isEmpty()) {
                try (var transaction = Transaction.openRoot()) {
                    int taken = contents.extract(slot, resource, contents.getAmountAsInt(slot), transaction);
                    transaction.commit();
                    player.setItemInHand(InteractionHand.MAIN_HAND, resource.toStack(taken));
                }
                return;
            }
        }
    }

    /** @return whether {@code player} may use {@code hatch} by hand: it is theirs, or no one's; anyone else is told whose it is */
    private static boolean usableBy(WellHatches.Hatch hatch, Player player) {
        if (hatch.owner().isEmpty() || hatch.owner().get().id().equals(player.getUUID())) {
            return true;
        }
        player.sendOverlayMessage(Component.translatable("voidworks.hatch.owned_by", hatch.owner().get().name()));
        return false;
    }
}
