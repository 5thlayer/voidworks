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
import io.github._5thlayer.voidworks.mutation.Families;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The Family ladders in EMI (#42): each upgrade step, a Rung's items × its count into the next
 * Rung's result, once under the Mutation Chamber with its void energy cost and chance, and once
 * under the Void Crucible with the time a lone one takes at the End. An item whose Rungs disagree
 * shows no step, as it has none in either machine.
 *
 * <p>The steps are read from the Families the server sent the client.
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing outside this
 * package names an EMI type, so the Module plays without it.
 */
@EmiEntrypoint
public final class UpgradeEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        var chamber = category("mutation_chamber", EmiStack.of(VoidworksBlocks.MUTATION_CHAMBER_ITEM.get()));
        var crucible = category("void_crucible", EmiStack.of(VoidworksBlocks.VOID_CRUCIBLE_ITEM.get()));
        registry.addCategory(chamber);
        registry.addWorkstation(chamber, EmiStack.of(VoidworksBlocks.MUTATION_CHAMBER_ITEM.get()));
        registry.addCategory(crucible);
        registry.addWorkstation(crucible, EmiStack.of(VoidworksBlocks.VOID_CRUCIBLE_ITEM.get()));
        // The connection's registries, not the level's: EMI may reload on the tags the server sends
        // while configuring, before the client has a level.
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        Families.ladders(connection.registryAccess()).steps().forEach((upgrade, items) -> {
            registry.addRecipe(new UpgradeEmiRecipe(chamber, UpgradeEmiRecipe.Machine.MUTATION_CHAMBER, upgrade, items));
            registry.addRecipe(new UpgradeEmiRecipe(crucible, UpgradeEmiRecipe.Machine.VOID_CRUCIBLE, upgrade, items));
        });
    }

    private static EmiRecipeCategory category(String name, EmiStack icon) {
        return new EmiRecipeCategory(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, name), icon);
    }
}
