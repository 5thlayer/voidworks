// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.inventory;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The item capability of a block that takes into one input and gives from its other slots, the same
 * on every side: inserts go only to the input, and only of what its rule accepts; extracts come only
 * from the other slots. It shows every slot as it is, and holds nothing of its own. The Displacers
 * and the Mutation blocks stand on it.
 */
public final class ItemFace implements ResourceHandler<ItemResource> {

    private final ItemStacksResourceHandler inventory;
    private final int input;
    private final Predicate<ItemResource> accepts;

    /**
     * @param inventory what the block holds
     * @param input     the slot that takes
     * @param accepts   whether the input takes an item, never asked of nothing
     */
    public ItemFace(ItemStacksResourceHandler inventory, int input, Predicate<ItemResource> accepts) {
        this.inventory = inventory;
        this.input = input;
        this.accepts = accepts;
    }

    /** Drops everything the block holds at the centre of {@code pos}, as it is removed. */
    public void drop(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return inventory.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return inventory.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() || isValid(index, resource) ? inventory.getCapacityAsLong(index, resource) : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index == input && !resource.isEmpty() && accepts.test(resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return isValid(index, resource) ? inventory.insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index == input ? 0 : inventory.extract(index, resource, amount, transaction);
    }
}
