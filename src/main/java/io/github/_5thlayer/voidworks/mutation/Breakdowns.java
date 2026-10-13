// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every crafting recipe, indexed by the item it makes and read in reverse: the breakdown an item has,
 * as plain logic with no Minecraft types. An item exactly one recipe makes breaks down into that
 * recipe's ingredients, taking its result count; one that two or more make, or none, has none, and so
 * does one whose recipe has no fixed ingredients. An ingredient that leaves a remainder in the grid
 * never comes back, so a recipe of nothing else gives no breakdown. A pack's entry for an item
 * decides over all of it: it fills in a breakdown, or, with no ingredients, bars the item.
 *
 * @param <I> what names an item: the game's item, or a plain id in a test
 */
public final class Breakdowns<I> {

    /**
     * A crafting recipe as a breakdown reads it.
     *
     * @param result the item it makes
     * @param count how many it makes
     * @param ingredients its ingredients, a slot each and empty slots left out; empty when it has no
     *        fixed ingredients or its result carries data components, so it breaks nothing down
     */
    public record Recipe<I>(I result, int count, Optional<List<Ingredient<I>>> ingredients) {
    }

    /**
     * An ingredient slot as a breakdown reads it.
     *
     * @param first the item it gives back, the first it accepts in its own order
     * @param remains whether it leaves a remainder in the grid, such as a milk bucket, so never comes back
     */
    public record Ingredient<I>(I first, boolean remains) {
    }

    private static final Breakdowns<?> EMPTY = new Breakdowns<>(Map.of());

    private final Map<I, Optional<Breakdown<I>>> byResult;

    private Breakdowns(Map<I, Optional<Breakdown<I>>> byResult) {
        this.byResult = byResult;
    }

    /** @return the index of no recipe: only a pack's entries give a breakdown */
    @SuppressWarnings("unchecked")
    public static <I> Breakdowns<I> empty() {
        return (Breakdowns<I>) EMPTY;
    }

    /** Indexes {@code recipes}, the crafting-table recipes, by the item each makes. */
    public static <I> Breakdowns<I> of(List<Recipe<I>> recipes) {
        var byResult = new LinkedHashMap<I, Optional<Breakdown<I>>>();
        for (var recipe : recipes) {
            // A second recipe for an item leaves it with none.
            byResult.merge(recipe.result(), reverse(recipe), (first, second) -> Optional.empty());
        }
        return new Breakdowns<>(byResult);
    }

    private static <I> Optional<Breakdown<I>> reverse(Recipe<I> recipe) {
        return recipe.ingredients().flatMap(ingredients -> {
            var returns = new LinkedHashMap<I, Integer>();
            ingredients.stream().filter(ingredient -> !ingredient.remains()).forEach(ingredient -> returns.merge(ingredient.first(), 1, Integer::sum));
            return returns.isEmpty() ? Optional.empty() : Optional.of(new Breakdown<>(recipe.count(), returns));
        });
    }

    /**
     * @param entry a pack's entry for {@code item}, empty for none
     * @return the breakdown {@code item} has: the entry's, unless it bars the item, or else its one recipe's
     */
    public Optional<Breakdown<I>> of(I item, Optional<Breakdown<I>> entry) {
        if (entry.isPresent()) {
            return entry.filter(breakdown -> !breakdown.returns().isEmpty());
        }
        return byResult.getOrDefault(item, Optional.empty());
    }
}
