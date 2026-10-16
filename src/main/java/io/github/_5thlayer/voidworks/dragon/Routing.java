// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * nowhere, the dragon holds its load where it is until there is. It rests at the Well it last stopped
 * at; one away from its owner's Wells, with nothing to do, dives to the nearest of them to rest there.
 *
 * <p>Diving, it weakens by the worse of its two ends' pressure ({@link Harm}), as it goes; a dive is a
 * distance, which it covers at the speed its health gives it, so it slows as it weakens. Before it
 * dives from a Well it heals from that Well's motes, its network's, as far as they go.
 *
 * <p>It ages by the motes it unloads ({@link LifeStages}), which multiplies its speed and load, and
 * once it has outlived its old stage dies at the end of a stop at a loaded Well. Each time it has carried a set number of motes
 * as an adult since its last egg, counted as it unloads them, it
 * owes a Drained Dragon Egg, which it lays at the next Well it stops at, to load or unload, whose chunk
 * is loaded, with every other egg it owes; what it carried beyond them counts toward the next.
 *
 * <p>Its owner riding it at a stop holds it there, whatever it is doing, until they get off or pick
 * another Well of theirs ({@link #ride}): it dives there at once, carrying them, a stop added to its
 * trip, where it sets them down and unloads what that Well is short of, in grades worth something
 * there, before it goes on to its destination with the rest. Its owner standing on the rim of one of
 * their Wells calls a dragon ({@link Owners#calling}): one of theirs stopped there already waits there
 * once its stop is over; else the next of theirs to finish its stop, or the nearest with nothing
 * to do at once, dives there as a ridden dive does, its load cut to match, a stop added to its trip, waits while they stay, and then
 * goes on.
 */
public final class Routing {

    /** The most legs one dragon ends in one step, so no plan that went wrong turns a step into an endless loop. */
    private static final int MOST_LEGS = 8;

    private final Wells wells;
    private final Owners owners;
    private final Dive dive;
    private final Harm harm;
    private final long load;
    private final int stopTicks;
    private final long eggMotes;
    private final LifeStages stages;
    /** What each Line's dragons carry. */
    private final Map<Line, Cargo> cargoes = new EnumMap<>(Line.class);

    /**
     * @param load the most motes a dragon carries
     * @param stopTicks how long a dragon stops at a Well to load or unload
     * @param eggMotes how many motes a dragon carries as an adult for each egg it lays
     * @param stages how it ages, which multiplies its speed and load
     */
    public Routing(Wells wells, Owners owners, Dive dive, Harm harm, long load, int stopTicks, long eggMotes, LifeStages stages) {
        this.wells = wells;
        this.owners = owners;
        this.dive = dive;
        this.harm = harm;
        this.load = load;
        this.stopTicks = stopTicks;
        this.eggMotes = eggMotes;
        this.stages = stages;
        cargoes.put(Line.MOTE, new MoteCargo(wells));
        cargoes.put(Line.MATTER, new MatterCargo());
    }

    /**
     * Moves every dragon on to game time {@code now}: each leg that has ended leads to its next, and each
     * dragon with nothing to do looks for a trip. A dragon that has outlived its old stage dies once a
     * stop of its, at a Well whose chunk is loaded, is over, carrying nothing and having laid every egg it
     * owes there, whether or not its owner still rides it: its record is gone. At a Well not loaded it
     * lives on, to die at its next stop at a loaded one.
     *
     * @return the dragons that died, as they were when they did
     */
    public List<VoidDragonRecord> step(VoidDragons dragons, long now) {
        var died = new ArrayList<VoidDragonRecord>();
        for (var dragon : List.copyOf(dragons.all())) {
            for (int legs = 0; legs < MOST_LEGS; legs++) {
                if (endsItsLastStop(dragon, now)) {
                    var laid = laying(dragon, dragon.rest());
                    if (laid.towardEgg() < eggMotes) {
                        dragons.remove(laid.id());
                        died.add(laid);
                        break;
                    }
                    // It lives on until it has laid every egg it owes.
                    dragons.update(laid);
                    dragon = laid;
                }
                var next = next(dragons, dragon, now);
                if (next.equals(dragon)) {
                    break;
                }
                // Each change is in the records at once, so the next dragon's plan counts its claim.
                dragons.update(next);
                dragon = next;
            }
        }
        return died;
    }

    /**
     * @return whether {@code dragon} has outlived its old stage and its stop is over now, at a Well whose
     *         chunk is loaded, carrying nothing: where it dies, once it has laid what it owes
     */
    private boolean endsItsLastStop(VoidDragonRecord dragon, long now) {
        return stages.outlived(dragon.cargo()) && dragon.carried() == 0
                && dragon.trip().filter(trip -> trip.divingTo().isEmpty() && trip.until() <= now).isPresent()
                && wells.loaded(dragon.rest());
    }

    private VoidDragonRecord next(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        if (dragon.trip().isPresent()) {
            var trip = dragon.trip().get();
            var divingTo = trip.divingTo();
            if (divingTo.isPresent() && trip.toCover() > 0 && trip.countedTo() < now) {
                return dived(dragon, trip, divingTo.get(), now);
            }
            // Its rider holds it at its stop, however long the stop was to last.
            if (trip.until() > now || divingTo.isEmpty() && owners.riding(dragon.id())) {
                return dragon;
            }
            return switch (trip.leg()) {
                case TO_SOURCE -> load(dragons, dragon, trip, now);
                case LOADING -> answer(dragons, dragon, Optional.of(trip), now)
                        .orElseGet(() -> setOff(dragon, trip.then(Trip.Leg.TO_DESTINATION, now), now));
                case TO_DESTINATION -> unload(dragons, dragon, trip, now);
                case UNLOADING -> answer(dragons, dragon, Optional.of(trip), now).orElseGet(() -> dragon.on(Optional.empty()));
                case TO_CALL -> dragon.resting(trip.added().orElseThrow()).on(Optional.of(trip.then(Trip.Leg.WAITING, now + stopTicks)));
                case WAITING -> owners.calling(dragon.owner()).equals(trip.added()) ? dragon : goOn(dragon, trip, now);
                case TO_PICKED -> drop(dragons, dragon, trip, now);
                case SETTING_DOWN -> answer(dragons, dragon, Optional.of(trip), now).orElseGet(() -> goOn(dragon, trip, now));
            };
        }
        if (owners.riding(dragon.id())) {
            return dragon;
        }
        return answer(dragons, dragon, Optional.empty(), now)
                .orElseGet(() -> dragon.load().isEmpty() ? planOrGoToRest(dragons, dragon, now) : send(dragons, dragon, now));
    }

    /** A dragon with nothing carried plans a trip; with none to plan, away from its owner's Wells, whole and awake, it dives to the nearest of them to rest. */
    private VoidDragonRecord planOrGoToRest(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        var planned = plan(dragons, dragon, now);
        if (!planned.equals(dragon) || stop(dragon.owner(), dragon.rest()).isPresent()) {
            return planned;
        }
        return stops(dragon.owner()).stream()
                .map(stop -> stop.nearest(dragon.rest(), dive))
                .min(dive.soonestFrom(dragon.rest()))
                .map(well -> setOff(dragon, alone(well), now))
                .orElse(dragon);
    }

    /**
     * Its owner, riding the dragon {@code id} at a stop of theirs, picks their Well at {@code picked}: it
     * dives there at once, healing first as it leaves a Well, carrying them, a stop added to its trip.
     * The dive takes no more than a ridden dive does ({@link Dive#ridden}), and its load is cut by the
     * share of time saved, so it carries no more motes a second than unridden: it first puts what it
     * holds beyond that into the Well it is at, lowest grade first, which counts toward neither its
     * ageing nor its eggs.
     *
     * @return the dragon on its way; none, with nothing changed, while it is not stopped at one of its
     *         owner's Wells, when {@code picked} is not one of theirs, or when the Well it is at has no
     *         room for what it holds beyond its ridden load
     */
    public Ride ride(VoidDragons dragons, UUID id, Place picked, long now) {
        var found = dragons.get(id)
                .filter(dragon -> dragon.trip().flatMap(Trip::divingTo).isEmpty())
                .filter(dragon -> stop(dragon.owner(), picked).isPresent());
        var at = found.flatMap(dragon -> stop(dragon.owner(), dragon.rest()));
        if (at.isEmpty()) {
            return Ride.CANNOT;
        }
        var dragon = found.get();
        var trip = dragon.trip().orElseGet(() -> alone(picked)).adding(Trip.Leg.TO_PICKED, picked);
        var diving = diveAsRidden(dragon, at, trip, now);
        if (diving.isEmpty()) {
            return Ride.NO_ROOM;
        }
        dragons.update(diving.get());
        return Ride.DIVING;
    }

    /**
     * {@code dragon}, at the stop {@code at}, if any, sets off on {@code trip} to the stop added to it
     * as a ridden dive goes: healing first as it leaves a Well, in no more than a ridden dive's time
     * ({@link Dive#ridden}), its load cut by the share of time saved, so it carries no more motes a
     * second than unridden. It first puts what it holds beyond that into the Well it is at, lowest grade
     * first, which counts toward neither its ageing nor its eggs.
     *
     * @return the dragon on its way; empty when it holds more than its ridden load and the Well it is at,
     *         if any, has no room for the rest
     */
    private Optional<VoidDragonRecord> diveAsRidden(VoidDragonRecord dragon, Optional<Stop> at, Trip trip, long now) {
        // Timed at the speed it will dive at, healed as it leaves, which frees the room its healing takes.
        int healing = at.map(stop -> healing(dragon, stop)).orElse(0);
        int speed = stages.speed(dragon.healed(healing));
        long unridden = Math.ceilDiv(dive.ticks(dragon.rest(), trip.added().orElseThrow()) * VoidDragonRecord.WHOLE_SPEED, speed);
        long ridden = dive.ridden(unridden);
        long excess = ridden == unridden ? 0 : dragon.carried() - loadOf(dragon) * ridden / unridden;
        if (excess > at.map(stop -> cargo(dragon).room(stop, (long) healing * harm.motesPerPoint())).orElse(0L)) {
            return Optional.empty();
        }
        var lightened = excess > 0 ? dragon.carrying(cargo(dragon).putBack(dragon.load(), dragon.rest(), excess)) : dragon;
        var healed = healed(lightened);
        return Optional.of(healed.on(Optional.of(trip.claiming(Math.min(trip.claimed(), healed.carried())).diving(now, ridden * stages.speed(healed), stages.speed(healed)))));
    }

    /** What came of a rider's pick. */
    public enum Ride {

        /** The dragon dives, carrying them. */
        DIVING,
        /** It is not stopped at one of its owner's Wells, or the Well picked is not one of theirs. */
        CANNOT,
        /** The Well it is at has no room for what it holds beyond its ridden load. */
        NO_ROOM
    }

    /**
     * A dragon that has finished its stop, or has nothing to do, answers its owner's call, while they
     * call at a stop of theirs that none of their other dragons is answering or stopped at, and no other
     * with nothing to do would reach sooner: it dives
     * there, a stop added to {@code onward}, the rest of its trip, else to a trip of that stop alone,
     * as a ridden dive goes. One already at the Well called answers by waiting there,
     * with no dive.
     *
     * @return the dragon on its way, or waiting; empty while no call is left to answer
     */
    private Optional<VoidDragonRecord> answer(VoidDragons dragons, VoidDragonRecord dragon, Optional<Trip> onward, long now) {
        return owners.calling(dragon.owner())
                .filter(called -> stop(dragon.owner(), called).isPresent())
                .filter(called -> !nearerWithNothingToDo(dragons, dragon, called))
                .filter(called -> dragons.all().stream().noneMatch(other -> other.owner().equals(dragon.owner())
                        && (other.trip().filter(trip -> trip.answering(called)).isPresent()
                                || (!other.id().equals(dragon.id()) && stoppedAt(other, called)))))
                .map(called -> {
                    var added = onward.orElseGet(() -> alone(called)).adding(Trip.Leg.TO_CALL, called);
                    return called.equals(dragon.rest())
                            ? dragon.on(Optional.of(added.then(Trip.Leg.WAITING, now + stopTicks)))
                            : setOffToCall(dragon, added, now);
                });
    }

    /**
     * It sets off on {@code trip} to the Well its owner calls it to as though they rode it already
     * ({@link #diveAsRidden}); where it has nowhere to put back what it would, it dives the whole way
     * with all it carries.
     */
    private VoidDragonRecord setOffToCall(VoidDragonRecord dragon, Trip trip, long now) {
        return diveAsRidden(dragon, stop(dragon.owner(), dragon.rest()), trip, now)
                .orElseGet(() -> setOff(dragon, trip, now));
    }

    /**
     * @return whether another of {@code dragon}'s owner's dragons, with nothing to do and not ridden, would
     *         reach {@code called} sooner, or as soon from nearer, and so answers the call
     */
    private boolean nearerWithNothingToDo(VoidDragons dragons, VoidDragonRecord dragon, Place called) {
        var soonest = dive.soonestFrom(called);
        return dragons.all().stream().anyMatch(other -> other.owner().equals(dragon.owner()) && !other.id().equals(dragon.id())
                && other.trip().isEmpty() && !owners.riding(other.id()) && soonest.compare(other.rest(), dragon.rest()) < 0);
    }

    /** @return whether {@code dragon} is at the Well at {@code place}, resting or stopped there, not diving */
    private static boolean stoppedAt(VoidDragonRecord dragon, Place place) {
        return dragon.rest().equals(place) && dragon.trip().flatMap(Trip::divingTo).isEmpty();
    }

    /** @return a trip that is only a stop added at the Well at {@code place}, claiming nothing, after which it ends */
    private static Trip alone(Place place) {
        return Trip.planned(Optional.empty(), place, 0, false, Trip.Leg.TO_DESTINATION);
    }

    /** After a stop added to its trip, it goes on to its destination with what it still claims; claiming nothing, its trip ends. */
    private VoidDragonRecord goOn(VoidDragonRecord dragon, Trip trip, long now) {
        return trip.claimed() > 0 ? setOff(dragon, trip.goingOn(), now) : dragon.on(Optional.empty());
    }

    /**
     * Arrived at the Well its rider picked, it sets them down there and unloads what the Well is short
     * of, in grades worth something there, highest first; what it still carries, up to its claim, goes
     * on with the trip.
     */
    private VoidDragonRecord drop(VoidDragons dragons, VoidDragonRecord dragon, Trip trip, long now) {
        var place = trip.added().orElseThrow();
        var stopped = dragon.resting(place);
        var after = stop(dragon.owner(), place).map(stop -> stopped.carrying(cargo(dragon).put(stopped.load(), place, stop, need(dragons, stop, dragon), false))).orElse(stopped);
        long dropped = stopped.carried() - after.carried();
        var laid = dropped > 0 ? laying(unloaded(after, dropped), place) : after;
        return laid.on(Optional.of(trip.claiming(Math.min(trip.claimed(), laid.carried())).then(Trip.Leg.SETTING_DOWN, now + stopTicks)));
    }

    /** A dragon with nothing to do and nothing carried: a trip to the stop that needs most, from the fullest that can give it something; or none. */
    private VoidDragonRecord plan(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        var owned = stops(dragon.owner());
        var needing = owned.stream()
                .filter(stop -> need(dragons, stop, dragon) > 0)
                .sorted(Comparator.comparingLong((Stop stop) -> need(dragons, stop, dragon)).reversed())
                .toList();
        for (var destination : needing) {
            var source = owned.stream()
                    .filter(stop -> deliverable(dragons, stop, destination, dragon) > 0)
                    .max(cargo(dragon).sources(destination));
            if (source.isPresent()) {
                long claimed = Math.min(loadOf(dragon), Math.min(need(dragons, destination, dragon), deliverable(dragons, source.get(), destination, dragon)));
                var from = source.get().nearest(dragon.rest(), dive);
                return setOff(dragon, Trip.planned(Optional.of(from), destination.nearest(from, dive), claimed, false,
                        Trip.Leg.TO_SOURCE), now);
            }
        }
        return dragon;
    }

    /** Arrived at the source: it loads what the destination wants ({@link Cargo#take}), or gives up the trip with nothing to load. */
    private VoidDragonRecord load(VoidDragons dragons, VoidDragonRecord dragon, Trip trip, long now) {
        var source = trip.source().orElseThrow();
        var stopped = dragon.resting(source);
        var from = stop(dragon.owner(), source);
        var to = stop(dragon.owner(), trip.destination());
        if (from.isEmpty() || to.isEmpty()) {
            return stopped.on(Optional.empty());
        }
        long most = Math.min(loadOf(dragon), Math.min(need(dragons, to.get(), dragon), deliverable(dragons, from.get(), to.get(), dragon)));
        var loaded = cargo(dragon).take(from.get(), source, to.get(), most);
        if (loaded.isEmpty()) {
            return stopped.on(Optional.empty());
        }
        return laying(stopped.carrying(loaded), source).on(Optional.of(trip.claiming(loaded.amount()).then(Trip.Leg.LOADING, now + stopTicks)));
    }

    /**
     * Arrived at the destination: it unloads, highest grade first, what the Well is still short of in
     * grades worth something there, or for a load stowed, as much as fits. With nothing unloaded, the
     * trip ends and the load is sent on from here.
     */
    private VoidDragonRecord unload(VoidDragons dragons, VoidDragonRecord dragon, Trip trip, long now) {
        var stopped = dragon.resting(trip.destination());
        var after = stop(dragon.owner(), trip.destination())
                .map(stop -> stopped.carrying(cargo(dragon).put(stopped.load(), trip.destination(), stop, trip.stow() ? cargo(dragon).room(stop) : need(dragons, stop, dragon), trip.stow())))
                .orElse(stopped);
        long put = stopped.carried() - after.carried();
        if (put == 0) {
            return stopped.on(Optional.empty());
        }
        return laying(unloaded(after, put), trip.destination()).on(Optional.of(trip.claiming(0).then(Trip.Leg.UNLOADING, now + stopTicks)));
    }

    /** @return {@code dragon} having unloaded {@code motes}, which it has carried over its life, and as an adult toward its eggs */
    private VoidDragonRecord unloaded(VoidDragonRecord dragon, long motes) {
        return dragon.unloaded(motes, stages.towardEggs(dragon.cargo(), motes));
    }

    /** @return the most {@code dragon} loads now: the base load times its stage's factor */
    private long loadOf(VoidDragonRecord dragon) {
        return stages.load(load, dragon.cargo());
    }

    /**
     * @return {@code dragon}, stopping at its owner's Well at {@code place}, having laid there an egg for
     *         every {@link #eggMotes} it has carried toward them, as many as the Well lays; it owes the rest
     */
    private VoidDragonRecord laying(VoidDragonRecord dragon, Place place) {
        long eggs = 0;
        while (eggs < dragon.towardEgg() / eggMotes && wells.lay(place)) {
            eggs++;
        }
        return dragon.laid(eggs * eggMotes);
    }

    /**
     * A dragon carrying a load and with no trip: to the owner's stop that needs most of what it
     * carries, else to the one with most room, to stow it; with room nowhere it holds the load.
     */
    private VoidDragonRecord send(VoidDragons dragons, VoidDragonRecord dragon, long now) {
        var owned = stops(dragon.owner());
        var needing = owned.stream()
                .filter(stop -> cargo(dragon).wants(stop, dragon.load()))
                .max(Comparator.comparingLong(stop -> need(dragons, stop, dragon)))
                .filter(stop -> need(dragons, stop, dragon) > 0);
        if (needing.isPresent()) {
            long claimed = Math.min(dragon.carried(), need(dragons, needing.get(), dragon));
            return setOff(dragon, Trip.planned(Optional.empty(), needing.get().nearest(dragon.rest(), dive), claimed, false,
                    Trip.Leg.TO_DESTINATION), now);
        }
        var roomiest = owned.stream()
                .max(Comparator.comparingLong(stop -> room(dragons, stop, dragon)))
                .filter(stop -> room(dragons, stop, dragon) > 0);
        if (roomiest.isPresent()) {
            long claimed = Math.min(dragon.carried(), room(dragons, roomiest.get(), dragon));
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
        return healed.on(Optional.of(trip.diving(now, dive.ticks(dragon.rest(), to) * VoidDragonRecord.WHOLE_SPEED, stages.speed(healed))));
    }

    /**
     * @return {@code dragon} on its dive on {@code trip} to {@code to}, counted on to game time {@code now}
     *         or to when it arrives, if sooner: the ground it covered at the speed it had, and the health
     *         it lost on the way
     */
    private VoidDragonRecord dived(VoidDragonRecord dragon, Trip trip, Place to, long now) {
        long divedTo = trip.divedTo(now, stages.speed(dragon));
        var weakened = dragon.weakened(harm.weakening(dragon.rest().dimension(), to.dimension(), trip.countedTo(), divedTo));
        return weakened.on(Optional.of(trip.covered(divedTo, stages.speed(dragon), stages.speed(weakened))));
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
        long owed = (long) healing(dragon, stop.get()) * harm.motesPerPoint();
        long paid = 0;
        for (var held : stop.get().motes().entrySet()) {
            if (paid == owed) {
                break;
            }
            paid += wells.take(place, held.getKey(), Math.min(owed - paid, held.getValue()));
        }
        return dragon.healed(Math.toIntExact(paid / harm.motesPerPoint()));
    }

    /** @return the points of health {@code dragon} heals from the Wells of {@code stop}, where it is, as far as their motes go */
    private int healing(VoidDragonRecord dragon, Stop stop) {
        return (int) Math.min(VoidDragonRecord.FULL_HEALTH - dragon.health(), stop.total() / harm.motesPerPoint());
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

    /** @return what {@code dragon}'s Line carries */
    private Cargo cargo(VoidDragonRecord dragon) {
        return cargoes.get(dragon.line());
    }

    /**
     * @return how much {@code from} offers {@code to} ({@link Cargo#offer}) of what {@code dragon} carries,
     *         less what other dragons of its Line, whoever's, are on their way to load on its network, which
     *         is spoken for
     */
    private long deliverable(VoidDragons dragons, Stop from, Stop to, VoidDragonRecord dragon) {
        long spokenFor = claimed(dragons, dragon,
                trip -> trip.leg() == Trip.Leg.TO_SOURCE && trip.source().filter(from.network()::contains).isPresent());
        return Math.max(0, cargo(dragon).offer(from, to) - spokenFor);
    }

    /** @return how much {@code stop} is short of ({@link Cargo#shortfall}) of what {@code dragon} carries, less what other dragons of its Line have claimed for its network */
    private long need(VoidDragons dragons, Stop stop, VoidDragonRecord dragon) {
        return Math.max(0, cargo(dragon).shortfall(stop) - claimedFor(dragons, stop, dragon));
    }

    /** @return how much more of what {@code dragon} carries {@code stop} takes ({@link Cargo#room}), less what other dragons of its Line have claimed for its network */
    private long room(VoidDragons dragons, Stop stop, VoidDragonRecord dragon) {
        return Math.max(0, cargo(dragon).room(stop) - claimedFor(dragons, stop, dragon));
    }

    /** @return what is on its way to any Well on {@code stop}'s network on the trips of every dragon of {@code dragon}'s Line but it, whoever's */
    private static long claimedFor(VoidDragons dragons, Stop stop, VoidDragonRecord dragon) {
        return claimed(dragons, dragon, trip -> stop.network().contains(trip.destination()));
    }

    /**
     * @return what is claimed on the trips that are {@code such} of every dragon of {@code dragon}'s Line but
     *         it, whoever's: claims count in their Line's unit, so another Line's never bind it
     */
    private static long claimed(VoidDragons dragons, VoidDragonRecord dragon, Predicate<Trip> such) {
        return dragons.all().stream()
                .filter(other -> !other.id().equals(dragon.id()) && other.line() == dragon.line())
                .flatMap(other -> other.trip().stream())
                .filter(such)
                .mapToLong(Trip::claimed)
                .sum();
    }
}
