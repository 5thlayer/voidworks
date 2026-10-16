// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * The Matter line's cargo: items, in Well hatches, counted in stacks. A stop's hatches are all those of
 * its owner's on its Wells; another owner's are never touched. A requester is short by its request,
 * less what it holds and what is on its way to it, and a provider offers what it holds, less what other
 * dragons are on their way to load there, so trips claim items by kind and never overshoot a request.
 * A dragon loads from the stop whose providers hold most of what its destination lacks, the item most
 * short first, up to its load, and unloads into the requesters asking for what it carries; a load
 * stowed goes into providers with room. It never touches motes, which only heal it.
 */
final class MatterCargo implements Cargo {

    private final Hatches hatches;
    private final long load;

    /** @param load the most stacks a young dragon carries */
    MatterCargo(Hatches hatches, long load) {
        this.hatches = hatches;
        this.load = load;
    }

    @Override
    public long load() {
        return load;
    }

    /** The stacks its requesters lack, each item's part stack counting as one. */
    @Override
    public long shortfall(Stop stop, List<Trip> bound) {
        return lacking(stop, bound).values().stream().mapToLong(Lack::stacks).sum();
    }

    /**
     * Its providers' empty slots, where a load no requester wants is stowed: a requester takes only what it
     * asks for, so it makes no room for any load. Motes healing a dragon make it no room for items.
     */
    @Override
    public long room(Stop stop, long healing) {
        return providers(stop).stream().mapToLong(Hatches.Hatch::emptySlots).sum();
    }

    /** Of each item {@code to} lacks, what {@code from}'s providers hold, less what is spoken for there, in stacks. */
    @Override
    public long offer(Stop from, Stop to, List<Trip> loading, List<Trip> bound) {
        return covered(lacking(to, bound), held(from, loading));
    }

    /** The stop whose providers hold most of what {@code to} lacks. */
    @Override
    public Comparator<Stop> sources(Stop to) {
        var lacking = lacking(to, List.of());
        return Comparator.comparingLong(from -> covered(lacking, held(from, List.of())));
    }

    @Override
    public boolean wants(Stop stop, Load load) {
        var carried = load.items();
        return hatches(stop).stream().anyMatch(hatch -> hatch.request().filter(asked -> carried.containsKey(asked.item())).isPresent()
                && hatch.lacks() > 0);
    }

    /** A claim on the items of {@link #plan}. */
    @Override
    public Trip planning(Trip trip, Stop from, Stop to, long most, List<Trip> loading, List<Trip> bound) {
        var plan = plan(from, to, most, loading, bound);
        return trip.claiming(plan.values().stream().mapToLong(Lack::stacks).sum(), counts(plan));
    }

    /** The items of {@link #plan}, out of {@code from}'s providers, components and all, filling no more than {@code most} stacks. */
    @Override
    public Load take(Stop from, Place place, Stop to, long most, List<Trip> loading, List<Trip> bound) {
        var taken = MatterLoad.NONE;
        var providers = providers(from);
        for (var planned : plan(from, to, most, loading, bound).entrySet()) {
            long left = planned.getValue().count();
            for (var provider : providers) {
                for (var ware : provider.contents()) {
                    long count = Math.min(left, Math.min(ware.count(), taken.room(ware, most)));
                    if (ware.item().equals(planned.getKey()) && count > 0) {
                        long took = hatches.take(provider.place(), ware.withCount(count));
                        taken = taken.adding(ware.withCount(took));
                        left -= took;
                    }
                }
            }
        }
        return taken;
    }

    /** Into the requesters asking for what it carries, as far as each lacks it; or for a load stowed, into the providers with room. */
    @Override
    public Load put(Load load, Place place, Stop to, long most, boolean stow) {
        return stow ? fill(matter(load), List.of(), providers(to), most) : fill(matter(load), requesters(to), List.of(), most);
    }

    /** Into the requesters on the Well at {@code place} asking for what it carries, then into its providers with room. */
    @Override
    public Load putBack(Load load, Place place, long most) {
        var on = hatches.on(place);
        return fill(matter(load), on.stream().filter(Hatches.Hatch::requester).toList(), on.stream().filter(Hatches.Hatch::provider).toList(), most);
    }

    /**
     * Puts {@code load} into {@code requesters}, each as far as it lacks what it asks for, then into
     * {@code providers} as far as they have room, no more of it than {@code most} stacks' worth.
     *
     * @return what is left of it
     */
    private MatterLoad fill(MatterLoad load, List<Hatches.Hatch> requesters, List<Hatches.Hatch> providers, long most) {
        var left = load;
        // What is left to put, in stacks, each worth its item's stack size.
        long stacks = most;
        for (var requester : requesters) {
            var asked = requester.request();
            long lacks = requester.lacks();
            for (var ware : left.wares()) {
                if (asked.isPresent() && ware.item().equals(asked.get().item())) {
                    long put = hatches.put(requester.place(), ware.withCount(Math.min(Math.min(ware.count(), lacks), stacks * ware.stackSize())));
                    left = left.less(ware.withCount(put));
                    lacks -= put;
                    stacks -= Ware.stacks(put, ware.stackSize());
                }
            }
        }
        for (var provider : providers) {
            for (var ware : left.wares()) {
                long put = hatches.put(provider.place(), ware.withCount(Math.min(ware.count(), stacks * ware.stackSize())));
                left = left.less(ware.withCount(put));
                stacks -= Ware.stacks(put, ware.stackSize());
            }
        }
        return left;
    }

    /**
     * @return the items to take out of {@code from} for {@code to}: of each item {@code to} lacks, after
     *         what is on its way to it, as many as {@code from} holds, after what is spoken for there, the
     *         item most short first, until they fill {@code most} stacks
     */
    private Map<Identifier, Lack> plan(Stop from, Stop to, long most, List<Trip> loading, List<Trip> bound) {
        var held = held(from, loading);
        var plan = new LinkedHashMap<Identifier, Lack>();
        long left = most;
        var lacking = lacking(to, bound).entrySet().stream()
                .sorted(Map.Entry.<Identifier, Lack>comparingByValue(Comparator.comparingLong(Lack::stacks)).reversed())
                .toList();
        for (var lack : lacking) {
            var taking = lack.getValue().of(Math.min(held.getOrDefault(lack.getKey(), 0L), left * lack.getValue().stackSize()));
            if (taking.count() > 0) {
                plan.put(lack.getKey(), taking);
                left -= taking.stacks();
            }
        }
        return plan;
    }

    /** @return how many of each item {@code stop}'s requesters lack, less what is on its way to it on {@code bound} */
    private Map<Identifier, Lack> lacking(Stop stop, List<Trip> bound) {
        var lacking = new LinkedHashMap<Identifier, Lack>();
        for (var hatch : hatches(stop)) {
            hatch.request().ifPresent(asked -> lacking.merge(asked.item(), new Lack(hatch.lacks(), asked.stackSize()), Lack::plus));
        }
        bound.forEach(trip -> trip.items().forEach((item, count) -> lacking.computeIfPresent(item, (kind, lack) -> lack.less(count))));
        lacking.values().removeIf(lack -> lack.count() == 0);
        return lacking;
    }

    /** @return how many of each item's kind {@code stop}'s providers hold, less what other dragons are on their way to load there on {@code loading} */
    private Map<Identifier, Long> held(Stop stop, List<Trip> loading) {
        var held = new HashMap<Identifier, Long>();
        providers(stop).forEach(hatch -> hatch.contents().forEach(ware -> held.merge(ware.item(), ware.count(), Long::sum)));
        loading.forEach(trip -> trip.items().forEach((item, count) -> held.computeIfPresent(item, (kind, has) -> Math.max(0, has - count))));
        return held;
    }

    /** @return the stacks of {@code lacking} that {@code held} covers, item by item */
    private static long covered(Map<Identifier, Lack> lacking, Map<Identifier, Long> held) {
        return lacking.entrySet().stream().mapToLong(lack -> lack.getValue().of(held.getOrDefault(lack.getKey(), 0L)).stacks()).sum();
    }

    /** @return the hatches of every Well of {@code stop}'s, its owner's */
    private List<Hatches.Hatch> hatches(Stop stop) {
        return stop.wells().stream().flatMap(well -> hatches.on(well.place()).stream()).toList();
    }

    private List<Hatches.Hatch> providers(Stop stop) {
        return hatches(stop).stream().filter(Hatches.Hatch::provider).toList();
    }

    private List<Hatches.Hatch> requesters(Stop stop) {
        return hatches(stop).stream().filter(Hatches.Hatch::requester).toList();
    }

    private static Map<Identifier, Long> counts(Map<Identifier, Lack> lacks) {
        var counts = new LinkedHashMap<Identifier, Long>();
        lacks.forEach((item, lack) -> counts.put(item, lack.count()));
        return counts;
    }

    /** @return the items of {@code load}: none of a load of motes, which a Matter-line dragon never takes up */
    private static MatterLoad matter(Load load) {
        return switch (load) {
            case MatterLoad matter -> matter;
            case MoteLoad motes -> MatterLoad.NONE;
        };
    }

    /** So many of one item, {@code stackSize} to a stack. */
    private record Lack(long count, int stackSize) {

        long stacks() {
            return Ware.stacks(count, stackSize);
        }

        /** @return no more of it than {@code most} */
        Lack of(long most) {
            return new Lack(Math.min(count, most), stackSize);
        }

        Lack plus(Lack other) {
            return new Lack(count + other.count, stackSize);
        }

        Lack less(long fewer) {
            return new Lack(Math.max(0, count - fewer), stackSize);
        }
    }
}
