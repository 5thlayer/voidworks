// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import io.github._5thlayer.voidworks.energy.MoteResource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Voidstone's {@code voidworks:motes}: its network's motes, whichever network it is in now, so a
 * holder that keeps it still reaches the motes after a merge. A Voidstone in no network holds none.
 */
public record VoidstoneMotes(ServerLevel level, BlockPos pos) implements ResourceHandler<MoteResource> {

    /** What a Voidstone in no network holds: nothing, with no room. */
    private static final ResourceHandler<MoteResource> EMPTY = new MoteNetwork(() -> 0, () -> {
    });

    private ResourceHandler<MoteResource> network() {
        return VoidstoneNetworks.of(level).at(pos).<ResourceHandler<MoteResource>>map(network -> network)
                .orElse(EMPTY);
    }

    @Override
    public int size() {
        return network().size();
    }

    @Override
    public MoteResource getResource(int index) {
        return network().getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return network().getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, MoteResource resource) {
        return network().getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, MoteResource resource) {
        return network().isValid(index, resource);
    }

    @Override
    public int insert(int index, MoteResource resource, int amount, TransactionContext transaction) {
        return network().insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, MoteResource resource, int amount, TransactionContext transaction) {
        return network().extract(index, resource, amount, transaction);
    }

    /** @return the {@code voidworks:motes} of the Voidstone at {@code pos} */
    public static ResourceHandler<MoteResource> at(ServerLevel level, BlockPos pos) {
        return new VoidstoneMotes(level, pos.immutable());
    }
}
