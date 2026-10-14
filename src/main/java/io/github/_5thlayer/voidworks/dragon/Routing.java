// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Void Dragons carrying motes between their owner's Void Wells, on their records alone, with no chunk
 * loaded (ADR 0005). Plain logic over {@link Wells}, so it is tested with no game running;
 * {@code DragonRouting} steps it on the server.
 *
 * <p>An owner's Wells on one network are one stop (ADR 0006): a dragon never carries between them,
 * and judges the network by the highest of their targets, another owner's Wells and targets there
 * never binding it. A dragon with nothing to do plans a trip: to a stop of its owner's below its
 * target, from the owner's stop of highest network pressure that holds something worth delivering
 * there, a reservoir or a stop above its target, which gives its excess. A stop below its target never
 * gives. It dives to the source, stops to load the grades worth something at the destination, highest
 * first, up to its load, dives on, and stops to unload, each time at the stop's Well its dive reaches
 * soonest. Each trip is claimed: a network's need counts the motes already on their way to any Well on
 * it, whoever's dragon carries them, so dragons sharing it never overshoot, and what a source gives
 * counts the motes other dragons are on their way to load there, so none sets off for motes another
 * will take. A destination that is gone, no longer whole or no longer short when it arrives sends the
 * load on to another of the owner's stops that needs it, else to the one with most room; with room
 * nowhere, the dragon holds its load where it is until there is. It rests at the Well it last stopped at.
 *
 * <p>Diving, it weakens by the worse of its two ends' pressure ({@link Harm}), as it goes; a dive is a
 * distance, which it covers at the speed its health gives it, so it slows as it weakens. Before it
 * dives from a Well it heals from that Well's motes, its network's, as far as they go.
 */
public final class Routing {

    /** The most legs one dragon ends in one step, so no plan that went wrong turns a step into an endless loop. */
    private static final int MOST_LEGS = 8;

    private final Wells wells;
    private final Dive dive;
    private final Harm harm;
    private final long load;
    private final int stopTicks;

    /**
     * @param load the most motes a dragon carries
     * @param stopTicks how long a dragon stops at a Well to load or unload
     */
    public Routing(Wells wells, Dive dive, Harm harm, long load, int stopTicks) {
        this.wells = wells;
        this.dive = dive;
        this.harm = harm;
        this.load = load;
        this.stopTicks = stopTicks;
    }

    /** Moves every dragon on to game time {@code now}: each leg that has ended leads to its next, and each dragon with nothing to do looks for a trip. */
    public void step(VoidDragons dragons, long now) {
        for (var dragon : List.copyOf(dragons.all())) {
            for (int legs = 0; legs < MOST_LEGS; legs++) {
                var next = next(dragons, dragon, now);
                if (next.equals(dragon)) {
                    break;
                }
                // Each change is in the records at once, so the next dragon's plan counts its claim.
                dragons.update(next);
                dragon = next;
            }
        }
    }

    private VoidDragonRecord next(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        if (dragon.trip().isPresent()) {
            var trip = dragon.trip().get();
            var divingTo = trip.divingTo();
            if (divingTo.isPresent() && trip.toCover() > 0 && trip.countedTo() < now) {
                return dived(dragon, trip, divingTo.get(), now);
            }
            if (trip.until() > now) {
                return dragon;
            }
            return switch (trip.leg()) {
                case TO_SOURCE -> load(dragons, dragon, trip, now);
                case LOADING -> setOff(dragon, trip.then(Trip.Leg.TO_DESTINATION, now), now);
                case TO_DESTINATION -> unload(dragons, dragon, trip, now);
                case UNLOADING -> dragon.on(Optional.empty());
            };
        }
        return dragon.load().isEmpty() ? plan(dragons, dragon, now) : send(dragons, dragon, now);
    }

    /** A dragon with nothing to do and nothing carried: a trip to the stop that needs most, from the fullest that can give it something; or none. */
    private VoidDragonRecord plan(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        var owned = stops(dragon.owner());
        var needing = owned.stream()
                .filter(stop -> need(dragons, stop, dragon.id()) > 0)
                .sorted(Comparator.comparingLong((Stop stop) -> need(dragons, stop, dragon.id())).reversed())
                .toList();
        for (var destination : needing) {
            var source = owned.stream()
                    .filter(stop -> deliverable(dragons, stop, destination, dragon.id()) > 0)
                    .max(Comparator.comparingDouble(Stop::fill));
            if (source.isPresent()) {
                long claimed = Math.min(load, Math.min(need(dragons, destination, dragon.id()), deliverable(dragons, source.get(), destination, dragon.id())));
                var from = source.get().nearest(dragon.rest(), dive);
                return setOff(dragon, Trip.planned(Optional.of(from), destination.nearest(from, dive), claimed, false,
                        Trip.Leg.TO_SOURCE), now);
            }
        }
        return dragon;
    }

    /** Arrived at the source: it loads what is worth something at the destination, highest grade first, or gives up the trip with nothing to load. */
    private VoidDragonRecord load(VoidDragons dragons, VoidDragonRecord dragon, Trip trip, long now) {
        var source = trip.source().orElseThrow();
        var stopped = dragon.resting(source);
        var from = stop(dragon.owner(), source);
        var to = stop(dragon.owner(), trip.destination());
        if (from.isEmpty() || to.isEmpty()) {
            return stopped.on(Optional.empty());
        }
        long most = Math.min(load, Math.min(need(dragons, to.get(), dragon.id()), deliverable(dragons, from.get(), to.get(), dragon.id())));
        var loaded = new TreeMap<Integer, Long>();
        long left = most;
        for (var grade : from.get().motes().sequencedKeySet().reversed()) {
            if (left == 0) {
                break;
            }
            if (to.get().worth(grade)) {
                long taken = wells.take(source, grade, Math.min(left, from.get().motes().get(grade)));
                if (taken > 0) {
                    loaded.merge(grade, taken, Long::sum);
                    left -= taken;
                }
            }
        }
        if (loaded.isEmpty()) {
            return stopped.on(Optional.empty());
        }
        return stopped.carrying(loaded).on(Optional.of(trip.claiming(most - left).then(Trip.Leg.LOADING, now + stopTicks)));
    }

    /**
     * Arrived at the destination: it unloads, highest grade first, what the Well is still short of in
     * grades worth something there, or for a load stowed, as much as fits. With nothing unloaded, the
     * trip ends and the load is sent on from here.
     */
    private VoidDragonRecord unload(VoidDragons dragons, VoidDragonRecord dragon, Trip trip, long now) {
        var stopped = dragon.resting(trip.destination());
        var to = stop(dragon.owner(), trip.destination());
        long most = to.map(stop -> trip.stow() ? stop.room() : need(dragons, stop, dragon.id())).orElse(0L);
        var left = new TreeMap<>(dragon.load());
        long room = most;
        for (var grade : dragon.load().sequencedKeySet().reversed()) {
            if (room == 0) {
                break;
            }
            if (trip.stow() || to.get().worth(grade)) {
                long put = wells.put(trip.destination(), grade, Math.min(room, left.get(grade)));
                room -= put;
                if (left.merge(grade, -put, Long::sum) == 0) {
                    left.remove(grade);
                }
            }
        }
        if (room == most) {
            return stopped.on(Optional.empty());
        }
        return stopped.carrying(left).on(Optional.of(trip.claiming(0).then(Trip.Leg.UNLOADING, now + stopTicks)));
    }

    /**
     * A dragon carrying a load and with no trip: to the owner's stop that needs most of what it
     * carries, else to the one with most room, to stow it; with room nowhere it holds the load.
     */
    private VoidDragonRecord send(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        var owned = stops(dragon.owner());
        var needing = owned.stream()
                .filter(stop -> dragon.load().keySet().stream().anyMatch(stop::worth))
                .max(Comparator.comparingLong(stop -> need(dragons, stop, dragon.id())))
                .filter(stop -> need(dragons, stop, dragon.id()) > 0);
        if (needing.isPresent()) {
            long claimed = Math.min(dragon.carried(), need(dragons, needing.get(), dragon.id()));
            return setOff(dragon, Trip.planned(Optional.empty(), needing.get().nearest(dragon.rest(), dive), claimed, false,
                    Trip.Leg.TO_DESTINATION), now);
        }
        var roomiest = owned.stream()
                .max(Comparator.comparingLong(stop -> room(dragons, stop, dragon.id())))
                .filter(stop -> room(dragons, stop, dragon.id()) > 0);
        if (roomiest.isPresent()) {
            long claimed = Math.min(dragon.carried(), room(dragons, roomiest.get(), dragon.id()));
            return setOff(dragon, Trip.planned(Optional.empty(), roomiest.get().nearest(dragon.rest(), dive), claimed, true,
                    Trip.Leg.TO_DESTINATION), now);
        }
        return dragon;
    }

    /**
     * Sets off on {@code trip}, a dive from where it is, at game time {@code now}: from a Well it heals
     * first, then dives at the speed its health gives it.
     *
     * @return {@code dragon} on its way
     */
    private VoidDragonRecord setOff(VoidDragonRecord dragon, Trip trip, long now) {
        var to = trip.divingTo().orElseThrow();
        var healed = to.equals(dragon.rest()) ? dragon : healed(dragon);
        return healed.on(Optional.of(trip.diving(now, dive.ticks(dragon.rest(), to) * VoidDragonRecord.WHOLE_SPEED, healed.speed())));
    }

    /**
     * @return {@code dragon} on its dive on {@code trip} to {@code to}, counted on to game time {@code now}
     *         or to when it arrives, if sooner: the ground it covered at the speed it had, and the health
     *         it lost on the way
     */
    private VoidDragonRecord dived(VoidDragonRecord dragon, Trip trip, Place to, long now) {
        long divedTo = trip.divedTo(now, dragon.speed());
        var weakened = dragon.weakened(harm.weakening(dragon.rest().dimension(), to.dimension(), trip.countedTo(), divedTo));
        return weakened.on(Optional.of(trip.covered(divedTo, dragon.speed(), weakened.speed())));
    }

    /**
     * @return {@code dragon} healed from the Well where it is, if there is one, a point of health for
     *         each {@link Harm#motesPerPoint} motes, lowest grade first, as far as they go
     */
    private VoidDragonRecord healed(VoidDragonRecord dragon) {
        var place = dragon.rest();
        var stop = stop(dragon.owner(), place);
        if (stop.isEmpty()) {
            return dragon;
        }
        long owed = Math.min(VoidDragonRecord.FULL_HEALTH - dragon.health(), stop.get().total() / harm.motesPerPoint()) * harm.motesPerPoint();
        long paid = 0;
        for (var held : stop.get().motes().entrySet()) {
            if (paid == owed) {
                break;
            }
            paid += wells.take(place, held.getKey(), Math.min(owed - paid, held.getValue()));
        }
        return dragon.healed(Math.toIntExact(paid / harm.motesPerPoint()));
    }

    /** @return {@code owner}'s stops: their Wells, those on one network together */
    private List<Stop> stops(UUID owner) {
        var byNetwork = new LinkedHashMap<Set<Place>, List<Wells.Well>>();
        for (var well : wells.of(owner)) {
            byNetwork.computeIfAbsent(well.network(), network -> new ArrayList<>()).add(well);
        }
        return byNetwork.values().stream().map(Stop::new).toList();
    }

    /** @return {@code owner}'s stop that takes in their Well at {@code place}, if it is still one of theirs, whole and awake */
    private Optional<Stop> stop(UUID owner, Place place) {
        return stops(owner).stream().filter(stop -> stop.has(place)).findFirst();
    }

    /**
     * @return how many motes {@code from} gives that are worth something at {@code to}, less those other
     *         dragons than {@code except}, whoever's, are on their way to load on its network, which are spoken for
     */
    private static long deliverable(VoidDragons dragons, Stop from, Stop to, UUID except) {
        long spokenFor = claimed(dragons, except,
                trip -> trip.leg() == Trip.Leg.TO_SOURCE && trip.source().filter(from.network()::contains).isPresent());
        return Math.max(0, from.deliverableTo(to) - spokenFor);
    }

    /** @return how many motes {@code stop} is short of its target, less those other dragons than {@code except} have claimed for its network */
    private static long need(VoidDragons dragons, Stop stop, UUID except) {
        return Math.max(0, stop.shortfall() - claimedFor(dragons, stop, except));
    }

    /** @return how many more motes {@code stop} takes, less those other dragons than {@code except} have claimed for its network */
    private static long room(VoidDragons dragons, Stop stop, UUID except) {
        return Math.max(0, stop.room() - claimedFor(dragons, stop, except));
    }

    /** @return the motes on their way to any Well on {@code stop}'s network on the trips of every dragon but {@code except}, whoever's */
    private static long claimedFor(VoidDragons dragons, Stop stop, UUID except) {
        return claimed(dragons, except, trip -> stop.network().contains(trip.destination()));
    }

    /** @return the motes claimed on the trips that are {@code such} of every dragon but {@code except}, whoever's */
    private static long claimed(VoidDragons dragons, UUID except, Predicate<Trip> such) {
        return dragons.all().stream()
                .filter(dragon -> !dragon.id().equals(except))
                .flatMap(dragon -> dragon.trip().stream())
                .filter(such)
                .mapToLong(Trip::claimed)
                .sum();
    }

    /**
     * An owner's Wells on one network, one stop for the owner's dragons (ADR 0006): a dragon never
     * carries between them, and each is a place to stop, load, unload and heal. It holds what its
     * network holds, and is judged by the highest of their targets, another owner's on the network
     * never binding it.
     *
     * @param wells the owner's Wells on the network
     */
    private record Stop(List<Wells.Well> wells) {

        /** @return the places of every Well on its network, whoever's */
        Set<Place> network() {
            return wells.getFirst().network();
        }

        /** @return whether the owner's Well at {@code place} is one of its own */
        boolean has(Place place) {
            return wells.stream().anyMatch(well -> well.place().equals(place));
        }

        /** @return the place of its Well a dive from {@code from} reaches soonest, {@code from} itself if it is one */
        Place nearest(Place from, Dive dive) {
            return wells.stream().map(Wells.Well::place).min(Comparator.comparingLong(place -> dive.ticks(from, place))).orElseThrow();
        }

        /** @return the highest of its Wells' targets; empty, a reservoir, when none has one */
        OptionalLong target() {
            return wells.stream().map(Wells.Well::target).filter(OptionalLong::isPresent).mapToLong(OptionalLong::getAsLong).max();
        }

        /** @return what its network holds, grade to count */
        SortedMap<Integer, Long> motes() {
            return wells.getFirst().motes();
        }

        /** @return how many motes it holds, of every grade together */
        long total() {
            return motes().values().stream().mapToLong(Long::longValue).sum();
        }

        /** @return how many more it takes */
        long room() {
            return Math.max(0, wells.getFirst().capacity() - total());
        }

        /** @return how many it is short of its target; none for a reservoir or a stop at or above it */
        long shortfall() {
            return target().isPresent() ? Math.max(0, target().getAsLong() - total()) : 0;
        }

        /** @return how many it gives: all of a reservoir, a stop's excess over its target, none below it */
        long excess() {
            return target().isPresent() ? Math.max(0, total() - target().getAsLong()) : total();
        }

        /** @return how full it is, from 0 to 1, by which its network pressure rises */
        double fill() {
            return (double) total() / wells.getFirst().capacity();
        }

        /** @return how many motes it gives that are worth something at {@code to}: of its excess, no more than it holds of such grades */
        long deliverableTo(Stop to) {
            long worth = motes().entrySet().stream().filter(held -> to.worth(held.getKey())).mapToLong(Map.Entry::getValue).sum();
            return Math.min(excess(), worth);
        }

        /** @return whether a mote of {@code grade} is worth something here */
        boolean worth(int grade) {
            return grade > wells.getFirst().pressure();
        }
    }
}
