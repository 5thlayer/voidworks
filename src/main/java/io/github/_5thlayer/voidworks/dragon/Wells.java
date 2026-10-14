// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The Void Wells as a Void Dragon's {@link Routing} sees them, whether or not their chunks are
 * loaded: what each holds, and moving motes in and out of it. A Well is the face of its network (ADR
 * 0006): what it holds and how much it holds at most are its core's and every Voidstone's of its
 * network together. {@code DragonRouting} reads them from the server's saved data; a test fakes them.
 */
public interface Wells {

    /** @return {@code owner}'s Wells that are whole and awake, the only ones a dragon stops at */
    List<Well> of(UUID owner);

    /** Takes up to {@code count} motes of {@code grade} out of the Well at {@code place}, its core's first. @return how many it took */
    long take(Place place, int grade, long count);

    /**
     * Puts up to {@code count} motes of {@code grade} into the Well at {@code place}: into its core, and
     * once that is full into its network's Voidstone nearest it. @return how many went in
     */
    long put(Place place, int grade, long count);

    /**
     * A Void Well as it is now.
     *
     * @param target how many motes it asks to keep, over every grade, a share of its {@code capacity}; empty for a reservoir
     * @param pressure the Void Pressure where it stands, which a mote's grade must exceed to be worth something there
     * @param capacity how many motes its network holds, of every grade together, at most
     * @param motes what its network holds, grade to count
     * @param network the places of every Well on its network, whoever's, its own among them
     */
    record Well(Place place, OptionalLong target, int pressure, long capacity, SortedMap<Integer, Long> motes, Set<Place> network) {

        public Well {
            motes = Collections.unmodifiableSortedMap(new TreeMap<>(motes));
            network = Set.copyOf(network);
        }
    }
}
