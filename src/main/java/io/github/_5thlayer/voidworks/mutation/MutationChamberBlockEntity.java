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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Mutation Chamber: placed Dormant, it soaks its cost, and then, once every
 * {@link VoidworksConfig#mutationInterval()} ticks, upgrades what its input holds to the next Rung of
 * its Family ({@link Families#next}). Each upgrade takes the Rung's count of items for one result and
 * spends the Rung's cost from the mote stores touching it together ({@link TouchingMotes}), Voidstone
 * networks or any block providing {@code voidworks:motes}, by the spend rule at the Chamber's block,
 * then rolls the Rung's chance: on a success the items become the result in its output, and on a
 * failure they stay and the motes are spent. It waits, spending nothing, while its input holds fewer
 * than the count, its output cannot take the result, or the stores touching it cannot afford the cost.
 *
 * <p>It holds what every Mutation block does ({@link MutationItems}), with one output: its item face
 * takes into the input only items with an upgrade.
 */
public final class MutationChamberBlockEntity extends BlockEntity implements Dormancy.Holder, MutationItems.Holder {

    /** The slot of what waits to upgrade. */
    public static final int INPUT = MutationItems.INPUT;
    /** The slot of what upgraded. */
    public static final int OUTPUT = MutationItems.FIRST_OUTPUT;

    private final Dormancy dormancy = new Dormancy(this);
    private final MutationItems items = new MutationItems(1, Families::hasUpgrade, this::setChanged);
    private final ItemStacksResourceHandler inventory = items.inventory();
    private int sinceUpgrade;

    public MutationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.MUTATION_CHAMBER_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    @Override
    public MutationItems items() {
        return items;
    }

    /** @return what upgraded, empty for nothing */
    public ItemStack output() {
        return items.held(OUTPUT);
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
        var next = Families.next(held);
        if (next.isEmpty() || stallBeforePaying(next).isPresent()) {
            return;
        }
        var upgrade = next.get();
        if (!TouchingMotes.spend(level, worldPosition, upgrade.cost())) {
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
        return Families.next(inventory.getResource(INPUT));
    }

    /**
     * @return why it cannot upgrade what its input holds: an item with no upgrade, fewer than the
     *         Rung's count, an output that cannot take the result, no mote store touching it, or none
     *         that can afford the cost together; empty when it can, or its input is empty. While it
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
        return TouchingMotes.totalWorth(level, worldPosition)
                .map(worth -> worth < cost ? Optional.of(Stall.CANNOT_AFFORD) : Optional.<Stall>empty())
                .orElse(Optional.of(Stall.NO_MOTE_STORE));
    }

    /** What stalls an upgrade before any mote store is asked, which {@link #upgrade} asks for real. */
    private Optional<Stall> stallBeforePaying(Optional<Upgrade<Item>> next) {
        if (next.isEmpty()) {
            return Optional.of(Stall.NO_UPGRADE);
        }
        if (items.holdsFewer(next.get().count())) {
            return Optional.of(Stall.TOO_FEW);
        }
        if (!items.fits(OUTPUT, ItemResource.of(next.get().result()), 1)) {
            return Optional.of(Stall.OUTPUT_FULL);
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
        /** The mote stores touching it are not worth the cost together, where it stands. */
        CANNOT_AFFORD
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        dormancy.save(output);
        items.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dormancy.load(input);
        items.load(input);
    }

    /** Broken, it drops what it holds; a payment toward its Dormant cost is lost with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            items.drop(level, pos);
        }
    }
}
