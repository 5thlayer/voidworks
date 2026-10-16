// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.Map;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A Well hatch in hand, a provider's or a requester's, which places the hatch as that and which the
 * hatch drops as what it is. Sneak-used in the air, the whole stack turns into the other. The block's
 * own item, the one its recipe makes, is the provider's.
 */
public final class WellHatchItem extends BlockItem {

    private final WellHatches.Mode mode;

    public WellHatchItem(Block block, WellHatches.Mode mode, Properties properties) {
        super(block, properties);
        this.mode = mode;
    }

    /** @return what a hatch it places is */
    public WellHatches.Mode mode() {
        return mode;
    }

    /** @return the item a hatch that is {@code mode} is in hand */
    public static WellHatchItem of(WellHatches.Mode mode) {
        return mode == WellHatches.Mode.REQUESTER ? VoidworksBlocks.WELL_HATCH_REQUESTER_ITEM.get() : VoidworksBlocks.WELL_HATCH_ITEM.get();
    }

    /** Sneak-used in the air, the whole stack turns into the other mode's, heard as a shulker box opening; otherwise it does what any block in hand does. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            return super.use(level, player, hand);
        }
        var other = mode.other();
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), WellHatches.SWITCH_SOUND, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        return InteractionResult.SUCCESS.heldItemTransformedTo(player.getItemInHand(hand).transmuteCopy(of(other)));
    }

    /** Only the provider's is the block's own item, the one it is picked and named as when nothing says otherwise. */
    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        if (mode == WellHatches.Mode.PROVIDER) {
            super.registerBlocks(map, item);
        }
    }
}
