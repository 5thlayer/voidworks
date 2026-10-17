// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.math.LongMath;
import net.minecraft.resources.Identifier;

/**
 * The Matter line's cargo: items, in Well hatches, counted in stacks. A stop's hatches are all those of
 * its owner's on its Wells; another owner's are never touched. A requester is short by its request,
 * less what it holds and what is on its way to it, and a provider offers what it holds, less what other
 * dragons are on their way to load there, so trips claim items by kind and never overshoot a request.
 * A dragon loads from the stop whose providers hold most of what its destination lacks, the item most
 * short first, up to its load, and unloads into the requesters asking for what it carries; a load
 * stowed goes into providers with room.
 *
 * <p>As it loads, it pays the {@link Toll} for what it loads, priced to its destination, from the motes of
 * the network it loads on, by the spend rule at that Well's core; it never carries motes, and those
 * left after the Toll heal it. A stop offers only what its network can pay the Toll for, so a stop that
 * can pay for none is never loaded from.
 */
final class MatterCargo implements Cargo {

    private final Hatches hatches;
    private final Wells wells;
    private final long load;
    private final Toll toll;

    /**
     * @param load the most stacks a young dragon carries
     * @param toll what a load pays as it loads, out of {@code wells}
     */
    MatterCargo(Hatches hatches, Wells wells, long load, Toll toll) {
        this.hatches = hatches;
        this.wells = wells;
        this.load = load;
        this.toll = toll;
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
     * asks for, so it makes no room for any load.
     */
    @Override
    public long room(Stop stop) {
        return providers(stop).stream().mapToLong(Hatches.Hatch::emptySlots).sum();
    }

    /**
     * Of each item {@code to} lacks, what {@code from}'s providers hold, less what is spoken for there, as
     * far as {@code from}'s network can pay the Toll to {@code to} for it, in stacks.
     */
    @Override
    public long offer(Stop from, Stop to, List<Trip> loading, List<Trip> bound) {
        return plan(from, to, Long.MAX_VALUE, loading, bound).values().stream().mapToLong(Lack::stacks).sum();
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

    /**
     * The items of {@link #plan}, out of {@code from}'s providers, components and all, filling no more than
     * {@code most} stacks, their Toll to {@code to} paid out of the network at the Well at {@code place}.
     */
    @Override
    public Handled take(Stop from, Place place, Stop to, long most, List<Trip> loading, List<Trip> bound) {
        var taken = MatterLoad.NONE;
        var seen = seenAt(place);
        var providers = providers(from);
        var budget = new Budget(from, to.pressure());
        for (var planned : plan(from, to, most, loading, bound).entrySet()) {
            long left = planned.getValue().count();
            for (var provider : providers) {
                for (var ware : provider.contents()) {
                    if (!ware.item().equals(planned.getKey())) {
                        continue;
                    }
                    long count = budget.lift(ware.withCount(Math.min(left, Math.min(ware.count(), taken.room(ware, most)))));
                    if (count > 0) {
                        long took = hatches.take(provider.place(), ware.withCount(count));
                        taken = taken.adding(ware.withCount(took));
                        seen.moved(provider.place(), ware.withCount(took));
                        left -= took;
                    }
                }
            }
        }
        long paid = toll.of(taken.wares(), from.pressure(), to.pressure());
        Toll.spend(from.motes(), from.pressure(), paid).ifPresent(spend -> spend.motes().forEach((grade, count) -> wells.take(place, grade, count)));
        return new Handled(taken.paid(paid), seen.passing());
    }

    /**
     * @return how many stacks the provider at {@code place} on {@code from} holds, and for how many of them,
     *         in the order it holds them, its network can pay the Toll: to the stop of {@code stops} that
     *         lacks something it holds and stands lowest, the cheapest it is wanted at, else on a level
     *         trip; empty when no provider of {@code from}'s is there
     */
    Optional<Payable> payable(Place place, Stop from, List<Stop> stops) {
        return providers(from).stream()
                .filter(hatch -> hatch.place().equals(place))
                .findFirst()
                .map(hatch -> payable(hatch, from, stops));
    }

    private Payable payable(Hatches.Hatch hatch, Stop from, List<Stop> stops) {
        var held = hatch.contents().stream().map(Ware::item).toList();
        int to = stops.stream()
                .filter(stop -> !stop.equals(from) && lacking(stop, List.of()).keySet().stream().anyMatch(held::contains))
                .mapToInt(Stop::pressure)
                .min().orElse(from.pressure());
        var budget = new Budget(from, to);
        long payable = 0;
        for (var ware : hatch.contents()) {
            long count = budget.lift(ware);
            payable += Ware.stacks(count, ware.stackSize());
            if (count < ware.count()) {
                break;
            }
        }
        return new Payable(new MatterLoad(hatch.contents()).amount(), payable);
    }

    /** Into the requesters asking for what it carries, as far as each lacks it; or for a load stowed, into the providers with room. */
    @Override
    public Handled put(Load load, Place place, Stop to, long most, boolean stow) {
        var seen = seenAt(place);
        var left = stow ? fill(matter(load), List.of(), providers(to), most, seen) : fill(matter(load), requesters(to), List.of(), most, seen);
        return new Handled(left, seen.passing());
    }

    /**
     * It keeps the items it took first, filling {@code keep} stacks, and puts the rest into {@code at}'s
     * hatches as on arrival: into the requesters asking for them, then into the providers with room. Motes
     * healing a dragon make no room for items.
     */
    @Override
    public Optional<Load> shed(Load load, Place place, Stop at, long keep, long healing) {
        var carried = matter(load);
        var kept = carried.upTo(keep);
        var rest = carried.less(kept);
        var requesters = requesters(at);
        var providers = providers(at);
        if (!fits(rest, requesters, providers)) {
            return Optional.empty();
        }
        return Optional.of(kept.plus(fill(rest, requesters, providers, Long.MAX_VALUE, Seen.UNSEEN)));
    }

    /**
     * Puts {@code load} into {@code requesters}, each as far as it lacks what it asks for, then into
     * {@code providers} as far as they have room, no more of it than {@code most} stacks' worth, each
     * put counting its part stack as one, each seen by {@code seen}.
     *
     * @return what is left of it
     */
    private MatterLoad fill(MatterLoad load, List<Hatches.Hatch> requesters, List<Hatches.Hatch> providers, long most, Seen seen) {
        var left = load;
        // What is left to put, in stacks, each worth its item's stack size.
        long stacks = most;
        for (var requester : requesters) {
            var asked = requester.request();
            long lacks = requester.lacks();
            for (var ware : left.wares()) {
                if (asked.isPresent() && ware.item().equals(asked.get().item())) {
                    long put = hatches.put(requester.place(), ware.withCount(Math.min(lacks, ware.within(stacks))));
                    left = left.less(ware.withCount(put));
                    seen.moved(requester.place(), ware.withCount(put));
                    lacks -= put;
                    stacks -= Ware.stacks(put, ware.stackSize());
                }
            }
        }
        for (var provider : providers) {
            for (var ware : left.wares()) {
                long put = hatches.put(provider.place(), ware.withCount(ware.within(stacks)));
                left = left.less(ware.withCount(put));
                seen.moved(provider.place(), ware.withCount(put));
                stacks -= Ware.stacks(put, ware.stackSize());
            }
        }
        return left;
    }

    /**
     * @return whether {@link #fill} puts all of {@code load}: into {@code requesters}, each as far as it
     *         lacks what it asks for and has empty slots, then into {@code providers}' empty slots. Room on
     *         stacks alike is left out, so it may say no where all of it would fit, but never yes where it
     *         would not.
     */
    private static boolean fits(MatterLoad load, List<Hatches.Hatch> requesters, List<Hatches.Hatch> providers) {
        var wares = load.wares();
        // What is left to put of each ware.
        var left = wares.stream().mapToLong(Ware::count).toArray();
        for (var requester : requesters) {
            var asked = requester.request();
            long lacks = requester.lacks();
            long slots = requester.emptySlots();
            for (int index = 0; index < wares.size(); index++) {
                var ware = wares.get(index);
                if (asked.isPresent() && ware.item().equals(asked.get().item())) {
                    long put = Math.min(left[index], Math.min(lacks, slots * ware.stackSize()));
                    left[index] -= put;
                    lacks -= put;
                    slots -= Ware.stacks(put, ware.stackSize());
                }
            }
        }
        long slots = 0;
        for (int index = 0; index < wares.size(); index++) {
            slots += Ware.stacks(left[index], wares.get(index).stackSize());
        }
        return slots <= providers.stream().mapToLong(Hatches.Hatch::emptySlots).sum();
    }

    /**
     * @return the items to take out of {@code from} for {@code to}: of each item {@code to} lacks, after
     *         what is on its way to it, as many as {@code from} holds, after what is spoken for there, the
     *         item most short first, until they fill {@code most} stacks or {@code from}'s network can pay
     *         the Toll to {@code to} for no more, each ware as far as it pays for it in the order the
     *         providers hold them
     */
    private Map<Identifier, Lack> plan(Stop from, Stop to, long most, List<Trip> loading, List<Trip> bound) {
        var spoken = new HashMap<Identifier, Long>();
        loading.forEach(trip -> trip.items().forEach((item, count) -> spoken.merge(item, count, Long::sum)));
        var budget = new Budget(from, to.pressure());
        var plan = new LinkedHashMap<Identifier, Lack>();
        long left = most;
        var lacking = lacking(to, bound).entrySet().stream()
                .sorted(Map.Entry.<Identifier, Lack>comparingByValue(Comparator.comparingLong(Lack::stacks)).reversed())
                .toList();
        for (var lack : lacking) {
            long wanted = Math.min(lack.getValue().count(), LongMath.saturatedMultiply(left, lack.getValue().stackSize()));
            // The first of its kind are spoken for, as other dragons take them first.
            long stillSpoken = spoken.getOrDefault(lack.getKey(), 0L);
            long taking = 0;
            var held = providers(from).stream().flatMap(provider -> provider.contents().stream()).filter(ware -> ware.item().equals(lack.getKey())).toList();
            for (var ware : held) {
                long free = Math.max(0, ware.count() - stillSpoken);
                stillSpoken = Math.max(0, stillSpoken - ware.count());
                taking += budget.lift(ware.withCount(Math.min(free, wanted - taking)));
            }
            if (taking > 0) {
                var taken = lack.getValue().of(taking);
                plan.put(lack.getKey(), taken);
                left -= taken.stacks();
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

    /** @return what is seen moving at the Well at {@code well}: what goes in or out of the hatches on it */
    private Seen seenAt(Place well) {
        return new Seen(hatches.on(well).stream().map(Hatches.Hatch::place).collect(Collectors.toSet()));
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

    /**
     * The items seen moving at the Well a dragon handles a load at: the first taken out of or put into each
     * hatch on that Well, a stack at most, in that order. Those of the stop's other Wells are not seen.
     */
    private static final class Seen {

        /** What a ridden dive puts back as its rider jumps, which nobody sees. */
        static final Seen UNSEEN = new Seen(Set.of());

        private final Set<Place> here;
        private final Map<Place, Ware> first = new LinkedHashMap<>();

        /** @param here the hatches on the Well it is seen at */
        Seen(Set<Place> here) {
            this.here = Set.copyOf(here);
        }

        void moved(Place hatch, Ware ware) {
            if (ware.count() > 0 && here.contains(hatch)) {
                first.putIfAbsent(hatch, ware.withCount(Math.min(ware.count(), ware.stackSize())));
            }
        }

        List<Passing> passing() {
            return first.entrySet().stream().map(moved -> new Passing(moved.getKey(), moved.getValue())).toList();
        }
    }

    /** What a stop's network can still pay the Toll with, for wares lifted from it to a Well of pressure {@code to}. */
    private final class Budget {

        private final int from;
        private final int to;
        private long left;

        Budget(Stop from, int to) {
            this.from = from.pressure();
            this.to = to;
            this.left = Toll.budget(from.motes(), this.from);
        }

        /** @return how many of {@code ware}, up to its count, it pays the Toll for, which it then has paid */
        long lift(Ware ware) {
            long count = toll.affordable(ware, from, to, left);
            left -= toll.of(ware.withCount(count), from, to);
            return count;
        }
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
