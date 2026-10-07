// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.util.function.IntPredicate;

/**
 * The open void check, as plain logic with no Minecraft types: a position is open to the void when
 * nothing lies beneath it, down to the bottom of the world. The column is read through a predicate
 * over heights, so JUnit covers it without loading the End.
 */
public final class OpenVoid {

    /** How many steps of Void Pressure the open void adds where the dimension's harvest kind is End. */
    public static final int EXTRA_PRESSURE = 1;

    private OpenVoid() {
    }

    /**
     * @param minY the lowest height of the world, inclusive
     * @param y the height of the position; its own block and everything above it are not looked at
     * @param hasBlockAt whether there is a block at a given height in the position's column
     * @return whether no height from {@code minY} up to just below {@code y} holds a block
     */
    public static boolean isOpen(int minY, int y, IntPredicate hasBlockAt) {
        for (int below = y - 1; below >= minY; below--) {
            if (hasBlockAt.test(below)) {
                return false;
            }
        }
        return true;
    }
}
