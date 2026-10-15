// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Where a Void Dragon's presence sits while its dragon rests at a Well: astride the Well's rim, over
 * the middle of its shaft, lengthwise along the wall its core is set in, so neither its head nor its
 * tail covers the core's face.
 *
 * @param pos where it stands, on top of the rim
 * @param along one way along the core's wall; its head points this way or the other
 */
record Seat(BlockPos pos, Direction along) {

    /** @return the seat on the Well whose core is at {@code core}, facing {@code facing} out of its wall */
    static Seat of(BlockPos core, Direction facing) {
        return new Seat(WellStructure.shaft(core, facing).above(DragonPresences.MOUTH), facing.getClockWise());
    }

    /** @return the way along the core's wall its head points, of the two, the nearer to a mob's heading of {@code yaw} degrees */
    Direction headWay(float yaw) {
        float off = Math.abs(Mth.wrapDegrees(yaw - along.toYRot()));
        return off <= 90.0F ? along : along.getOpposite();
    }
}
