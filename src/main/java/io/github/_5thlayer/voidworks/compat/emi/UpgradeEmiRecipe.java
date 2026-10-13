// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.emi;

import java.util.List;
import java.util.Set;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.mutation.CrucibleTime;
import io.github._5thlayer.voidworks.mutation.Upgrade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.Item;

/**
 * One upgrade step as EMI draws it: every item that takes it, as alternatives, × the Rung's count,
 * an arrow, then the one result; beneath, the Mutation Chamber's cost and chance, or the Void
 * Crucible's time alone at the End, which never fails.
 */
final class UpgradeEmiRecipe extends BasicEmiRecipe {

    enum Machine {
        MUTATION_CHAMBER("mutation_chamber"),
        VOID_CRUCIBLE("void_crucible");

        private final String name;

        Machine(String name) {
            this.name = name;
        }
    }

    private static final int GREY = 0xFF808080;

    private final Machine machine;
    private final Upgrade<Item> upgrade;

    UpgradeEmiRecipe(EmiRecipeCategory category, Machine machine, Upgrade<Item> upgrade, Set<Item> items) {
        super(category, id(machine, upgrade), 112, 44);
        this.machine = machine;
        this.upgrade = upgrade;
        inputs.add(EmiIngredient.of(items.stream().map(item -> (EmiIngredient) EmiStack.of(item, upgrade.count())).toList()));
        outputs.add(EmiStack.of(upgrade.result()));
    }

    /** Keyed by what sets the step apart, so it stays the same across reloads: its result, count, cost and chance. */
    private static Identifier id(Machine machine, Upgrade<Item> upgrade) {
        var result = BuiltInRegistries.ITEM.getKey(upgrade.result());
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "/upgrade/" + machine.name + "/" + result.getNamespace() + "/"
                + result.getPath() + "/" + upgrade.count() + "/" + upgrade.cost() + "/" + Math.round(upgrade.chance() * 1000));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 0);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 24, 1);
        widgets.addSlot(outputs.get(0), 54, 0).recipeContext(this);
        switch (machine) {
            case MUTATION_CHAMBER -> {
                widgets.addText(Component.translatable("voidworks.emi.cost", upgrade.cost()), 0, 22, GREY, false);
                widgets.addText(Component.translatable("voidworks.emi.chance", Math.round(upgrade.chance() * 100)), 0, 33, GREY, false);
            }
            case VOID_CRUCIBLE -> {
                // The server's config, which the client holds once it has joined.
                if (VoidworksConfig.loaded()) {
                    double ticks = new CrucibleTime(VoidworksConfig.crucibleTicks()).ticks(upgrade.cost(), CrucibleTime.END_PRESSURE, 1);
                    var time = StringUtil.formatTickDuration((int) Math.min(Math.ceil(ticks), Integer.MAX_VALUE), 20.0F);
                    widgets.addText(Component.translatable("voidworks.emi.crucible.time", time), 0, 22, GREY, false);
                }
                widgets.addTooltipText(List.of(Component.translatable("voidworks.emi.crucible.pressure"),
                        Component.translatable("voidworks.emi.crucible.sharing")), 0, 20, 112, 12);
            }
        }
    }
}
