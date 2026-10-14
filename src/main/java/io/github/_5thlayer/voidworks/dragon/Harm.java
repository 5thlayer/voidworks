// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.function.ToIntFunction;

import net.minecraft.resources.Identifier;

/**
 * How a Void Dragon weakens and heals. Diving between Wells, it loses health by how far the Void
 * Pressure at the worse of the dive's two ends is below the End's, so a trip to or from the Nether
 * costs it most and one between End Wells nothing; stopped or resting it loses none. Hurt, it flies
 * more slowly ({@link VoidDragonRecord#speed}), slowing during a dive as it weakens. Before it dives
 * from a Well it heals from that Well's store, a point of health for {@code motesPerPoint} motes
 * ({@link Routing}).
 *
 * @param deficit how many steps each dimension's Void Pressure is below the End's, by its id; none in the End
 * @param ticks the game time in which it loses a point of health for each step of that deficit
 * @param motesPerPoint the motes a point of health heals for
 */
public record Harm(ToIntFunction<Identifier> deficit, int ticks, long motesPerPoint) {

    /**
     * @return the health a dragon diving from a Well in {@code leaving} to one in {@code reaching}, from
     *         game time {@code from} to {@code to}, loses: the worse of their deficits each time the
     *         clock passes a whole multiple of {@link #ticks}
     */
    public int weakening(Identifier leaving, Identifier reaching, long from, long to) {
        int worse = Math.max(deficit.applyAsInt(leaving), deficit.applyAsInt(reaching));
        return Math.toIntExact(worse * (Math.floorDiv(to, ticks) - Math.floorDiv(from, ticks)));
    }
}
