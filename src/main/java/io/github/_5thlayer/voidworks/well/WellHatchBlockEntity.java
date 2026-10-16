// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.inventory.ItemFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * A Well hatch's block entity, which holds nothing of its own: what the hatch holds is its entry in the
 * server's {@link WellHatches}, saved with the world, so a dragon loads and unloads it with no chunk
 * loaded. Its item faces are that entry's slots. A hatch placed with no player, as by a command, joined
 * no Well and is anyone's; it is put there, holding nothing, the first time it is asked for.
 *
 * <p>Its watchers draw one item on its front ({@link #shown}): a requester's request, or the first
 * stack a provider holds, which the server works out each tick and sends them as it changes.
 */
public final class WellHatchBlockEntity extends BlockEntity {

    /**
     * A watcher's item face, with no slots: it knows nothing of what the hatch holds, but a mod there
     * that looks for a face before asking the server what a block holds, as Jade does, finds one.
     */
    private static final ResourceHandler<ItemResource> EMPTY = new ItemStacksResourceHandler(0);

    private static final String SHOWN_KEY = "shown";

    private ItemStack shown = ItemStack.EMPTY;

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
        return Optional.of(hatches.at(place).orElseGet(() -> hatches.put(place, Optional.empty(), Optional.empty(), getBlockState().getValue(WellHatchBlock.MODE),
                VoidworksConfig.wellHatchSlots())));
    }

    /** @return what it holds, the same on every face: hoppers, belts and pipes fill and empty it; for a watcher, a face holding nothing */
    public ResourceHandler<ItemResource> itemFace() {
        return hatch().<ResourceHandler<ItemResource>>map(WellHatches.Hatch::contents).orElse(EMPTY);
    }

    /**
     * @return the item drawn on its front, one of it: a requester's request, or the first stack a
     *         provider holds; empty for a requester asking for nothing and a provider holding nothing
     */
    public ItemStack shown() {
        return shown;
    }

    /** Sends its watchers the item drawn on its front when that changes. */
    void serverTick(ServerLevel level) {
        var now = hatch().map(WellHatchBlockEntity::toShow).orElse(ItemStack.EMPTY);
        if (!ItemStack.matches(now, shown)) {
            shown = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static ItemStack toShow(WellHatches.Hatch hatch) {
        if (hatch.mode() == WellHatches.Mode.REQUESTER) {
            return hatch.request().map(asked -> new ItemStack(asked.item())).orElse(ItemStack.EMPTY);
        }
        var contents = hatch.contents();
        for (int slot = 0; slot < contents.size(); slot++) {
            if (!contents.getResource(slot).isEmpty()) {
                return contents.getResource(slot).toStack(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Only what its watchers draw, the item on its front, never saved, which it works out again each tick. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        if (!shown.isEmpty()) {
            output.store(SHOWN_KEY, ItemStack.CODEC, shown);
        }
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as the chunk arrives. */
    @Override
    public void handleUpdateTag(ValueInput input) {
        shown = input.read(SHOWN_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as it changes. */
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        shown = input.read(SHOWN_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
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
