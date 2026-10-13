// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.mutation.KnownBreakdowns;
import net.minecraft.resources.Identifier;

/**
 * The Unmaking Chamber's breakdowns in EMI (#52): each item with one, × its count, into the
 * ingredients that may come back, with the void energy it spends and the range its yield runs over.
 * An item with no breakdown, or one a pack bars, shows none.
 *
 * <p>The breakdowns are the ones the server sent ({@link KnownBreakdowns}), ahead of the recipes
 * that make EMI reload.
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing outside this
 * package names an EMI type, so the Module plays without it.
 */
@EmiEntrypoint
public final class BreakdownEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        var chamber = EmiStack.of(VoidworksBlocks.UNMAKING_CHAMBER_ITEM.get());
        var category = new EmiRecipeCategory(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "unmaking_chamber"), chamber);
        registry.addCategory(category);
        registry.addWorkstation(category, chamber);
        KnownBreakdowns.all().forEach((item, breakdown) -> registry.addRecipe(new BreakdownEmiRecipe(category, item, breakdown)));
    }
}
