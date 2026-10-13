// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One network: the Voidstone, and any Void Well, that touch face to face, each holding its own
 * motes ({@link MoteStore}). Motes conduct between them ({@link VoidNetworks#conduct}); what a
 * network holds is the sum of what they hold.
 *
 * <p>{@link VoidNetworks} makes, merges and splits networks; nothing else adds or removes their stores.
 */
public final class MoteNetwork {

    final Set<MoteStore> stores = new LinkedHashSet<>();

    MoteNetwork() {
    }

    /** @return its stores, Voidstone and Wells */
    public Collection<MoteStore> stores() {
        return Collections.unmodifiableSet(stores);
    }

    /** @return how many motes the network holds, of every grade together, at most */
    public long capacity() {
        return stores.stream().mapToLong(MoteStore::capacity).sum();
    }

    /** @return how many motes it holds, of every grade together */
    public long total() {
        return stores.stream().mapToLong(MoteStore::total).sum();
    }

    /** @return how many more motes it takes, of any grade */
    public long room() {
        return stores.stream().mapToLong(MoteStore::room).sum();
    }

    /** @return how many Voidstone and Wells it is */
    public int size() {
        return stores.size();
    }

    /** @return the motes it holds, grade to count, lowest grade first; no grade with none */
    public SortedMap<Integer, Long> countByGrade() {
        var counts = new TreeMap<Integer, Long>();
        for (var store : stores) {
            for (int slot = 0; slot < MoteStore.SLOTS; slot++) {
                if (store.motes[slot] > 0) {
                    counts.merge(MoteStore.grade(slot), store.motes[slot], Long::sum);
                }
            }
        }
        return counts;
    }
}
