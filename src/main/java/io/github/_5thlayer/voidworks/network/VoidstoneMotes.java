// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import io.github._5thlayer.voidworks.energy.MoteResource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Voidstone's or an awake Void Well's {@code voidworks:motes}: the motes held at its position,
 * which conduction fills from the rest of its network and drains into it. A position in no network
 * holds none.
 */
public record VoidstoneMotes(ServerLevel level, BlockPos pos) implements ResourceHandler<MoteResource> {

    /** What a position in no network holds: nothing, with no room. */
    private static final ResourceHandler<MoteResource> EMPTY = new VoidNetworks(() -> 0, () -> 0).empty();

    /** @return the store it reaches now, or an empty one for none */
    public ResourceHandler<MoteResource> store() {
        return VoidstoneNetworks.of(level).store(pos).<ResourceHandler<MoteResource>>map(store -> store).orElse(EMPTY);
    }

    @Override
    public int size() {
        return store().size();
    }

    @Override
    public MoteResource getResource(int index) {
        return store().getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return store().getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, MoteResource resource) {
        return store().getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, MoteResource resource) {
        return store().isValid(index, resource);
    }

    @Override
    public int insert(int index, MoteResource resource, int amount, TransactionContext transaction) {
        return store().insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, MoteResource resource, int amount, TransactionContext transaction) {
        return store().extract(index, resource, amount, transaction);
    }

    /** @return the {@code voidworks:motes} of the Voidstone or Void Well at {@code pos} */
    public static ResourceHandler<MoteResource> at(ServerLevel level, BlockPos pos) {
        return new VoidstoneMotes(level, pos.immutable());
    }
}
