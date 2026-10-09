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

    /** A butterfly size: the motes it stands for and its scale, in blocks; 1, 8 and 64 read small, middling and large. */
    private record Size(int motes, float scale) {
    }

    /** Every size, largest first: the one place a size is defined. */
    private static final List<Size> TABLE = List.of(new Size(64, 0.5F), new Size(8, 0.3F), new Size(1, 0.17F));

    /** The motes one butterfly stands for, largest first. */
    public static final List<Integer> SIZES = TABLE.stream().map(Size::motes).toList();

    private Butterflies() {
    }

    /**
     * @return the scale, in blocks, of the butterfly that stands for {@code motes}
     * @throws IllegalArgumentException when no butterfly stands for {@code motes}
     */
    public static float scale(int motes) {
        return TABLE.stream().filter(size -> size.motes() == motes).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no butterfly stands for " + motes + " motes"))
                .scale();
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
