// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A breakdown of an item into its crafting recipe's ingredients: how many of the item it takes, and
 * every ingredient item that may come back for them, each by chance at the Unmaking Chamber's yield.
 *
 * @param <I> what names an item: the game's item, or a plain id in a test
 * @param count how many of the item a breakdown takes, the recipe's result count, at least 1
 * @param returns each ingredient that may come back, in its recipe's order, with how many, each at
 *        least 1; none for a pack's entry that bars the item
 */
public record Breakdown<I>(int count, Map<I, Integer> returns) {

    /** The void energy a breakdown spends per ingredient item that may come back. */
    public static final long COST_PER_ITEM = 100;

    public Breakdown {
        if (count < 1) {
            throw new IllegalArgumentException("a breakdown takes at least one item: " + count);
        }
        returns.forEach((item, amount) -> {
            if (amount < 1) {
                throw new IllegalArgumentException("a breakdown returns at least one " + item + ", not " + amount);
            }
        });
        returns = Collections.unmodifiableMap(new LinkedHashMap<>(returns));
    }

    /** @return how many ingredient items may come back, all of them together */
    public int items() {
        return returns.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** @return the void energy a breakdown spends: {@link #COST_PER_ITEM} per ingredient item that may come back */
    public long cost() {
        return COST_PER_ITEM * items();
    }
}
