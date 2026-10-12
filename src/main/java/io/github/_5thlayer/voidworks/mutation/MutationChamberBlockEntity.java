// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.TouchingMotes;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Mutation Chamber: placed Dormant, it soaks its cost, and then, once every
 * {@link VoidworksConfig#mutationInterval()} ticks, upgrades what its input holds to the next Rung of
 * its Family ({@link Families#next}). Each upgrade takes the Rung's count of items for one result and
 * spends the Rung's cost from one touching mote store, a Voidstone network or any block providing
 * {@code voidworks:motes}, by the spend rule at the Chamber's block, then rolls the Rung's chance: on
 * a success the items become the result in its output, and on a failure they stay and the motes are
 * spent. It waits, spending nothing, while its input holds fewer than the count, its output cannot
 * take the result, or no touching store can afford the cost alone.
 *
 * <p>Its item face, the same on every side, takes into the input only items with an upgrade, and
 * gives only from the output, so hoppers and belts feed and empty it.
 */
public final class MutationChamberBlockEntity extends BlockEntity implements Dormancy.Holder {

    /** The slot of what waits to upgrade. */
    public static final int INPUT = 0;
    /** The slot of what upgraded. */
    public static final int OUTPUT = 1;

    private static final String ITEMS_KEY = "items";

    private final Dormancy dormancy = new Dormancy(this);
    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(2) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };
    private final ResourceHandler<ItemResource> face = new Face();
    private int sinceUpgrade;

    public MutationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.MUTATION_CHAMBER_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    /** Its input, then its output, for the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    /** The item capability, the same on every side: the input takes only items with an upgrade, and only the output gives. */
    public ResourceHandler<ItemResource> itemFace() {
        return face;
    }

    /** @return what waits to upgrade, empty for nothing */
    public ItemStack input() {
        return held(INPUT);
    }

    /** @return what upgraded, empty for nothing */
    public ItemStack output() {
        return held(OUTPUT);
    }

    private ItemStack held(int slot) {
        return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
    }

    /** @return whether an item of {@code resource} climbs a Family, so the input takes it */
    static boolean hasUpgrade(ItemResource resource) {
        return next(resource).isPresent();
    }

    /** @return the upgrade an item of {@code resource} climbs to, or empty for nothing or an item with none */
    private static Optional<Upgrade<Item>> next(ItemResource resource) {
        return resource.isEmpty() ? Optional.empty() : Families.next(resource.toStack(1));
    }

    public void serverTick(ServerLevel level) {
        if (!dormancy.soak(level) || ++sinceUpgrade < VoidworksConfig.mutationInterval()) {
            return;
        }
        sinceUpgrade = 0;
        upgrade(level);
    }

    /** Upgrades what the input holds once, or does nothing when it is stalled or holds nothing. */
    private void upgrade(ServerLevel level) {
        var held = inventory.getResource(INPUT);
        var next = next(held);
        if (next.isEmpty() || stallBeforePaying(next).isPresent()) {
            return;
        }
        var upgrade = next.get();
        if (!pay(level, upgrade.cost())) {
            return;
        }
        if (level.getRandom().nextDouble() >= upgrade.chance()) {
            level.playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.5F, 0.6F);
            return;
        }
        try (var transaction = Transaction.openRoot()) {
            inventory.extract(INPUT, held, upgrade.count(), transaction);
            inventory.insert(OUTPUT, ItemResource.of(upgrade.result()), 1, transaction);
            transaction.commit();
        }
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** @return the upgrade what its input holds climbs to, or empty for an empty input or an item with none */
    public Optional<Upgrade<Item>> next() {
        return next(inventory.getResource(INPUT));
    }

    /**
     * @return why it cannot upgrade what its input holds: an item with no upgrade, fewer than the
     *         Rung's count, an output that cannot take the result, no mote store touching it, or none
     *         that can afford the cost alone; empty when it can, or its input is empty. While it
     *         is Dormant, only what stalls its items: its stores pay its {@link Dormancy} first.
     */
    public Optional<Stall> stall(ServerLevel level) {
        if (inventory.getResource(INPUT).isEmpty()) {
            return Optional.empty();
        }
        var next = next();
        var stall = stallBeforePaying(next);
        long cost = next.map(Upgrade::cost).orElse(0L);
        if (stall.isPresent() || !dormancy.awake() || cost == 0) {
            return stall;
        }
        return TouchingMotes.bestWorth(level, worldPosition)
                .map(best -> best < cost ? Optional.of(Stall.CANNOT_AFFORD) : Optional.<Stall>empty())
                .orElse(Optional.of(Stall.NO_MOTE_STORE));
    }

    /** What stalls an upgrade before any mote store is asked, which {@link #upgrade} asks for real. */
    private Optional<Stall> stallBeforePaying(Optional<Upgrade<Item>> next) {
        if (next.isEmpty()) {
            return Optional.of(Stall.NO_UPGRADE);
        }
        if (inventory.getAmountAsInt(INPUT) < next.get().count()) {
            return Optional.of(Stall.TOO_FEW);
        }
        try (var transaction = Transaction.openRoot()) {
            // Tried and rolled back: the output takes the result or the upgrade waits.
            if (inventory.insert(OUTPUT, ItemResource.of(next.get().result()), 1, transaction) != 1) {
                return Optional.of(Stall.OUTPUT_FULL);
            }
        }
        return Optional.empty();
    }

    /** What stops the Chamber upgrading what its input holds. */
    public enum Stall {
        /** What it holds climbs no Family, as after a datapack changed them. */
        NO_UPGRADE,
        /** It holds fewer than the Rung's count. */
        TOO_FEW,
        /** Its output cannot take the result. */
        OUTPUT_FULL,
        /** No mote store touches it to pay the cost. */
        NO_MOTE_STORE,
        /** No mote store touching it is worth the cost alone, where it stands. */
        CANNOT_AFFORD
    }

    /**
     * Spends {@code cost} void energy from the first touching mote store that can afford it alone,
     * by the spend rule at the Chamber's block, its motes flying in as butterflies.
     *
     * @return whether it was paid
     */
    private boolean pay(ServerLevel level, long cost) {
        if (cost == 0) {
            return true;
        }
        for (var store : TouchingMotes.at(level, worldPosition)) {
            var spend = VoidSpender.spend(store.motes(), level, worldPosition, cost);
            if (spend.isPresent()) {
                spend.get().motes().forEach((grade, count) -> MoteFlight.toBlock(level, store.pos().getCenter(), worldPosition, grade, count));
                return true;
            }
        }
        return false;
    }

    /**
     * Puts what {@code stack} holds into the input, as much as fits, as a player's hand does.
     *
     * @return how many it took: none for an item with no upgrade, or an input holding another
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
     * Gives {@code player} what upgraded, or, with nothing upgraded, takes back what waits to.
     *
     * @return whether there was anything to give
     */
    public boolean giveTo(Player player) {
        for (int slot : new int[] {OUTPUT, INPUT}) {
            var stack = held(slot);
            if (!stack.isEmpty()) {
                inventory.set(slot, ItemResource.EMPTY, 0);
                player.getInventory().placeItemBackInInventory(stack);
                return true;
            }
        }
        return false;
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        dormancy.save(output);
        inventory.serialize(output.child(ITEMS_KEY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dormancy.load(input);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
    }

    /** Broken, it drops what it holds; a payment toward its Dormant cost is lost with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = held(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    /** The item capability: inserts go only to the input, and only of items with an upgrade; extracts come only from the output. */
    private final class Face implements ResourceHandler<ItemResource> {

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
            return index == INPUT && hasUpgrade(resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isValid(index, resource) ? inventory.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index == OUTPUT ? inventory.extract(index, resource, amount, transaction) : 0;
        }
    }
}
