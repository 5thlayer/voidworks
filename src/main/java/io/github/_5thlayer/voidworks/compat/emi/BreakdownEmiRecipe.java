// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.mutation.Breakdown;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * One breakdown as EMI draws it: the item × its count, an arrow, then each ingredient that may come
 * back as one stack, on a grid of three by three; beneath, the void energy it spends and the range
 * its yield runs over, from start to cap, with nothing on how it rises.
 */
final class BreakdownEmiRecipe extends BasicEmiRecipe {

    private static final int GREY = 0xFF808080;
    private static final int GRID_X = 48;

    private final Breakdown<Item> breakdown;

    BreakdownEmiRecipe(EmiRecipeCategory category, Item item, Breakdown<Item> breakdown) {
        super(category, id(item), GRID_X + 54, 78);
        this.breakdown = breakdown;
        inputs.add(EmiStack.of(item, breakdown.count()));
        breakdown.returns().forEach((ingredient, count) -> outputs.add(EmiStack.of(ingredient, count)));
    }

    /** Keyed by the item broken down: an item has one breakdown at most. */
    private static Identifier id(Item item) {
        var key = BuiltInRegistries.ITEM.getKey(item);
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "/unmaking/" + key.getNamespace() + "/" + key.getPath());
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 18);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 21, 19);
        for (int slot = 0; slot < 9; slot++) {
            var stack = slot < outputs.size() ? outputs.get(slot) : EmiStack.EMPTY;
            var widget = widgets.addSlot(stack, GRID_X + slot % 3 * 18, slot / 3 * 18);
            if (slot < outputs.size()) {
                widget.recipeContext(this);
            }
        }
        widgets.addText(Component.translatable("voidworks.emi.cost", breakdown.cost()), 0, 57, GREY, false);
        // The server's config, which the client holds once it has joined.
        if (VoidworksConfig.loaded()) {
            var yield = VoidworksConfig.unmakingYield();
            widgets.addText(Component.translatable("voidworks.emi.yield", percent(yield.start()), percent(yield.cap())), 0, 68, GREY, false);
        }
    }

    private static long percent(double fraction) {
        return Math.round(fraction * 100);
    }
}
