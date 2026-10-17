// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Wells as plain data, each its owner's, whole and awake until broken; Wells on one network share its motes. */
final class FakeWells implements Wells {

    /** One network: the places of its Wells, whoever's, and the motes they share, {@code perWell} a Well. */
    private record Network(Set<Place> wells, TreeMap<Integer, Long> motes, long perWell) {

        long capacity() {
            return perWell * wells.size();
        }
    }

    private record Entry(UUID owner, OptionalLong target, int pressure, Network network) {
    }

    private final long capacity;
    private final Map<Place, Entry> wells = new LinkedHashMap<>();
    /** Where an egg was laid, egg by egg. */
    final List<Place> laid = new ArrayList<>();
    /** The Line of each egg laid, egg by egg. */
    final List<Line> laidOf = new ArrayList<>();
    /** The Wells whose chunks are not loaded, where no egg is laid. */
    final Set<Place> unseen = new LinkedHashSet<>();

    /** @param capacity how many motes each Well adds to its network's capacity */
    FakeWells(long capacity) {
        this.capacity = capacity;
    }

    /** Adds {@code owner}'s Well at {@code place}, on a network of its own holding {@code motes}. */
    void add(UUID owner, Place place, OptionalLong target, int pressure, Map<Integer, Long> motes) {
        wells.put(place, new Entry(owner, target, pressure, new Network(new LinkedHashSet<>(Set.of(place)), new TreeMap<>(motes), capacity)));
    }

    /** Adds {@code owner}'s Well at {@code place} to the network of the Well at {@code onNetworkOf}, sharing its motes. */
    void join(UUID owner, Place place, OptionalLong target, Place onNetworkOf) {
        var on = wells.get(onNetworkOf);
        on.network().wells().add(place);
        wells.put(place, new Entry(owner, target, on.pressure(), on.network()));
    }

    void remove(Place place) {
        var removed = wells.remove(place);
        if (removed != null) {
            removed.network().wells().remove(place);
        }
    }

    /** @return what the network of the Well at {@code place} holds, of the grades it holds any of */
    Map<Integer, Long> motes(Place place) {
        var motes = new TreeMap<>(wells.get(place).network().motes());
        motes.values().removeIf(count -> count == 0);
        return motes;
    }

    @Override
    public List<Well> of(UUID owner) {
        var owned = new ArrayList<Well>();
        wells.forEach((place, entry) -> {
            if (entry.owner().equals(owner)) {
                var network = entry.network();
                owned.add(new Well(place, entry.target(), entry.pressure(), network.capacity(), network.motes(), Set.copyOf(network.wells())));
            }
        });
        return owned;
    }

    @Override
    public long take(Place place, int grade, long count) {
        if (!wells.containsKey(place)) {
            return 0;
        }
        var motes = wells.get(place).network().motes();
        long taken = Math.min(count, motes.getOrDefault(grade, 0L));
        motes.merge(grade, -taken, Long::sum);
        return taken;
    }

    @Override
    public long put(Place place, int grade, long count) {
        if (!wells.containsKey(place)) {
            return 0;
        }
        var network = wells.get(place).network();
        long total = network.motes().values().stream().mapToLong(Long::longValue).sum();
        long put = Math.min(count, network.capacity() - total);
        network.motes().merge(grade, put, Long::sum);
        return put;
    }

    @Override
    public boolean loaded(Place place) {
        return wells.containsKey(place) && !unseen.contains(place);
    }

    @Override
    public boolean lay(Place place, Line line) {
        if (!wells.containsKey(place) || unseen.contains(place)) {
            return false;
        }
        laid.add(place);
        laidOf.add(line);
        return true;
    }
}
