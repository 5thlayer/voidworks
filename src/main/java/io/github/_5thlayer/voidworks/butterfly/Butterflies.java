// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.butterfly;

import java.util.ArrayList;
import java.util.List;

/**
 * How harvested motes are seen: like XP orbs, a butterfly stands for 1, 8 or 64 motes by its size,
 * and the butterflies of one harvest add up to its exact count.
 */
public final class Butterflies {

    /** The motes one butterfly stands for, largest first. */
    public static final List<Integer> SIZES = List.of(64, 8, 1);

    private Butterflies() {
    }

    /**
     * @return the butterflies {@code count} motes are seen as, largest first: as few as add up to
     *         {@code count} exactly
     * @throws IllegalArgumentException when {@code count} is negative
     */
    public static List<Integer> sizes(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("no harvest has " + count + " motes");
        }
        var sizes = new ArrayList<Integer>();
        int left = count;
        // Each size divides the next larger one, so taking the largest first is the fewest.
        for (int size : SIZES) {
            for (; left >= size; left -= size) {
                sizes.add(size);
            }
        }
        return List.copyOf(sizes);
    }
}
