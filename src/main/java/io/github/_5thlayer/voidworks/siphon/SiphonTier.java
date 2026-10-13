// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import java.util.List;

/**
 * A Void Siphon's tier, which sets how many motes it holds and how fast it charges or empties on
 * Voidstone. A Siphon climbs a tier at a smithing table, keeping its motes and its toggle: an echo
 * shard makes the Resonant Siphon, and dragon's breath, after the dragon, the Draconic one.
 */
public enum SiphonTier {
    BASE,
    RESONANT,
    DRACONIC;

    /** The shipped capacities by tier: even the top holds a small fraction of a Void Well. */
    public static final List<Integer> DEFAULT_CAPACITIES = List.of(256, 1024, 4096);
    /** The shipped charge and empty rates by tier, in motes each time. */
    public static final List<Integer> DEFAULT_RATES = List.of(8, 16, 32);

    /** @return this tier's entry of {@code perTier}, bottom tier first, or its last entry when it is shorter */
    public int of(List<? extends Integer> perTier) {
        return perTier.get(Math.min(ordinal(), perTier.size() - 1));
    }
}
