// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import net.minecraft.util.Mth;

/**
 * How far a Void Dragon's presence has settled onto its seat, 0 on the wing and 1 sitting, which its
 * shadow narrows by: evenly by how near its seat it is as it lands or takes off, but never faster than
 * {@link #MOST_A_TICK} a tick, so it eases even for a landing begun close to the seat, or one given up.
 */
final class Settling {

    /**
     * How near its seat, in blocks, a landing or taking-off presence is settling onto it rather than on
     * the wing: a block short of how high a take-off climbs, so it has risen wholly before it flies on.
     */
    static final double NEAR_SEAT = DragonPresences.RISE - 1.0;

    /**
     * The most its settling changes in a tick: half a second at the least to settle wholly or rise
     * wholly, as fast as a take-off climbs the blocks {@link #NEAR_SEAT}, so it keeps up with one.
     */
    static final float MOST_A_TICK = 0.1F;

    private Settling() {
    }

    /**
     * @return how far it has settled a tick on from {@code settled}, toward {@link #toward} as
     *         {@code perch} and {@code fromSeat} say, by at most {@link #MOST_A_TICK}
     */
    static float settle(float settled, VoidDragon.Perch perch, double fromSeat) {
        return Mth.approach(settled, toward(perch, fromSeat), MOST_A_TICK);
    }

    /**
     * @return how far it is settling toward, {@code perch} and {@code fromSeat} blocks from its seat:
     *         wholly sitting, not at all on the wing, and evenly by how near it is within
     *         {@link #NEAR_SEAT} of its seat as it lands or takes off
     */
    static float toward(VoidDragon.Perch perch, double fromSeat) {
        return switch (perch) {
            case FLYING -> 0.0F;
            case SITTING -> 1.0F;
            case LANDING, TAKING_OFF -> 1.0F - (float) Mth.clamp(fromSeat / NEAR_SEAT, 0.0, 1.0);
        };
    }
}
