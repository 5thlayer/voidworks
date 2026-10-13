// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import net.minecraft.core.Vec3i;

/**
 * How long a Void Crucible takes over one result: the Rung's void energy cost times the ticks per
 * void energy, halved for each step of Void Pressure above the End's and doubled for each below it,
 * and multiplied by the working Crucibles sharing its rate, itself included: those {@link #near} it.
 * Plain logic, reading no game state.
 *
 * @param ticksPerEnergy the ticks a void energy of cost takes at the End's pressure, alone
 */
public record CrucibleTime(int ticksPerEnergy) {

    /**
     * The Void Pressure the ticks per void energy hold at: the End's shipped pressure, which stays the
     * reference when a datapack moves the End's, so the config means the same in any pack.
     */
    public static final int END_PRESSURE = 3;

    /**
     * @param cost     the Rung's void energy cost
     * @param pressure the Void Pressure where the Crucible stands
     * @param sharing  the working Crucibles sharing its rate, itself included
     * @return the ticks one result takes, never less than one
     */
    public double ticks(long cost, int pressure, int sharing) {
        return Math.max(1, Math.scalb((double) cost * ticksPerEnergy * sharing, END_PRESSURE - pressure));
    }

    /**
     * @return whether a Crucible {@code offset} from another is near enough to share its rate: within
     *         {@code radius} blocks on every axis, a cube, so a player counts it out on a grid
     */
    public static boolean near(Vec3i offset, int radius) {
        return Math.abs(offset.getX()) <= radius && Math.abs(offset.getY()) <= radius && Math.abs(offset.getZ()) <= radius;
    }
}
