// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Overworld Void Displacer: it swaps fed blocks with the natural terrain touching it or the
 * Voidstone it has left, and sends the terrain block, through the displacement table, to its
 * output. It costs no motes, so it is never Dormant.
 */
public final class OverworldDisplacerBlockEntity extends DisplacerBlockEntity {

    public OverworldDisplacerBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.OVERWORLD_DISPLACER_ENTITY.get(), pos, state);
    }

    @Override
    protected HarvestKind kind() {
        return HarvestKind.OVERWORLD;
    }

    @Override
    protected boolean harvestable(BlockState state) {
        return state.is(VoidworksBlocks.OVERWORLD_TERRAIN);
    }

    @Override
    protected ItemStack displaced(BlockState harvested, int density) {
        return Displacement.result(harvested, density);
    }
}
