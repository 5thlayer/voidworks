// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Void Well's block entity: Dormant until paid, then a store of its network. Its motes are the
 * network's, saved with the dimension's networks, not with it; awake, it reaches them through
 * {@code voidworks:motes}, so they conduct to and from the Voidstone it touches.
 */
public final class VoidWellBlockEntity extends BlockEntity implements Dormancy.Holder {

    private final Dormancy dormancy = new Dormancy(this);

    public VoidWellBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.VOID_WELL_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    /** Soaks its cost while Dormant; once awake, it is in its network, joining it the first tick it is. */
    void serverTick(ServerLevel level) {
        if (dormancy.soak(level)) {
            VoidstoneNetworks.of(level).join(worldPosition, StoreKind.WELL);
        }
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
