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
 * overshooting its target ({@link Routing}).
 *
 * @param source where it loads; empty once it has loaded, or for a trip that carries a load it holds already
 * @param destination where it unloads
 * @param claimed the motes on their way to the destination: planned until it loads, then its load, and none once unloaded
 * @param stow whether it carries a load nowhere needs to the Well with most room, where it unloads as much as fits
 * @param leg what it is doing now
 * @param until the game time the leg ends
 */
public record Trip(Optional<Place> source, Place destination, long claimed, boolean stow, Leg leg, long until) {

    public static final Codec<Trip> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Place.CODEC.optionalFieldOf("source").forGetter(Trip::source),
            Place.CODEC.fieldOf("destination").forGetter(Trip::destination),
            Codec.LONG.fieldOf("claimed").forGetter(Trip::claimed),
            Codec.BOOL.optionalFieldOf("stow", false).forGetter(Trip::stow),
            Leg.CODEC.fieldOf("leg").forGetter(Trip::leg),
            Codec.LONG.fieldOf("until").forGetter(Trip::until)
    ).apply(instance, Trip::new));

    /** @return this trip on its next {@code leg}, until {@code until} */
    Trip then(Leg leg, long until) {
        return new Trip(source, destination, claimed, stow, leg, until);
    }

    /** @return this trip with {@code claimed} motes on their way */
    Trip claiming(long claimed) {
        return new Trip(source, destination, claimed, stow, leg, until);
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
        UNLOADING("unloading");

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
