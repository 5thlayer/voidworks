// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.taking;

/**
 * How far the void has taken a block, by the time since it started: through {@link #STAGES} stages
 * over {@link #DURATION} ticks, evenly, none reached as it starts and every one by its end.
 */
public final class TakingStages {

    /** How long the void takes over a block, in ticks: a Displacer's harvest interval, so its growth reads as a wave. */
    public static final int DURATION = 20;

    /** The stages it goes through, one mask each. */
    public static final int STAGES = 12;

    private TakingStages() {
    }

    /** @return how many stages the void has reached {@code elapsed} ticks after it started, from 0 to {@link #STAGES} */
    public static int reached(float elapsed) {
        return Math.clamp((int) Math.floor(elapsed * STAGES / DURATION), 0, STAGES);
    }

    /** @return whether the void has taken the whole block {@code elapsed} ticks after it started */
    public static boolean over(float elapsed) {
        return elapsed >= DURATION;
    }
}
