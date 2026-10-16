// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.inventory.ItemFace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * A Well hatch's block entity, which holds nothing of its own: what the hatch holds is its entry in the
 * server's {@link WellHatches}, saved with the world, so a dragon loads and unloads it with no chunk
 * loaded. Its item faces are that entry's slots. A hatch placed with no player, as by a command, joined
 * no Well and is anyone's; it is put there, holding nothing, the first time it is asked for.
 */
public final class WellHatchBlockEntity extends BlockEntity {

    public WellHatchBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.WELL_HATCH_ENTITY.get(), pos, state);
    }

    /** @return its entry in the server's {@link WellHatches}; empty for a watcher, which has none */
    public Optional<WellHatches.Hatch> hatch() {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        var place = Place.of(server, worldPosition);
        var hatches = SavedWellHatches.of(server.getServer());
        return Optional.of(hatches.at(place).orElseGet(() -> hatches.put(place, Optional.empty(), Optional.empty(), VoidworksConfig.wellHatchSlots())));
    }

    /** @return what it holds, the same on every face: hoppers, belts and pipes fill and empty it; null for a watcher */
    public @Nullable ResourceHandler<ItemResource> itemFace() {
        return hatch().<ResourceHandler<ItemResource>>map(WellHatches.Hatch::contents).orElse(null);
    }

    /** Broken, it leaves the server's hatches and drops what it held. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        SavedWellHatches.of(server.getServer()).remove(Place.of(server, pos)).ifPresent(hatch -> ItemFace.drop(server, pos, hatch.contents()));
    }
}
