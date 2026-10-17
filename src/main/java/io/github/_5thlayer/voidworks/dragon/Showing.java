// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

/**
 * What a Void Dragon's presence shows of its record at a moment, read from the record alone, so the
 * presence follows the record and never drives it (ADR 0005). Plain logic, tested with no game
 * running; {@link DragonPresences} shows it.
 */
public enum Showing {

    /** It dives through the void: a presence still shown dives away and is gone, and none shows in its place. */
    DIVING,
    /** It has just stopped at a Well, and is seen emerging from its shaft. */
    EMERGING,
    /** It is stopped at a Well, loading or unloading, waiting for its owner or setting its rider down, and flies close about it. */
    WORKING,
    /** It has nothing to do: at a Well, it sits astride its rim, room allowing, else flies about where it rests. */
    RESTING;

    /** How long after a dragon stops at a Well a presence shown there emerges, rather than being there already. */
    public static final int EMERGE_TICKS = 40;

    /**
     * @param now the game time {@link Routing} runs on
     * @param stopTicks how long a stop lasts, so when it began is read from when it ends
     * @return what {@code dragon}'s presence shows at game time {@code now}
     */
    public static Showing of(VoidDragonRecord dragon, long now, int stopTicks) {
        if (dragon.trip().isEmpty()) {
            return RESTING;
        }
        var trip = dragon.trip().get();
        return switch (trip.leg()) {
            case TO_SOURCE, TO_DESTINATION, TO_CALL, TO_PICKED -> DIVING;
            case LOADING, UNLOADING, WAITING, SETTING_DOWN -> now - trip.began(stopTicks) < EMERGE_TICKS ? EMERGING : WORKING;
        };
    }
}
