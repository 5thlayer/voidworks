// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntSupplier;

import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * One Voidstone network: the Voidstone that touches, and the single pool of motes they hold
 * together. Its capacity is a fixed amount per Voidstone, shared by every grade; conduction, and
 * with it network pressure, comes with the Void Well.
 *
 * <p>It is a mote store with one slot per grade, from {@link VoidEnergy#MIN_GRADE} up, so motes of
 * a grade pool and motes of different grades never do, and a spend reaches it like any other store.
 * {@link VoidNetworks} makes and merges networks; nothing else adds or removes their Voidstone.
 */
public final class MoteNetwork extends SnapshotJournal<long[]> implements ResourceHandler<MoteResource> {

    private static final int SLOTS = VoidEnergy.MAX_GRADE - VoidEnergy.MIN_GRADE + 1;

    private final IntSupplier perVoidstone;
    private final Runnable changed;
    final Set<BlockPos> voidstone = new HashSet<>();
    /** Motes by slot, a slot per grade from the lowest. */
    long[] motes = new long[SLOTS];

    MoteNetwork(IntSupplier perVoidstone, Runnable changed) {
        this.perVoidstone = perVoidstone;
        this.changed = changed;
    }

    /** @return how many motes the network holds, of every grade together, at most */
    public long capacity() {
        return (long) voidstone.size() * perVoidstone.getAsInt();
    }

    /** @return how many motes it holds, of every grade together */
    public long total() {
        long total = 0;
        for (long count : motes) {
            total += count;
        }
        return total;
    }

    /** @return how many more motes it takes, of any grade */
    public long room() {
        return Math.max(0, capacity() - total());
    }

    /** @return how many Voidstone it is */
    public int voidstoneCount() {
        return voidstone.size();
    }

    /** Takes in {@code other}'s Voidstone and motes; the caller stops using {@code other}. */
    void absorb(MoteNetwork other) {
        voidstone.addAll(other.voidstone);
        for (int slot = 0; slot < SLOTS; slot++) {
            motes[slot] += other.motes[slot];
        }
    }

    /** Loses the motes beyond its capacity, the lowest grades first, so the ones worth most are kept. */
    void trim() {
        long excess = total() - capacity();
        for (int slot = 0; slot < SLOTS && excess > 0; slot++) {
            long lost = Math.min(excess, motes[slot]);
            motes[slot] -= lost;
            excess -= lost;
        }
    }

    @Override
    public int size() {
        return SLOTS;
    }

    @Override
    public MoteResource getResource(int index) {
        return motes[index] > 0 ? MoteResource.of(grade(index)) : MoteResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return motes[index];
    }

    @Override
    public long getCapacityAsLong(int index, MoteResource resource) {
        if (!resource.isEmpty() && !isValid(index, resource)) {
            return 0;
        }
        return motes[index] + room();
    }

    @Override
    public boolean isValid(int index, MoteResource resource) {
        return !resource.isEmpty() && resource.grade() == grade(index);
    }

    @Override
    public int insert(int index, MoteResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) {
            return 0;
        }
        int inserted = (int) Math.min(amount, room());
        if (inserted > 0) {
            updateSnapshots(transaction);
            motes[index] += inserted;
        }
        return inserted;
    }

    @Override
    public int extract(int index, MoteResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) {
            return 0;
        }
        int extracted = (int) Math.min(amount, motes[index]);
        if (extracted > 0) {
            updateSnapshots(transaction);
            motes[index] -= extracted;
        }
        return extracted;
    }

    @Override
    protected long[] createSnapshot() {
        return motes.clone();
    }

    @Override
    protected void revertToSnapshot(long[] snapshot) {
        motes = snapshot;
    }

    @Override
    protected void onRootCommit(long[] originalState) {
        changed.run();
    }

    static int grade(int slot) {
        return slot + VoidEnergy.MIN_GRADE;
    }

    static int slot(int grade) {
        return VoidEnergy.requireGrade(grade) - VoidEnergy.MIN_GRADE;
    }
}
