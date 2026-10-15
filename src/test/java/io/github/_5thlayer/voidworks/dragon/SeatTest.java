// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * Where a Void Dragon resting at a Well sits: astride its rim over the shaft, lengthwise along the
 * wall its core is set in, so neither head nor tail covers the core, its head whichever way along
 * that wall is nearer its heading as it comes down.
 */
class SeatTest {

    private static final BlockPos CORE = new BlockPos(10, 64, 20);

    @Test
    void itSitsOnTheRimOverTheMiddleOfTheShaft() {
        // A north-facing core is in the north wall, so the shaft's middle is two blocks south of it,
        // and the rim's top is three above that.
        assertEquals(new BlockPos(10, 67, 22), Seat.of(CORE, Direction.NORTH).pos());
    }

    @Test
    void itLiesAlongTheCoresWall() {
        assertEquals(Direction.Axis.X, Seat.of(CORE, Direction.NORTH).headWay(0.0F).getAxis());
        assertEquals(Direction.Axis.X, Seat.of(CORE, Direction.SOUTH).headWay(0.0F).getAxis());
        assertEquals(Direction.Axis.Z, Seat.of(CORE, Direction.EAST).headWay(90.0F).getAxis());
    }

    @Test
    void itsHeadPointsTheWayAlongTheWallNearerItsHeading() {
        var seat = Seat.of(CORE, Direction.NORTH);
        // A mob's heading of 90 degrees faces west, -90 east.
        assertEquals(Direction.WEST, seat.headWay(60.0F));
        assertEquals(Direction.EAST, seat.headWay(-120.0F));
        assertEquals(Direction.EAST, seat.headWay(250.0F));
    }
}
