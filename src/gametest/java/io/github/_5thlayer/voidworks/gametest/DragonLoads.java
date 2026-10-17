// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Optional;

import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Load;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.dragon.VoidDragons;

/** A grown Void Dragon's record set to carry a load, resting or on a trip, for what its presence shows of it. */
final class DragonLoads {

    private DragonLoads() {
    }

    /** {@code dragon}, of {@code line}, carries {@code load}, on {@code trip} or resting. */
    static void carrying(VoidDragons dragons, VoidDragonRecord dragon, Line line, Load load, Optional<Trip> trip) {
        dragons.update(new VoidDragonRecord(dragon.id(), dragon.owner(), line, dragon.health(), dragon.rest(), load, trip, 0, 0));
    }

    /** @return a trip from {@code here} carrying {@code load}, on {@code leg} until {@code until} */
    static Trip trip(Place here, Load load, Trip.Leg leg, long until) {
        return new Trip(Optional.empty(), here, load.amount(), false, leg, until, 0, 0, Optional.empty(), load.items());
    }
}
