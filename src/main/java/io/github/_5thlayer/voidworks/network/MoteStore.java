// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The motes one position of a network holds: a Voidstone's or a Void Well's own, which a spend
 * there reaches and conduction fills and drains. Its capacity is shared by every grade.
 *
 * <p>It is a mote store with one slot per grade, from {@link VoidEnergy#MIN_GRADE} up, so motes of
 * a grade pool and motes of different grades never do.
 */
public final class MoteStore extends SnapshotJournal<long[]> implements ResourceHandler<MoteResource> {

    static final int SLOTS = VoidEnergy.MAX_GRADE - VoidEnergy.MIN_GRADE + 1;

    private final BlockPos pos;
    private final StoreKind kind;
    private final VoidNetworks networks;
    /** Motes by slot, a slot per grade from the lowest. */
    long[] motes = new long[SLOTS];

    MoteStore(BlockPos pos, StoreKind kind, VoidNetworks networks) {
        this.pos = pos;
        this.kind = kind;
        this.networks = networks;
    }

    /** @return where it stands */
    public BlockPos pos() {
        return pos;
    }

    /** @return whether it is a Voidstone's or a Void Well's */
    public StoreKind kind() {
        return kind;
    }

    /** @return how many motes it holds, of every grade together, at most */
    public long capacity() {
        return networks.capacity(kind);
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

    /** @return its network pressure for the grade in {@code slot}, were it to hold {@code count} of them */
    double pressure(int slot, long count) {
        return kind.pressure((double) count / capacity());
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
        networks.changed();
    }

    static int grade(int slot) {
        return slot + VoidEnergy.MIN_GRADE;
    }

    static int slot(int grade) {
        return VoidEnergy.requireGrade(grade) - VoidEnergy.MIN_GRADE;
    }
}
