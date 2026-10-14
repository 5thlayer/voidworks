// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What a Void Dragon's presence shows of its record: nothing while it dives, the dragon emerging
 * just after it stops at a Well, then at work there while it loads or unloads, and resting with
 * nothing to do.
 */
class ShowingTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Place HERE = new Place(OVERWORLD, new BlockPos(0, 64, 0));
    private static final Place THERE = new Place(OVERWORLD, new BlockPos(640, 64, 0));
    private static final int STOP_TICKS = 100;
    private static final long NOW = 10_000;

    @Test
    void withNoTripItRests() {
        assertEquals(Showing.RESTING, Showing.of(dragon(Optional.empty()), NOW, STOP_TICKS));
    }

    @ParameterizedTest
    @EnumSource(value = Trip.Leg.class, names = {"TO_SOURCE", "TO_DESTINATION"})
    void whileItDivesNothingShows(Trip.Leg leg) {
        assertEquals(Showing.DIVING, Showing.of(dragon(trip(leg, NOW + 50)), NOW, STOP_TICKS));
    }

    @ParameterizedTest
    @EnumSource(value = Trip.Leg.class, names = {"LOADING", "UNLOADING"})
    void justStoppedAtAWellItEmergesThere(Trip.Leg leg) {
        // Stopped 20 ticks ago: its stop ends STOP_TICKS after it began.
        assertEquals(Showing.EMERGING, Showing.of(dragon(trip(leg, NOW - 20 + STOP_TICKS)), NOW, STOP_TICKS));
    }

    @ParameterizedTest
    @EnumSource(value = Trip.Leg.class, names = {"LOADING", "UNLOADING"})
    void stoppedAWhileItIsAtWork(Trip.Leg leg) {
        long since = Showing.EMERGE_TICKS;
        assertEquals(Showing.WORKING, Showing.of(dragon(trip(leg, NOW - since + STOP_TICKS)), NOW, STOP_TICKS));
    }

    private static VoidDragonRecord dragon(Optional<Trip> trip) {
        return new VoidDragonRecord(UUID.randomUUID(), UUID.randomUUID(), VoidDragonRecord.FULL_HEALTH, HERE, new TreeMap<>(), trip);
    }

    private static Optional<Trip> trip(Trip.Leg leg, long until) {
        return Optional.of(new Trip(Optional.of(HERE), THERE, 100, false, leg, until, until, 0));
    }
}
