// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The End Void Displacer: it places fed blocks into the air ahead of its front face, touching it or
 * the Voidstone it has left, so its body grows outward that way as a pyramid widening from that face,
 * never walling in its other faces nor roofing over the ground around it, and nothing comes back to
 * its output. Over the open void its
 * harvests grade one step higher, as the Void Pressure there is, and each Voidstone it leaves closes
 * the column above it. It is placed Dormant, and harvests only once it has soaked its cost.
 */
public final class EndDisplacerBlockEntity extends DisplacerBlockEntity implements Dormancy.Holder {

    private final Dormancy dormancy = new Dormancy(this);

    public EndDisplacerBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.END_DISPLACER_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    @Override
    protected HarvestKind kind() {
        return HarvestKind.END;
    }

    @Override
    protected boolean reaches(BlockPos pos) {
        return Frontier.ahead(worldPosition, getBlockState().getValue(EndDisplacerBlock.FACING)).test(pos);
    }

    @Override
    protected boolean harvestable(BlockState state) {
        return state.isAir();
    }

    @Override
    protected ItemStack displaced(BlockState harvested, int density) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean ready(ServerLevel level) {
        return dormancy.soak(level);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        dormancy.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dormancy.load(input);
    }
}
