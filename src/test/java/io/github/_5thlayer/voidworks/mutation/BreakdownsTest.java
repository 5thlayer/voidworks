// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.voidworks.mutation.Breakdowns.Ingredient;
import io.github._5thlayer.voidworks.mutation.Breakdowns.Recipe;
import org.junit.jupiter.api.Test;

/** The breakdown an item has, read from the one crafting recipe that makes it, in reverse, or from a pack's entry. */
class BreakdownsTest {

    private static Ingredient<String> plain(String item) {
        return new Ingredient<>(item, false);
    }

    private static Recipe<String> recipe(String result, int count, List<Ingredient<String>> ingredients) {
        return new Recipe<>(result, count, Optional.of(ingredients));
    }

    private static final Recipe<String> IRON_BLOCK = recipe("iron_block", 1, List.of(
            plain("iron_ingot"), plain("iron_ingot"), plain("iron_ingot"),
            plain("iron_ingot"), plain("iron_ingot"), plain("iron_ingot"),
            plain("iron_ingot"), plain("iron_ingot"), plain("iron_ingot")));

    @Test
    void anItemOneRecipeMakesBreaksDownIntoItsIngredientsCounted() {
        var breakdowns = Breakdowns.of(List.of(IRON_BLOCK));
        assertEquals(Optional.of(new Breakdown<>(1, Map.of("iron_ingot", 9))), breakdowns.of("iron_block", Optional.empty()));
    }

    @Test
    void aRecipeMakingSeveralTakesThatManyPerBreakdown() {
        var torch = recipe("torch", 4, List.of(plain("coal"), plain("stick")));
        assertEquals(Optional.of(new Breakdown<>(4, Map.of("coal", 1, "stick", 1))), Breakdowns.of(List.of(torch)).of("torch", Optional.empty()));
    }

    @Test
    void theIngredientsComeBackInTheirRecipesOrder() {
        var lantern = recipe("lantern", 1, List.of(plain("iron_nugget"), plain("torch"), plain("iron_nugget")));
        var breakdown = Breakdowns.of(List.of(lantern)).of("lantern", Optional.empty()).orElseThrow();
        assertEquals(List.of("iron_nugget", "torch"), List.copyOf(breakdown.returns().keySet()));
    }

    @Test
    void anItemSeveralRecipesMakeHasNone() {
        var fromPlanks = recipe("stick", 4, List.of(plain("oak_planks"), plain("oak_planks")));
        var fromBamboo = recipe("stick", 1, List.of(plain("bamboo"), plain("bamboo")));
        assertEquals(Optional.empty(), Breakdowns.of(List.of(fromPlanks, fromBamboo)).of("stick", Optional.empty()));
    }

    @Test
    void anItemNoRecipeMakesHasNone() {
        assertEquals(Optional.empty(), Breakdowns.of(List.of(IRON_BLOCK)).of("diamond", Optional.empty()));
    }

    @Test
    void anItemWhoseRecipeHasNoFixedIngredientsHasNone() {
        var rocket = new Recipe<String>("firework_rocket", 3, Optional.empty());
        assertEquals(Optional.empty(), Breakdowns.of(List.of(rocket)).of("firework_rocket", Optional.empty()));
    }

    @Test
    void aRecipeWithNoFixedIngredientsStillMakesAnItemSeveralRecipesMake() {
        var fixed = recipe("banner", 1, List.of(plain("wool"), plain("stick")));
        var copy = new Recipe<String>("banner", 2, Optional.empty());
        assertEquals(Optional.empty(), Breakdowns.of(List.of(fixed, copy)).of("banner", Optional.empty()));
    }

    @Test
    void anIngredientThatLeavesARemainderNeverComesBack() {
        var cake = recipe("cake", 1, List.of(new Ingredient<>("milk_bucket", true), new Ingredient<>("milk_bucket", true), plain("sugar"), plain("egg")));
        assertEquals(Optional.of(new Breakdown<>(1, Map.of("sugar", 1, "egg", 1))), Breakdowns.of(List.of(cake)).of("cake", Optional.empty()));
    }

    @Test
    void aRecipeOfOnlyRemaindersGivesNoBreakdown() {
        var odd = recipe("odd", 1, List.of(new Ingredient<>("milk_bucket", true)));
        assertEquals(Optional.empty(), Breakdowns.of(List.of(odd)).of("odd", Optional.empty()));
    }

    @Test
    void aPackEntryFillsInForAnItemWithSeveralRecipesOrNone() {
        var fromPlanks = recipe("stick", 4, List.of(plain("oak_planks"), plain("oak_planks")));
        var fromBamboo = recipe("stick", 1, List.of(plain("bamboo"), plain("bamboo")));
        var breakdowns = Breakdowns.of(List.of(fromPlanks, fromBamboo));
        var entry = new Breakdown<>(4, Map.of("oak_planks", 2));
        assertEquals(Optional.of(entry), breakdowns.of("stick", Optional.of(entry)));
        var diamond = new Breakdown<>(1, Map.of("coal", 9));
        assertEquals(Optional.of(diamond), breakdowns.of("diamond", Optional.of(diamond)));
    }

    @Test
    void anEmptyPackEntryBarsAnItemWithARecipe() {
        assertEquals(Optional.empty(), Breakdowns.of(List.of(IRON_BLOCK)).of("iron_block", Optional.of(new Breakdown<>(1, Map.of()))));
    }

    @Test
    void aBreakdownCostsAHundredVoidEnergyPerIngredientItem() {
        assertEquals(900, new Breakdown<>(1, Map.of("iron_ingot", 9)).cost());
        assertEquals(300, new Breakdown<>(4, Map.of("coal", 1, "stick", 2)).cost());
    }

    @Test
    void aBreakdownTakesAtLeastOneItemAndReturnsNoneNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Breakdown<>(0, Map.of("coal", 1)));
        assertThrows(IllegalArgumentException.class, () -> new Breakdown<>(1, Map.of("coal", 0)));
    }
}
