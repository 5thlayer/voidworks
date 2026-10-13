// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.TouchingMotes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Unmaking Chamber: placed Dormant, it soaks its cost, and then, once every
 * {@link VoidworksConfig#mutationInterval()} ticks, breaks down what its input holds into its
 * crafting recipe's ingredients ({@link UnmakingRecipes}). Each breakdown takes the recipe's result
 * count and spends {@link Breakdown#COST_PER_ITEM} per ingredient item from the mote stores touching
 * it together ({@link TouchingMotes}), by the spend rule at its block; then each ingredient item comes
 * back alone by chance, at its yield ({@link UnmakingYield}), which rises with the void energy it has
 * spent on breakdowns in its life. Broken, it forgets that. It waits, spending nothing, while its input
 * holds fewer than the count, its outputs cannot take everything the breakdown could return, or the
 * stores touching it cannot afford the cost.
 *
 * <p>It holds what every Mutation block does ({@link MutationItems}), with nine outputs: its item face
 * takes into the input only items with a breakdown.
 */
public final class UnmakingChamberBlockEntity extends BlockEntity implements Dormancy.Holder, MutationItems.Holder {

    /** How many outputs it has, one for each slot of a crafting grid. */
    public static final int OUTPUTS = 9;

    private static final String SPENT_KEY = "spent";

    private final Dormancy dormancy = new Dormancy(this);
    private final MutationItems items = new MutationItems(OUTPUTS, UnmakingRecipes::hasBreakdown, this::setChanged);
    private final ItemStacksResourceHandler inventory = items.inventory();
    private int sinceBreakdown;
    private long spent;

    public UnmakingChamberBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.UNMAKING_CHAMBER_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    @Override
    public MutationItems items() {
        return items;
    }

    /** @return the void energy it has spent on breakdowns in its life, its Dormant cost aside */
    public long spent() {
        return spent;
    }

    /** @return the chance each ingredient item comes back from its next breakdown */
    public double currentYield() {
        return VoidworksConfig.unmakingYield().at(spent);
    }

    public void serverTick(ServerLevel level) {
        if (!dormancy.soak(level) || ++sinceBreakdown < VoidworksConfig.mutationInterval()) {
            return;
        }
        sinceBreakdown = 0;
        breakDown(level);
    }

    /** Breaks down what the input holds once, or does nothing when it is stalled or holds nothing. */
    private void breakDown(ServerLevel level) {
        var held = inventory.getResource(MutationItems.INPUT);
        var next = breakdown();
        if (next.isEmpty() || stallBeforePaying(next).isPresent()) {
            return;
        }
        var breakdown = next.get();
        if (!TouchingMotes.spend(level, worldPosition, breakdown.cost())) {
            return;
        }
        var back = UnmakingYield.roll(breakdown, currentYield(), level.getRandom()::nextDouble);
        try (var transaction = Transaction.openRoot()) {
            inventory.extract(MutationItems.INPUT, held, breakdown.count(), transaction);
            back.forEach((item, amount) -> insertIntoOutputs(ItemResource.of(item), amount, transaction));
            transaction.commit();
        }
        spent += breakdown.cost();
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, back.isEmpty() ? 0.6F : 1.0F);
    }

    /** @return the breakdown what its input holds has, or empty for an empty input or an item with none */
    public Optional<Breakdown<Item>> breakdown() {
        return UnmakingRecipes.of(inventory.getResource(MutationItems.INPUT));
    }

    /**
     * @return why it cannot break down what its input holds: an item with no breakdown, fewer than the
     *         recipe's count, outputs that cannot take everything it could return, no mote store
     *         touching it, or none that can afford the cost together; empty when it can, or its input
     *         is empty. While it is Dormant, only what stalls its items: its stores pay its
     *         {@link Dormancy} first.
     */
    public Optional<Stall> stall(ServerLevel level) {
        if (inventory.getResource(MutationItems.INPUT).isEmpty()) {
            return Optional.empty();
        }
        var next = breakdown();
        var stall = stallBeforePaying(next);
        long cost = next.map(Breakdown::cost).orElse(0L);
        if (stall.isPresent() || !dormancy.awake()) {
            return stall;
        }
        return TouchingMotes.totalWorth(level, worldPosition)
                .map(worth -> worth < cost ? Optional.of(Stall.CANNOT_AFFORD) : Optional.<Stall>empty())
                .orElse(Optional.of(Stall.NO_MOTE_STORE));
    }

    /** What stalls a breakdown before any mote store is asked, which {@link #breakDown} asks for real. */
    private Optional<Stall> stallBeforePaying(Optional<Breakdown<Item>> next) {
        if (next.isEmpty()) {
            return Optional.of(Stall.NO_BREAKDOWN);
        }
        if (items.holdsFewer(next.get().count())) {
            return Optional.of(Stall.TOO_FEW);
        }
        if (!outputsFit(next.get())) {
            return Optional.of(Stall.OUTPUT_FULL);
        }
        return Optional.empty();
    }

    /** @return whether the outputs take everything {@code breakdown} could return at once, tried and rolled back */
    private boolean outputsFit(Breakdown<Item> breakdown) {
        try (var transaction = Transaction.openRoot()) {
            return breakdown.returns().entrySet().stream()
                    .allMatch(entry -> insertIntoOutputs(ItemResource.of(entry.getKey()), entry.getValue(), transaction) == entry.getValue());
        }
    }

    /** @return how many of {@code amount} the outputs took, filling each in turn */
    private int insertIntoOutputs(ItemResource resource, int amount, Transaction transaction) {
        int inserted = 0;
        for (int slot = MutationItems.FIRST_OUTPUT; slot < inventory.size() && inserted < amount; slot++) {
            inserted += inventory.insert(slot, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    /** What stops the Chamber breaking down what its input holds. */
    public enum Stall {
        /** What it holds has no breakdown, as after a datapack changed the recipes. */
        NO_BREAKDOWN,
        /** It holds fewer than the recipe's count. */
        TOO_FEW,
        /** Its outputs cannot take everything the breakdown could return. */
        OUTPUT_FULL,
        /** No mote store touches it to pay the cost. */
        NO_MOTE_STORE,
        /** The mote stores touching it are not worth the cost together, where it stands. */
        CANNOT_AFFORD
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        dormancy.save(output);
        items.save(output);
        output.putLong(SPENT_KEY, spent);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dormancy.load(input);
        items.load(input);
        spent = input.getLongOr(SPENT_KEY, 0);
    }

    /** Broken, it drops what it holds; a payment toward its Dormant cost and what it has spent are lost with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            items.drop(level, pos);
        }
    }
}
