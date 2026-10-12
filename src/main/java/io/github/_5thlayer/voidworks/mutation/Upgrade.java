// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

/**
 * An upgrade from one Rung of a Family to the next: what it lands on and what it takes.
 *
 * @param <I> what names an item: the game's item, or a plain id in a test
 * @param result the one item the upgrade gives
 * @param count how many items it takes per result, at least 1
 * @param cost the void energy a Mutation Chamber spends per result, not negative
 * @param chance the odds an upgrade succeeds, above 0 and at most 1
 */
public record Upgrade<I>(I result, int count, long cost, double chance) {

    public Upgrade {
        if (count < 1) {
            throw new IllegalArgumentException("an upgrade takes at least one item: " + count);
        }
        if (cost < 0) {
            throw new IllegalArgumentException("an upgrade cannot cost negative void energy: " + cost);
        }
        if (!(chance > 0 && chance <= 1)) {
            throw new IllegalArgumentException("an upgrade's chance is above 0 and at most 1: " + chance);
        }
    }
}
