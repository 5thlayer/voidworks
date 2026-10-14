// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Void Dragons carrying motes between their owner's Void Wells, on their records alone, with no chunk
 * loaded (ADR 0005). Plain logic over {@link Wells}, so it is tested with no game running;
 * {@code DragonRouting} steps it on the server.
 *
 * <p>A dragon with nothing to do plans a trip: to a Well of its owner's below its target, from the
 * owner's Well of highest network pressure that holds something worth delivering there, a reservoir
 * or a Well above its target, which gives its excess. A Well below its target never gives. It dives to
 * the source, stops to load the grades worth something at the destination, highest first, up to its
 * load, dives on, and stops to unload. Each trip is claimed: a Well's need counts the motes already on
 * their way to it, so dragons sharing it never overshoot, and what a source gives counts the motes
 * other dragons are on their way to load there, so none sets off for motes another will take. A destination that is gone, no longer whole
 * or no longer short when it arrives sends the load on to another of the owner's Wells that needs it,
 * else to the one with most room; with room nowhere, the dragon holds its load where it is until there
 * is. It rests at the Well it last stopped at.
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

    /** A dragon with nothing to do and nothing carried: a trip to the Well that needs most, from the fullest that can give it something; or none. */
    private VoidDragonRecord plan(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        var owned = wells.of(dragon.owner());
        var needing = owned.stream()
                .filter(well -> need(dragons, well, dragon.id()) > 0)
                .sorted(Comparator.comparingLong((Wells.Well well) -> need(dragons, well, dragon.id())).reversed())
                .toList();
        for (var destination : needing) {
            var source = owned.stream()
                    .filter(well -> deliverable(dragons, well, destination, dragon.id()) > 0)
                    .max(Comparator.comparingDouble(Wells.Well::fill));
            if (source.isPresent()) {
                long claimed = Math.min(load, Math.min(need(dragons, destination, dragon.id()), deliverable(dragons, source.get(), destination, dragon.id())));
                return setOff(dragon, Trip.planned(Optional.of(source.get().place()), destination.place(), claimed, false,
                        Trip.Leg.TO_SOURCE), now);
            }
        }
        return dragon;
    }

    /** Arrived at the source: it loads what is worth something at the destination, highest grade first, or gives up the trip with nothing to load. */
    private VoidDragonRecord load(VoidDragons dragons, VoidDragonRecord dragon, Trip trip, long now) {
        var source = trip.source().orElseThrow();
        var stopped = dragon.resting(source);
        var from = well(dragon.owner(), source);
        var to = well(dragon.owner(), trip.destination());
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
        var to = well(dragon.owner(), trip.destination());
        long most = to.map(well -> trip.stow() ? well.room() : need(dragons, well, dragon.id())).orElse(0L);
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
     * A dragon carrying a load and with no trip: to the owner's Well that needs most of what it
     * carries, else to the one with most room, to stow it; with room nowhere it holds the load.
     */
    private VoidDragonRecord send(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        var owned = wells.of(dragon.owner());
        var needing = owned.stream()
                .filter(well -> dragon.load().keySet().stream().anyMatch(well::worth))
                .max(Comparator.comparingLong(well -> need(dragons, well, dragon.id())))
                .filter(well -> need(dragons, well, dragon.id()) > 0);
        if (needing.isPresent()) {
            long claimed = Math.min(dragon.carried(), need(dragons, needing.get(), dragon.id()));
            return setOff(dragon, Trip.planned(Optional.empty(), needing.get().place(), claimed, false,
                    Trip.Leg.TO_DESTINATION), now);
        }
        var roomiest = owned.stream()
                .max(Comparator.comparingLong(well -> room(dragons, well, dragon.id())))
                .filter(well -> room(dragons, well, dragon.id()) > 0);
        if (roomiest.isPresent()) {
            long claimed = Math.min(dragon.carried(), room(dragons, roomiest.get(), dragon.id()));
            return setOff(dragon, Trip.planned(Optional.empty(), roomiest.get().place(), claimed, true,
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
        var well = well(dragon.owner(), place);
        if (well.isEmpty()) {
            return dragon;
        }
        long owed = Math.min(VoidDragonRecord.FULL_HEALTH - dragon.health(), well.get().total() / harm.motesPerPoint()) * harm.motesPerPoint();
        long paid = 0;
        for (var held : well.get().motes().entrySet()) {
            if (paid == owed) {
                break;
            }
            paid += wells.take(place, held.getKey(), Math.min(owed - paid, held.getValue()));
        }
        return dragon.healed(Math.toIntExact(paid / harm.motesPerPoint()));
    }

    private Optional<Wells.Well> well(UUID owner, Place place) {
        return wells.of(owner).stream().filter(well -> well.place().equals(place)).findFirst();
    }

    /**
     * @return how many motes {@code from} gives that are worth something at {@code to}, less those other
     *         dragons than {@code except} are on their way to load there, which are spoken for
     */
    private static long deliverable(VoidDragons dragons, Wells.Well from, Wells.Well to, UUID except) {
        long spokenFor = dragons.all().stream()
                .filter(dragon -> !dragon.id().equals(except))
                .flatMap(dragon -> dragon.trip().stream())
                .filter(trip -> trip.leg() == Trip.Leg.TO_SOURCE && trip.source().equals(Optional.of(from.place())))
                .mapToLong(Trip::claimed)
                .sum();
        return Math.max(0, from.deliverableTo(to) - spokenFor);
    }

    /** @return how many motes {@code well} is short of its target, less those other dragons than {@code except} have claimed for it */
    private static long need(VoidDragons dragons, Wells.Well well, UUID except) {
        return Math.max(0, well.shortfall() - claimed(dragons, well.place(), except));
    }

    /** @return how many more motes {@code well} takes, less those other dragons than {@code except} have claimed for it */
    private static long room(VoidDragons dragons, Wells.Well well, UUID except) {
        return Math.max(0, well.room() - claimed(dragons, well.place(), except));
    }

    /** @return the motes on their way to {@code place} on the trips of every dragon but {@code except} */
    private static long claimed(VoidDragons dragons, Place place, UUID except) {
        return dragons.all().stream()
                .filter(dragon -> !dragon.id().equals(except))
                .flatMap(dragon -> dragon.trip().stream())
                .filter(trip -> trip.destination().equals(place))
                .mapToLong(Trip::claimed)
                .sum();
    }
}
