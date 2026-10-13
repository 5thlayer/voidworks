// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;

import io.github._5thlayer.libworks.WorksTab;
import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/** Every Voidworks item is in the suite's Works tab, in a fixed order, and in no other tab. */
final class CreativeTabTests {

    private static final List<String> WORKS_TAB_ORDER = List.of(
            "overworld_void_displacer", "nether_void_displacer", "end_void_displacer",
            "mutation_chamber", "void_crucible", "void_siphon");

    private CreativeTabTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("works_tab_holds_every_item_in_order", 1, CreativeTabTests::worksTabInOrder);
        tests.test("no_other_tab_holds_a_voidworks_item", 1, CreativeTabTests::noOtherTab);
    }

    private static void worksTabInOrder(GameTestHelper helper) {
        rebuild(helper);
        List<String> shown = ownItems(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY));
        if (!shown.equals(WORKS_TAB_ORDER)) {
            helper.fail("the Works tab shows " + shown + ", not " + WORKS_TAB_ORDER);
            return;
        }
        helper.succeed();
    }

    private static void noOtherTab(GameTestHelper helper) {
        rebuild(helper);
        // Empty tabs would pass the loop below without its items reaching any tab.
        if (ownItems(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY)).isEmpty()) {
            helper.fail("the Works tab shows no Voidworks item, so no tab was filled");
            return;
        }
        for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            CreativeModeTab tab = entry.getValue();
            // The search tab lists every other tab's items.
            if (entry.getKey() == WorksTab.KEY || tab.getType() == CreativeModeTab.Type.SEARCH) {
                continue;
            }
            List<String> own = ownItems(tab);
            if (!own.isEmpty()) {
                helper.fail("the tab " + entry.getKey().identifier() + " shows " + own);
                return;
            }
        }
        helper.succeed();
    }

    private static void rebuild(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true,
                helper.getLevel().registryAccess());
    }

    private static List<String> ownItems(CreativeModeTab tab) {
        return tab.getDisplayItems().stream()
                .map(ItemStack::getItem)
                .map(BuiltInRegistries.ITEM::getKey)
                .filter(id -> id.getNamespace().equals(Voidworks.MOD_ID))
                .map(Identifier::getPath)
                .toList();
    }
}
