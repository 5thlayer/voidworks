// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * The trip a Void Dragon has claimed: to a source Well to load, and on to the destination to unload.
 * Its claim counts against the destination's need, so several dragons share the work without
 * overshooting its target ({@link Routing}). A stop may be added to it, at a Well its owner calls it
 * to or its rider picks, after which it goes on to the destination with what it still claims, if
 * anything.
 *
 * @param source where it loads; empty once it has loaded, or for a trip that carries a load it holds already
 * @param destination where it unloads
 * @param claimed the motes on their way to the destination: planned until it loads, then its load, and none once unloaded
 * @param stow whether it carries a load nowhere needs to the Well with most room, where it unloads as much as fits
 * @param leg what it is doing now
 * @param until the game time the leg ends; on a dive, when it arrives if it keeps its speed now
 * @param countedTo on a dive, the game time up to which the ground it has covered and the health it has lost are counted
 * @param toCover on a dive, the ground still to cover, in ticks at a whole dragon's speed times {@link VoidDragonRecord#WHOLE_SPEED}
 * @param added the Well of a stop added to it, which its owner called it to or its rider picked, while it is on its way there or stopped there
 */
public record Trip(Optional<Place> source, Place destination, long claimed, boolean stow, Leg leg, long until, long countedTo, long toCover,
                   Optional<Place> added) {

    public static final Codec<Trip> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Place.CODEC.optionalFieldOf("source").forGetter(Trip::source),
            Place.CODEC.fieldOf("destination").forGetter(Trip::destination),
            Codec.LONG.fieldOf("claimed").forGetter(Trip::claimed),
            Codec.BOOL.optionalFieldOf("stow", false).forGetter(Trip::stow),
            Leg.CODEC.fieldOf("leg").forGetter(Trip::leg),
            Codec.LONG.fieldOf("until").forGetter(Trip::until),
            // A dive saved before dragons weakened on it arrives when it was to, weakening for none of it.
            Codec.LONG.optionalFieldOf("counted_to").forGetter(trip -> Optional.of(trip.countedTo())),
            Codec.LONG.optionalFieldOf("to_cover", 0L).forGetter(Trip::toCover),
            Place.CODEC.optionalFieldOf("added").forGetter(Trip::added)
    ).apply(instance, (source, destination, claimed, stow, leg, until, countedTo, toCover, added) ->
            new Trip(source, destination, claimed, stow, leg, until, countedTo.orElse(until), toCover, added)));

    /** A trip with no stop added to it. */
    public Trip(Optional<Place> source, Place destination, long claimed, boolean stow, Leg leg, long until, long countedTo, long toCover) {
        this(source, destination, claimed, stow, leg, until, countedTo, toCover, Optional.empty());
    }

    /** @return a trip planned on {@code leg}, a dive not yet set off, which {@link Routing} times as it sets off */
    static Trip planned(Optional<Place> source, Place destination, long claimed, boolean stow, Leg leg) {
        return new Trip(source, destination, claimed, stow, leg, 0, 0, 0);
    }

    /** @return this trip on its next {@code leg}, until {@code until} */
    Trip then(Leg leg, long until) {
        return new Trip(source, destination, claimed, stow, leg, until, countedTo, toCover, added);
    }

    /** @return this trip with a stop added at the Well at {@code place}, on its way there on {@code leg}, a dive not yet set off */
    Trip adding(Leg leg, Place place) {
        return new Trip(source, destination, claimed, stow, leg, 0, 0, 0, Optional.of(place));
    }

    /** @return this trip going on to its destination after a stop added to it, a dive not yet set off */
    Trip goingOn() {
        return new Trip(source, destination, claimed, stow, Leg.TO_DESTINATION, 0, 0, 0, Optional.empty());
    }

    /** @return this trip with {@code claimed} motes on their way */
    Trip claiming(long claimed) {
        return new Trip(source, destination, claimed, stow, leg, until, countedTo, toCover, added);
    }

    /** @return this trip's dive counted up to game time {@code now}, {@code toCover} still to cover at {@code speed} */
    Trip diving(long now, long toCover, int speed) {
        return new Trip(source, destination, claimed, stow, leg, now + Math.ceilDiv(toCover, speed), now, toCover, added);
    }

    /** @return the game time a dragon diving at {@code speed} has dived to by {@code now}: then, or when it arrives, if sooner */
    long divedTo(long now, int speed) {
        return Math.min(now, countedTo + Math.ceilDiv(toCover, speed));
    }

    /** @return this trip's dive counted on to game time {@code to}, covered at {@code speed}, the rest to cover at {@code speedAfter} */
    Trip covered(long to, int speed, int speedAfter) {
        return diving(to, Math.max(0, toCover - (to - countedTo) * speed), speedAfter);
    }

    /** @return where the dragon is diving to on this leg; empty while it is stopped */
    Optional<Place> divingTo() {
        return switch (leg) {
            case TO_SOURCE -> source;
            case TO_DESTINATION -> Optional.of(destination);
            case TO_CALL, TO_PICKED -> added;
            case LOADING, UNLOADING, WAITING, SETTING_DOWN -> Optional.empty();
        };
    }

    /** @return whether it is on its way to its owner's call at the Well at {@code place}, or waiting there for them */
    boolean answering(Place place) {
        return (leg == Leg.TO_CALL || leg == Leg.WAITING) && added.equals(Optional.of(place));
    }

    /** What a dragon on a trip is doing. */
    public enum Leg implements StringRepresentable {

        /** Diving to the source. */
        TO_SOURCE("to_source"),
        /** Stopped at the source, loading. */
        LOADING("loading"),
        /** Diving to the destination with its load. */
        TO_DESTINATION("to_destination"),
        /** Stopped at the destination, unloading. */
        UNLOADING("unloading"),
        /** Diving to the Well its owner calls it to, from the stop it has finished. */
        TO_CALL("to_call"),
        /** Stopped at the Well its owner called it to, waiting while they stay on its rim. */
        WAITING("waiting"),
        /** Diving to the Well its rider picked, carrying them. */
        TO_PICKED("to_picked"),
        /** Stopped at the Well its rider picked, having set them down, unloading what the Well is short of. */
        SETTING_DOWN("setting_down");

        static final Codec<Leg> CODEC = StringRepresentable.fromEnum(Leg::values);

        private final String name;

        Leg(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
