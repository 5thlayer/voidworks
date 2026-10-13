// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Void Crucible: never Dormant and spending no motes, it upgrades what its input holds to the
 * next Rung of its Family ({@link Families#next}), one result at a time, by the Rung's count of
 * items, and never fails. A result takes the time {@link CrucibleTime} gives for the Rung's cost, the
 * Void Pressure where it stands, and the working Crucibles within {@link VoidworksConfig#crucibleRadius()}
 * blocks of it, itself included, so a wall of them upgrades no faster than one.
 *
 * <p>What it has done belongs to what its input holds: emptied, or given another item, it starts
 * again. It waits, done, while its output cannot take the result. It progresses only on its own
 * server ticks, so only while its chunk is loaded, and keeps its progress across a save. The result
 * it works on, or waits with, done, floats above it ({@link #floating}), the one thing its watchers
 * are sent.
 *
 * <p>It holds what every Mutation block does ({@link MutationItems}), with one output: its item face
 * takes into the input only items with an upgrade.
 */
public final class VoidCrucibleBlockEntity extends BlockEntity implements MutationItems.Holder {

    /** The slot of what waits to upgrade. */
    public static final int INPUT = MutationItems.INPUT;
    /** The slot of what upgraded. */
    public static final int OUTPUT = MutationItems.FIRST_OUTPUT;

    /** Where its result floats, in block units above its base: the height it is drawn and glimmers at. */
    public static final double FLOAT_HEIGHT = 1.3;

    /** How near done counts as done, for the sum of a result's per-tick shares. */
    private static final double DONE = 1 - 1e-9;

    private static final String PROGRESS_KEY = "progress";
    private static final String WORKING_ON_KEY = "working_on";
    private static final String FLOATING_KEY = "floating";
    private static final String STILL_KEY = "still";

    private final MutationItems items = new MutationItems(1, Families::hasUpgrade, this::setChanged);
    private final ItemStacksResourceHandler inventory = items.inventory();
    /** The item its progress belongs to, empty for none. */
    private ItemResource workingOn = ItemResource.EMPTY;
    /** How much of one result is done, from 0 to 1. */
    private double progress;
    /** The result it works on, or waits with, done, for its watchers to see floating above it; empty for none. */
    private ItemStack floating = ItemStack.EMPTY;
    /** Whether the floating result holds still: it is done and waits on its output. */
    private boolean still;
    /** A watcher's own, never sent: the game time it saw the result go still, so it holds the way it last faced. */
    private long wentStillAt;

    public VoidCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.VOID_CRUCIBLE_ENTITY.get(), pos, state);
    }

    @Override
    public MutationItems items() {
        return items;
    }

    /** @return what upgraded, empty for nothing */
    public ItemStack output() {
        return items.held(OUTPUT);
    }

    /** @return the result it works on, or waits with, done, which floats above it; empty for none */
    public ItemStack floating() {
        return floating;
    }

    /** @return whether the floating result holds still, as it does while it is done and waits on its output */
    public boolean floatingStill() {
        return still;
    }

    /**
     * @return whether particles glimmer about its floating result: while it works on it, and never
     *         while it waits, done, or floats nothing; a watcher reads it from what it is sent
     */
    public boolean glimmers() {
        return !floating.isEmpty() && !still;
    }

    /**
     * @return on a watcher, for drawing, the game time it saw the floating result go still, or the
     *         chunk arrive with it still; 0 on the server
     */
    public long wentStillAt() {
        return wentStillAt;
    }

    /** @return how much of one result is done, from 0 to 1 */
    public double progress() {
        return progress;
    }

    /** @return the upgrade what its input holds climbs to, or empty for an empty input or an item with none */
    public Optional<Upgrade<Item>> next() {
        return Families.next(inventory.getResource(INPUT));
    }

    public void serverTick(ServerLevel level) {
        var held = inventory.getResource(INPUT);
        if (!held.equals(workingOn)) {
            workingOn = held;
            progress = 0;
            setChanged();
        }
        if (working()) {
            if (progress < DONE) {
                progress = Math.min(1, progress + 1 / ticks(level, next().orElseThrow()));
                setChanged();
            }
            if (progress >= DONE) {
                finish(level, next().orElseThrow());
            }
        }
        showFloating(level);
    }

    /** Sends its watchers the result it works on, or waits with, done, and whether it holds still, when either changes. */
    private void showFloating(ServerLevel level) {
        var shown = showsResult() ? next().map(upgrade -> new ItemStack(upgrade.result())).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        boolean holdsStill = !shown.isEmpty() && stall().equals(Optional.of(Stall.OUTPUT_FULL));
        if (!ItemStack.matches(shown, floating) || holdsStill != still) {
            floating = shown;
            still = holdsStill;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Turns the Rung's count of items into the result in its output, if it can take it; otherwise it waits, done. */
    private void finish(ServerLevel level, Upgrade<Item> upgrade) {
        try (var transaction = Transaction.openRoot()) {
            if (inventory.insert(OUTPUT, ItemResource.of(upgrade.result()), 1, transaction) != 1) {
                return;
            }
            inventory.extract(INPUT, workingOn, upgrade.count(), transaction);
            transaction.commit();
        }
        progress = 0;
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** @return the ticks one result of {@code upgrade} takes it, where it stands, with the Crucibles sharing its rate now */
    private double ticks(ServerLevel level, Upgrade<Item> upgrade) {
        return new CrucibleTime(VoidworksConfig.crucibleTicks()).ticks(upgrade.cost(), VoidPressure.at(level, worldPosition), sharing(level));
    }

    /** @return the ticks left before its result is done, where it stands and with the Crucibles sharing its rate now; 0 when done or idle */
    public long ticksLeft(ServerLevel level) {
        var next = next();
        if (next.isEmpty() || !working() || progress >= DONE) {
            return 0;
        }
        // Less a hair, so a whole number of ticks left is not rounded up by the sum's error.
        return (long) Math.ceil((1 - progress) * ticks(level, next.get()) - 1e-6);
    }

    /** @return whether its result floats above it: it is upgrading, or done and waiting on its output */
    private boolean showsResult() {
        if (inventory.getResource(INPUT).isEmpty()) {
            return false;
        }
        var stall = stall();
        return stall.isEmpty() || stall.equals(Optional.of(Stall.OUTPUT_FULL));
    }

    /** @return whether it is upgrading: it holds enough of an item with an upgrade, and is not waiting, done, on its output */
    public boolean working() {
        return !inventory.getResource(INPUT).isEmpty() && stall().isEmpty();
    }

    /**
     * @return the working Crucibles within {@link VoidworksConfig#crucibleRadius()} blocks of it on
     *         every axis ({@link CrucibleTime#near}), itself included, so at least 1; those in chunks
     *         not loaded do not work
     */
    public int sharing(ServerLevel level) {
        int radius = VoidworksConfig.crucibleRadius();
        int count = 1;
        for (int chunkX = SectionPos.blockToSectionCoord(worldPosition.getX() - radius);
                chunkX <= SectionPos.blockToSectionCoord(worldPosition.getX() + radius); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(worldPosition.getZ() - radius);
                    chunkZ <= SectionPos.blockToSectionCoord(worldPosition.getZ() + radius); chunkZ++) {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (var entity : chunk.getBlockEntities().values()) {
                    if (entity != this && entity instanceof VoidCrucibleBlockEntity other && !other.isRemoved()
                            && CrucibleTime.near(other.worldPosition.subtract(worldPosition), radius) && other.working()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * @return why it is not upgrading what its input holds: an item with no upgrade, fewer than the
     *         Rung's count, or, done, an output that cannot take the result; empty when it is, or its
     *         input is empty
     */
    public Optional<Stall> stall() {
        if (inventory.getResource(INPUT).isEmpty()) {
            return Optional.empty();
        }
        var next = next();
        if (next.isEmpty()) {
            return Optional.of(Stall.NO_UPGRADE);
        }
        if (items.holdsFewer(next.get().count())) {
            return Optional.of(Stall.TOO_FEW);
        }
        // Only once done: the output takes the result or it waits, done.
        if (progress >= DONE && !items.fits(OUTPUT, ItemResource.of(next.get().result()), 1)) {
            return Optional.of(Stall.OUTPUT_FULL);
        }
        return Optional.empty();
    }

    /** What stops the Crucible upgrading what its input holds. */
    public enum Stall {
        /** What it holds climbs no Family, as after a datapack changed them. */
        NO_UPGRADE,
        /** It holds fewer than the Rung's count. */
        TOO_FEW,
        /** It is done, and its output cannot take the result. */
        OUTPUT_FULL
    }

    // -- persistence ----------------------------------------------------------------------------

    /** Only what its watchers draw: the result floating above it and whether it holds still, never saved, which it works out again each tick. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        if (!floating.isEmpty()) {
            output.store(FLOATING_KEY, ItemStack.CODEC, floating);
        }
        output.putBoolean(STILL_KEY, still);
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as the chunk arrives. */
    @Override
    public void handleUpdateTag(ValueInput input) {
        readDrawn(input);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as it changes. */
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readDrawn(input);
    }

    private void readDrawn(ValueInput input) {
        floating = input.read(FLOATING_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        boolean wasStill = still;
        still = input.getBooleanOr(STILL_KEY, false);
        if (still && !wasStill && level != null) {
            wentStillAt = level.getGameTime();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.save(output);
        output.putDouble(PROGRESS_KEY, progress);
        if (!workingOn.isEmpty()) {
            output.store(WORKING_ON_KEY, ItemResource.CODEC, workingOn);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.load(input);
        progress = input.getDoubleOr(PROGRESS_KEY, 0);
        workingOn = input.read(WORKING_ON_KEY, ItemResource.CODEC).orElse(ItemResource.EMPTY);
    }

    /** Broken, it drops what it holds, and what it had done is lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            items.drop(level, pos);
        }
    }
}
