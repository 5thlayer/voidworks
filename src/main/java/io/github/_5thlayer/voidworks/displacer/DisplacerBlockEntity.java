// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.inventory.ItemFace;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What every Void Displacer shares: it harvests the positions touching it or the Voidstone it has
 * left, nearest first, each harvest taking one fed block, turning the position into Voidstone and
 * putting the fed block's motes, at one grade above the Void Pressure there, into the network its
 * Voidstone forms, with a sound, and anything solid it displaced crumbling. It holds no motes
 * itself. What it harvests, and what its output and tank get for it, is its dimension's
 * ({@link OverworldDisplacerBlockEntity}, {@link EndDisplacerBlockEntity},
 * {@link NetherDisplacerBlockEntity}).
 *
 * <p>It works only where the harvest kind is its own: anywhere else it refuses on its first tick,
 * popping off as its item with what it holds, so it never stands there unbreakable. A full output
 * or tank, or a network that cannot take the harvest's motes, stalls it; a player takes back the fed blocks
 * with an empty hand, as nothing else can extract them. When its Zone is spent, or it can reach
 * nothing more, it waits for its output to empty, then becomes Voidstone itself and its network
 * keeps the motes; one that could reach nothing before its first harvest pops off instead, as
 * outside its dimension, even before it is {@linkplain #ready ready}.
 */
public abstract class DisplacerBlockEntity extends BlockEntity {

    /** The fed block's slot; the output's follow it. */
    public static final int INPUT = 0;
    /** How many slots the output has. */
    public static final int OUTPUT_SLOTS = 9;

    private static final String ITEMS_KEY = "items";
    private static final String ZONE_KEY = "zone";
    private static final String GROWN_KEY = "grown";

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(1 + OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };
    private final ResourceHandler<ItemResource> output = RangedResourceHandler.of(inventory, 1, 1 + OUTPUT_SLOTS);
    private final ItemFace face = new ItemFace(inventory, INPUT, resource -> Density.of(resource) > 0);

    /** The positions it has harvested, each Voidstone now, in the order it harvested them. */
    private final Set<BlockPos> grown = new LinkedHashSet<>();
    /** How many positions it harvests in its life; 0 until its first tick sets it from the config. */
    private int zone;
    private int sinceHarvest;

    protected DisplacerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The fed block's slot, then the output's, for the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    /** The item capability, the same on every side: the input takes only blocks of a Density above 0, and only the output gives. */
    public ResourceHandler<ItemResource> itemFace() {
        return face;
    }

    /** Sets how many positions it harvests in its life, counting those it has harvested; a Zone upgrade raises it. */
    public void setZone(int zone) {
        this.zone = Math.max(1, zone);
        setChanged();
    }

    /** @return how many more positions it harvests before its Zone is spent */
    public int harvestsLeft() {
        return Math.max(0, zoneOrConfig() - grown.size());
    }

    /** @return how many positions it harvests in its life: the config's until its first tick sets its own */
    private int zoneOrConfig() {
        return zone == 0 ? VoidworksConfig.zone() : zone;
    }

    /** @return what it holds to feed in, empty for nothing */
    public ItemStack fed() {
        return inventory.getResource(INPUT).toStack(inventory.getAmountAsInt(INPUT));
    }

    /** @return the positions it has harvested, in order */
    public List<BlockPos> grown() {
        return List.copyOf(grown);
    }

    // -- what a dimension's Displacer says --------------------------------------------------------

    /** @return the harvest kind it works in, and only there */
    protected abstract HarvestKind kind();

    /** @return whether it harvests a position holding {@code state} */
    protected abstract boolean harvestable(BlockState state);

    /**
     * @return whether it may reach {@code pos} at all, whatever is there; anywhere touching it or its
     *         Voidstone unless a Displacer says otherwise
     */
    protected boolean reaches(BlockPos pos) {
        return true;
    }

    /** @return what its output gets for displacing {@code harvested} with a fed block of {@code density}; empty for nothing */
    protected abstract ItemStack displaced(BlockState harvested, int density);

    /**
     * Puts what displacing {@code harvested} gives its tank there, as part of {@code transaction}.
     *
     * @return whether the tank took it all: always, for a Displacer with no tank
     */
    protected boolean toTank(BlockState harvested, TransactionContext transaction) {
        return true;
    }

    /**
     * Called each server tick where it may work and can reach something; it harvests only while
     * this is true.
     *
     * @return whether it may harvest this tick: always, unless it must be paid for first
     */
    protected boolean ready(ServerLevel level) {
        return true;
    }

    // -- the harvest ----------------------------------------------------------------------------

    public void serverTick(ServerLevel level) {
        if (VoidPressure.harvestKind(level) != kind()) {
            popOff(level);
            return;
        }
        if (zone == 0) {
            setZone(VoidworksConfig.zone());
        }
        if (grown.isEmpty() && frontierLoaded(level) && next(level).isEmpty()) {
            // Placed where it could never harvest: given back rather than lost to Voidstone, before
            // it soaks a mote of its cost.
            popOff(level);
            return;
        }
        if (!ready(level) || ++sinceHarvest < VoidworksConfig.harvestInterval()) {
            return;
        }
        sinceHarvest = 0;
        if (!frontierLoaded(level)) {
            // What lies beyond an unloaded chunk is unknown: neither harvested nor given up on.
            return;
        }
        var target = next(level);
        if (target.isPresent() && grown.size() < zone && harvest(level, target.get())) {
            target = next(level);
        }
        // A spent Displacer still holding output waits, as a full one stalls, for whatever pulls it.
        if ((target.isEmpty() || grown.size() >= zone) && outputEmpty()) {
            becomeVoidstone(level);
        }
    }

    private Optional<BlockPos> next(ServerLevel level) {
        return Frontier.next(worldPosition, grown, pos -> reaches(pos) && harvestable(level.getBlockState(pos)));
    }

    private boolean frontierLoaded(ServerLevel level) {
        for (var from : grownAndSelf()) {
            for (var direction : Direction.values()) {
                if (!level.isLoaded(from.relative(direction))) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<BlockPos> grownAndSelf() {
        var all = new ArrayList<BlockPos>(grown);
        all.add(worldPosition);
        return all;
    }

    /** Harvests {@code target}, or does nothing and says so when it is stalled or has nothing to feed in. */
    private boolean harvest(ServerLevel level, BlockPos target) {
        var fed = inventory.getResource(INPUT);
        int density = Density.of(fed);
        if (density == 0 || stallBeforeOutput(level, target, density).isPresent()) {
            return false;
        }
        int grade = VoidPressure.at(level, target) + 1;
        int motes = VoidworksConfig.motes(density);
        var networks = VoidstoneNetworks.of(level);
        var harvested = level.getBlockState(target);
        try (var transaction = Transaction.openRoot()) {
            if (inventory.extract(INPUT, fed, 1, transaction) != 1 || deliver(harvested, density, transaction).isPresent()) {
                return false;
            }
            transaction.commit();
        }
        level.setBlock(target, VoidworksBlocks.VOIDSTONE.get().in(level), Block.UPDATE_ALL);
        if (!harvested.isAir() && harvested.getFluidState().isEmpty()) {
            // What solid was displaced crumbles as if broken, without its breaking sound.
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, harvested),
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
        }
        grown.add(target.immutable());
        try (var transaction = Transaction.openRoot()) {
            // The room was asked for above, so the network takes them all.
            networks.deposit(target, MoteResource.of(grade), motes, transaction);
            transaction.commit();
        }
        MoteFlight.toBlock(level, target.getCenter(), worldPosition, grade, motes);
        // Vanilla's sound of something displaced through the void, until Voidworks has its own.
        level.playSound(null, target, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        setChanged();
        return true;
    }

    /**
     * @return why it cannot harvest the next position it would, though fed: a Void Pressure there
     *         too high to grade, a network with no room for the harvest's motes, or a full output or tank;
     *         or, spent, the output it waits on before it becomes Voidstone; empty when it can, or has
     *         nothing fed and nothing left to wait on. A Dormant one's payment is its
     *         {@link io.github._5thlayer.voidworks.dormant.Dormancy}'s.
     */
    public Optional<Stall> stall(ServerLevel level) {
        if (spent(level)) {
            return outputEmpty() ? Optional.empty() : Optional.of(Stall.OUTPUT_UNEMPTIED);
        }
        int density = Density.of(inventory.getResource(INPUT));
        if (density == 0) {
            return Optional.empty();
        }
        return next(level).flatMap(target -> stallBeforeOutput(level, target, density).or(() -> {
            // Tried and rolled back: the output and tank take what is displaced or they do not.
            try (var transaction = Transaction.openRoot()) {
                return deliver(level.getBlockState(target), density, transaction);
            }
        }));
    }

    /** What stalls a harvest of {@code target} before its output is tried, which {@link #harvest} tries for real. */
    private Optional<Stall> stallBeforeOutput(ServerLevel level, BlockPos target, int density) {
        if (!VoidEnergy.isGrade(VoidPressure.at(level, target) + 1)) {
            return Optional.of(Stall.PRESSURE_TOO_HIGH);
        }
        if (VoidstoneNetworks.of(level).roomIfJoined(target) < VoidworksConfig.motes(density)) {
            return Optional.of(Stall.NETWORK_FULL);
        }
        return Optional.empty();
    }

    /** What stops a fed Displacer harvesting its next position, or a spent one becoming Voidstone. */
    public enum Stall {
        /** The Void Pressure there is the highest, so a harvest would have no grade. */
        PRESSURE_TOO_HIGH,
        /** The network its harvest would join cannot take the harvest's motes. */
        NETWORK_FULL,
        /** Its output cannot take what the harvest displaces. */
        OUTPUT_FULL,
        /** Its tank cannot take what the harvest displaces. */
        TANK_FULL,
        /** Spent, it waits for its output to be emptied before it becomes Voidstone. */
        OUTPUT_UNEMPTIED
    }

    /**
     * Puts what displacing {@code harvested} with a fed block of {@code density} gives into its
     * output, then its tank, as part of {@code transaction}.
     *
     * @return which of them was full, or empty when both took it all
     */
    private Optional<Stall> deliver(BlockState harvested, int density, TransactionContext transaction) {
        if (!toOutput(displaced(harvested, density), transaction)) {
            return Optional.of(Stall.OUTPUT_FULL);
        }
        return toTank(harvested, transaction) ? Optional.empty() : Optional.of(Stall.TANK_FULL);
    }

    private boolean toOutput(ItemStack result, TransactionContext transaction) {
        if (result.isEmpty()) {
            return true;
        }
        int count = result.getCount();
        return ResourceHandlerUtil.insertStacking(output, ItemResource.of(result), count, transaction) == count;
    }

    /** @return whether it becomes Voidstone once its output is empty: its Zone spent or nothing left in reach, its frontier loaded */
    private boolean spent(ServerLevel level) {
        return !grown.isEmpty() && frontierLoaded(level) && (grown.size() >= zoneOrConfig() || next(level).isEmpty());
    }

    private boolean outputEmpty() {
        for (int slot = 0; slot < output.size(); slot++) {
            if (!output.getResource(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Its Zone spent or nothing left in reach, and its output empty: it becomes Voidstone, joining its network; {@link #preRemoveSideEffects} drops its fed blocks. */
    private void becomeVoidstone(ServerLevel level) {
        level.setBlock(worldPosition, VoidworksBlocks.VOIDSTONE.get().in(level), Block.UPDATE_ALL);
    }

    /** Where it cannot work, or never could harvest: it goes back to its item, and {@link #preRemoveSideEffects} drops what it held. */
    private void popOff(ServerLevel level) {
        Block.popResource(level, worldPosition, new ItemStack(getBlockState().getBlock()));
        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * Gives {@code player} the fed blocks back, the one way out of its input: a Displacer fed blocks
     * whose harvest its network cannot hold would otherwise stall for good.
     *
     * @return whether there was anything to give
     */
    public boolean giveBackFed(Player player) {
        var fed = inventory.getResource(INPUT);
        int count = inventory.getAmountAsInt(INPUT);
        if (fed.isEmpty() || count == 0) {
            return false;
        }
        inventory.set(INPUT, ItemResource.EMPTY, 0);
        player.getInventory().placeItemBackInInventory(fed.toStack(count));
        return true;
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child(ITEMS_KEY));
        output.putInt(ZONE_KEY, zone);
        output.store(GROWN_KEY, BlockPos.CODEC.listOf(), List.copyOf(grown));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
        zone = input.getIntOr(ZONE_KEY, 0);
        grown.clear();
        input.read(GROWN_KEY, BlockPos.CODEC.listOf()).ifPresent(grown::addAll);
    }

    /** Going, by turning to Voidstone, a creative player or a command, it drops what it holds. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) {
            return;
        }
        face.drop(level, pos);
    }
}
