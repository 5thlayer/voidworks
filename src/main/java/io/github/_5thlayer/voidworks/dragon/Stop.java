// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;

/**
 * An owner's Wells on one network, one stop for the owner's dragons (ADR 0006): a dragon never
 * carries between them, and each is a place to stop, load, unload and heal. It holds what its network
 * holds; what its Line's dragons find there to carry, and room for, its {@link Cargo} says.
 *
 * @param wells the owner's Wells on the network
 */
record Stop(List<Wells.Well> wells) {

    /** @return the places of every Well on its network, whoever's */
    Set<Place> network() {
        return wells.getFirst().network();
    }

    /** @return whether the owner's Well at {@code place} is one of its own */
    boolean has(Place place) {
        return wells.stream().anyMatch(well -> well.place().equals(place));
    }

    /**
     * @return the place of its Well a dive from {@code from} reaches soonest, {@code from} itself if it
     *         is one, and of those the dive takes as long to reach, the nearest
     */
    Place nearest(Place from, Dive dive) {
        return wells.stream().map(Wells.Well::place)
                .min(dive.soonestFrom(from))
                .orElseThrow();
    }

    /** @return the motes its network holds, grade to count, which heal a dragon of any Line */
    SortedMap<Integer, Long> motes() {
        return wells.getFirst().motes();
    }

    /** @return how many motes it holds, of every grade together */
    long total() {
        return motes().values().stream().mapToLong(Long::longValue).sum();
    }
}
