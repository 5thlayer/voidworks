// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
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
     */
    record Well(Place place, OptionalLong target, int pressure, long capacity, SortedMap<Integer, Long> motes) {

        public Well {
            motes = Collections.unmodifiableSortedMap(new TreeMap<>(motes));
        }

        /** @return how many motes it holds, of every grade together */
        public long total() {
            return motes.values().stream().mapToLong(Long::longValue).sum();
        }

        /** @return how many more it takes */
        public long room() {
            return Math.max(0, capacity - total());
        }

        /** @return how many it is short of its target; none for a reservoir or a Well at or above it */
        public long shortfall() {
            return target.isPresent() ? Math.max(0, target.getAsLong() - total()) : 0;
        }

        /** @return how many it gives: all of a reservoir, a Well's excess over its target, none below it */
        public long excess() {
            return target.isPresent() ? Math.max(0, total() - target.getAsLong()) : total();
        }

        /** @return how full it is, from 0 to 1, by which its network pressure rises */
        public double fill() {
            return (double) total() / capacity;
        }

        /** @return how many motes it gives that are worth something at {@code to}: of its excess, no more than it holds of such grades */
        public long deliverableTo(Well to) {
            long worth = motes.entrySet().stream().filter(held -> to.worth(held.getKey())).mapToLong(Map.Entry::getValue).sum();
            return Math.min(excess(), worth);
        }

        /** @return whether a mote of {@code grade} is worth something here */
        public boolean worth(int grade) {
            return grade > pressure;
        }
    }
}
