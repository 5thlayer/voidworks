// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.function.Predicate;

import io.github._5thlayer.voidworks.inventory.ItemFace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * What every Mutation block holds, and how items go in and out of it: an input, which takes only
 * what the block's own rule accepts, and its outputs, as many as the block has. Its item face, the
 * same on every side, takes only into the input and gives only from the outputs, so hoppers and
 * belts feed and empty it; a hand puts items into the input, and an empty one takes every output, or
 * with none takes back the input ({@link MutationBlock}). Broken, the block drops all of it.
 *
 * <p>Each of the Module's Mutation blocks stands on it, its block entity a {@link Holder} providing
 * {@link #itemFace} as its item capability, saving and loading it with {@link #save} and
 * {@link #load} and calling {@link #drop} when removed, and its block a {@link MutationBlock}.
 */
public final class MutationItems {

    /** The slot of what waits to Mutate. */
    public static final int INPUT = 0;
    /** The slot of the first output; the others follow it. */
    public static final int FIRST_OUTPUT = 1;

    private static final String KEY = "items";

    private final Predicate<ItemResource> accepts;
    private final ItemStacksResourceHandler inventory;
    private final ItemFace face;

    /**
     * @param outputs   how many output slots it has, at least one
     * @param accepts   whether the input takes an item, never asked of nothing
     * @param onChanged called whenever what it holds changes, a block entity's {@code setChanged}
     */
    public MutationItems(int outputs, Predicate<ItemResource> accepts, Runnable onChanged) {
        if (outputs < 1) {
            throw new IllegalArgumentException("a Mutation block has at least one output, not " + outputs);
        }
        this.accepts = accepts;
        this.inventory = new ItemStacksResourceHandler(FIRST_OUTPUT + outputs) {
            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                onChanged.run();
            }
        };
        this.face = new ItemFace(inventory, INPUT, accepts);
    }

    /** A Mutation block's block entity, so its block finds what it holds. */
    public interface Holder {

        /** @return what it holds, the same each time */
        MutationItems items();
    }

    /** Its input, then its outputs, for the block's own work and the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    /** The item capability, the same on every side: the input takes only what the rule accepts, and only the outputs give. */
    public ResourceHandler<ItemResource> itemFace() {
        return face;
    }

    /** @return whether the input takes an item of {@code resource}; never nothing */
    public boolean accepts(ItemResource resource) {
        return !resource.isEmpty() && accepts.test(resource);
    }

    /** @return what {@code slot} holds, empty for nothing */
    public ItemStack held(int slot) {
        return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
    }

    /**
     * Puts what {@code stack} holds into the input, as much as fits, as a player's hand does.
     *
     * @return how many it took: none for an item the rule refuses, or an input holding another
     */
    public int insertFromHand(ItemStack stack) {
        var resource = ItemResource.of(stack);
        try (var transaction = Transaction.openRoot()) {
            int taken = face.insert(INPUT, resource, stack.getCount(), transaction);
            transaction.commit();
            stack.shrink(taken);
            return taken;
        }
    }

    /**
     * Gives {@code player} every output, or, with every output empty, takes back the input.
     *
     * @return whether there was anything to give
     */
    public boolean giveTo(Player player) {
        boolean gave = false;
        for (int slot = FIRST_OUTPUT; slot < inventory.size(); slot++) {
            gave |= giveSlot(player, slot);
        }
        return gave || giveSlot(player, INPUT);
    }

    private boolean giveSlot(Player player, int slot) {
        var stack = held(slot);
        if (stack.isEmpty()) {
            return false;
        }
        inventory.set(slot, ItemResource.EMPTY, 0);
        player.getInventory().placeItemBackInInventory(stack);
        return true;
    }

    /** Drops everything it holds at {@code pos}, as its block is broken. */
    public void drop(Level level, BlockPos pos) {
        face.drop(level, pos);
    }

    public void save(ValueOutput output) {
        inventory.serialize(output.child(KEY));
    }

    public void load(ValueInput input) {
        inventory.deserialize(input.childOrEmpty(KEY));
    }
}
