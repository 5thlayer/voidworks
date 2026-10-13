// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Every Family's ladder, indexed by item: the next Rung an item climbs to, as plain logic with no
 * Minecraft types. An item a Rung accepts upgrades to the Rung above it; one on a top Rung, or in no
 * Family, has none. A top Rung makes no claim, so another Family may carry its items on.
 *
 * <p>An item that several Rungs accept, in one Family or several, upgrades only when every one of
 * them gives the same next step, in result, count, cost and chance. When they differ, it has no
 * upgrade, and it is a {@linkplain #disagreements() disagreement}, which the loader warns of.
 *
 * @param <I> what names an item: the game's item, or a plain id in a test
 */
public final class Ladders<I> {

    /** A Rung with its tag resolved: the items it accepts, and the upgrade into it, absent on the bottom Rung. */
    public record Rung<I>(Set<I> items, Optional<Upgrade<I>> upgrade) {

        /** @return every item the Rung accepts: its items and its own result, so an upgraded item climbs on */
        Set<I> accepted() {
            var accepted = new LinkedHashSet<>(items);
            upgrade.ifPresent(into -> accepted.add(into.result()));
            return accepted;
        }
    }

    private static final Ladders<?> EMPTY = new Ladders<>(Map.of(), Map.of());

    private final Map<I, Upgrade<I>> next;
    private final Map<I, SortedSet<String>> disagreements;

    private Ladders(Map<I, Upgrade<I>> next, Map<I, SortedSet<String>> disagreements) {
        this.next = next;
        this.disagreements = disagreements;
    }

    /** @return the ladders of no Family: nothing upgrades */
    @SuppressWarnings("unchecked")
    public static <I> Ladders<I> empty() {
        return (Ladders<I>) EMPTY;
    }

    /**
     * Indexes {@code families}, each a ladder of Rungs, bottom first.
     *
     * @param families each Family's Rungs by its name, which a disagreement reports
     */
    public static <I> Ladders<I> of(Map<String, List<Rung<I>>> families) {
        // Every claim on each item: the Families that make it, by the upgrade they give.
        var claims = new LinkedHashMap<I, Map<Upgrade<I>, SortedSet<String>>>();
        families.forEach((family, rungs) -> {
            for (int rung = 0; rung + 1 < rungs.size(); rung++) {
                var upgrade = rungs.get(rung + 1).upgrade().orElseThrow(
                        () -> new IllegalArgumentException("a Rung above the bottom of " + family + " has no upgrade into it"));
                for (var item : rungs.get(rung).accepted()) {
                    claims.computeIfAbsent(item, unused -> new LinkedHashMap<>())
                            .computeIfAbsent(upgrade, unused -> new TreeSet<>()).add(family);
                }
            }
        });
        var next = new LinkedHashMap<I, Upgrade<I>>();
        var disagreements = new LinkedHashMap<I, SortedSet<String>>();
        claims.forEach((item, upgrades) -> {
            if (upgrades.size() == 1) {
                next.put(item, upgrades.keySet().iterator().next());
            } else {
                var named = new TreeSet<String>();
                upgrades.values().forEach(named::addAll);
                disagreements.put(item, named);
            }
        });
        // Unmodifiable, not copied: a copy loses the Families' order, which steps() keeps.
        return new Ladders<>(Collections.unmodifiableMap(next), Map.copyOf(disagreements));
    }

    /** @return the upgrade {@code item} climbs to, or empty for an item only on top Rungs, in no Family, or where its Rungs disagree */
    public Optional<Upgrade<I>> next(I item) {
        return Optional.ofNullable(next.get(item));
    }

    /**
     * @return every upgrade some item climbs to, with the items that climb to it, in the order the
     *         Families name them: a recipe viewer's steps. An item whose Rungs disagree is in none.
     */
    public Map<Upgrade<I>, Set<I>> steps() {
        var steps = new LinkedHashMap<Upgrade<I>, Set<I>>();
        next.forEach((item, upgrade) -> steps.computeIfAbsent(upgrade, unused -> new LinkedHashSet<>()).add(item));
        steps.replaceAll((upgrade, items) -> Collections.unmodifiableSet(items));
        return Collections.unmodifiableMap(steps);
    }

    /** @return each item whose Rungs disagree on its next step, with the Families that name it */
    public Map<I, SortedSet<String>> disagreements() {
        return disagreements;
    }
}
