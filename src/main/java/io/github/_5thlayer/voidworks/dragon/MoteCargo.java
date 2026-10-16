// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The Mote line's cargo: motes, by grade. A stop is judged by the highest of its Wells' targets,
 * another owner's on the network never binding it: it is short of what it lacks of its target, and
 * gives its excess over it, or all it holds as a reservoir, with no target. A mote is worth something
 * at a stop only above its Void Pressure; a dragon loads and unloads only such grades, highest first,
 * and loads from the source of highest network pressure.
 */
final class MoteCargo implements Cargo {

    private final Wells wells;
    private final long load;

    /** @param load the most motes a young dragon carries */
    MoteCargo(Wells wells, long load) {
        this.wells = wells;
        this.load = load;
    }

    @Override
    public long load() {
        return load;
    }

    /** What it lacks of its target, less every mote on its way to it, whatever its grade. */
    @Override
    public long shortfall(Stop stop, List<Trip> bound) {
        var target = target(stop);
        return target.isPresent() ? Math.max(0, target.getAsLong() - stop.total() - claimed(bound)) : 0;
    }

    @Override
    public long room(Stop stop, long healing) {
        return Math.max(0, capacity(stop) - stop.total()) + healing;
    }

    /** Of {@code from}'s excess, no more than it holds of grades worth something at {@code to}, less every mote spoken for there. */
    @Override
    public long offer(Stop from, Stop to, List<Trip> loading, List<Trip> bound) {
        long worth = from.motes().entrySet().stream().filter(held -> worth(to, held.getKey())).mapToLong(Map.Entry::getValue).sum();
        return Math.max(0, Math.min(excess(from), worth) - claimed(loading));
    }

    /** The fullest first, by which its network pressure rises. */
    @Override
    public Comparator<Stop> sources(Stop to) {
        return Comparator.comparingDouble(stop -> (double) stop.total() / capacity(stop));
    }

    @Override
    public boolean wants(Stop stop, Load load) {
        return motes(load).keySet().stream().anyMatch(grade -> worth(stop, grade));
    }

    /** A claim on as many motes as {@code to} still needs and {@code from} still offers, whatever their grades. */
    @Override
    public Trip planning(Trip trip, Stop from, Stop to, long most, List<Trip> loading, List<Trip> bound) {
        return trip.claiming(Math.min(most, Math.min(shortfall(to, bound), offer(from, to, loading, bound))), Map.of());
    }

    /** Highest grade first, as many as {@code to} still needs and {@code from} still offers. */
    @Override
    public Load take(Stop from, Place place, Stop to, long most, List<Trip> loading, List<Trip> bound) {
        var taken = new TreeMap<Integer, Long>();
        long left = Math.min(most, Math.min(shortfall(to, bound), offer(from, to, loading, bound)));
        for (var grade : from.motes().sequencedKeySet().reversed()) {
            if (left == 0) {
                break;
            }
            if (worth(to, grade)) {
                long took = wells.take(place, grade, Math.min(left, from.motes().get(grade)));
                if (took > 0) {
                    taken.merge(grade, took, Long::sum);
                    left -= took;
                }
            }
        }
        return new MoteLoad(taken);
    }

    /** Highest grade first: of grades worth something there, or for a load stowed, of any. */
    @Override
    public Load put(Load load, Place place, Stop to, long most, boolean stow) {
        var motes = motes(load);
        var left = new TreeMap<>(motes);
        long room = most;
        for (var grade : motes.sequencedKeySet().reversed()) {
            if (room == 0) {
                break;
            }
            if (stow || worth(to, grade)) {
                long put = wells.put(place, grade, Math.min(room, left.get(grade)));
                room -= put;
                if (left.merge(grade, -put, Long::sum) == 0) {
                    left.remove(grade);
                }
            }
        }
        return new MoteLoad(left);
    }

    /** Lowest grade first. */
    @Override
    public Load putBack(Load load, Place place, long most) {
        var motes = motes(load);
        var left = new TreeMap<>(motes);
        long rest = most;
        for (var grade : motes.sequencedKeySet()) {
            if (rest == 0) {
                break;
            }
            long put = wells.put(place, grade, Math.min(rest, left.get(grade)));
            rest -= put;
            if (left.merge(grade, -put, Long::sum) == 0) {
                left.remove(grade);
            }
        }
        return new MoteLoad(left);
    }

    /** @return the motes of {@code load}, grade to count: none of a load of items, which a Mote-line dragon never takes up */
    private static SortedMap<Integer, Long> motes(Load load) {
        return switch (load) {
            case MoteLoad motes -> motes.motes();
            case MatterLoad matter -> MoteLoad.NONE.motes();
        };
    }

    /** @return the motes claimed on {@code trips}, whatever their grades */
    private static long claimed(List<Trip> trips) {
        return trips.stream().mapToLong(Trip::claimed).sum();
    }

    /** @return the highest of {@code stop}'s Wells' targets; empty, a reservoir, when none has one */
    private static OptionalLong target(Stop stop) {
        return stop.wells().stream().map(Wells.Well::target).filter(OptionalLong::isPresent).mapToLong(OptionalLong::getAsLong).max();
    }

    /** @return how many motes {@code stop}'s network holds, of every grade together, at most */
    private static long capacity(Stop stop) {
        return stop.wells().getFirst().capacity();
    }

    /** @return how many {@code stop} gives: all of a reservoir, a stop's excess over its target, none below it */
    private static long excess(Stop stop) {
        var target = target(stop);
        return target.isPresent() ? Math.max(0, stop.total() - target.getAsLong()) : stop.total();
    }

    /** @return whether a mote of {@code grade} is worth something at {@code stop} */
    private static boolean worth(Stop stop, int grade) {
        return grade > stop.wells().getFirst().pressure();
    }
}
