// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * The Unmaking Chamber's yield, the chance each ingredient item comes back: it starts at
 * {@code start} and rises with the void energy the Chamber has spent on breakdowns in its life, each
 * {@code halving} spent closing half the gap to {@code cap}, which it never reaches.
 *
 * @param start the yield of a Chamber that has spent nothing, from 0 to {@code cap}
 * @param cap what the yield rises toward, at most 1
 * @param halving the void energy that closes half the gap, above 0
 */
public record UnmakingYield(double start, double cap, long halving) {

    public UnmakingYield {
        if (!(start >= 0 && start <= cap && cap <= 1)) {
            throw new IllegalArgumentException("a yield starts from 0 at most at its cap, at most 1: " + start + ", " + cap);
        }
        if (halving < 1) {
            throw new IllegalArgumentException("a yield's halving is above 0: " + halving);
        }
    }

    /** @return the yield of a Chamber that has spent {@code spent} void energy on breakdowns */
    public double at(long spent) {
        return cap - (cap - start) * Math.pow(0.5, (double) spent / halving);
    }

    /**
     * Rolls each ingredient item {@code breakdown} may return alone, in order.
     *
     * @param random each roll, from 0 to below 1; an item comes back when its roll is below {@code yield}
     * @return what comes back, by item, leaving out what none of came back
     */
    public static <I> Map<I, Integer> roll(Breakdown<I> breakdown, double yield, DoubleSupplier random) {
        var back = new LinkedHashMap<I, Integer>();
        breakdown.returns().forEach((item, amount) -> {
            for (int i = 0; i < amount; i++) {
                if (random.getAsDouble() < yield) {
                    back.merge(item, 1, Integer::sum);
                }
            }
        });
        return back;
    }
}
