// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.libworks.testkit.WorksTabCheck;

/** Every Voidworks item is in the suite's Works tab, in a fixed order, and in no other tab. */
final class CreativeTabTests {

    private static final List<String> WORKS_TAB_ORDER = List.of(
            "overworld_void_displacer", "nether_void_displacer", "end_void_displacer",
            "mutation_chamber", "unmaking_chamber", "void_crucible", "void_siphon",
            "resonant_void_siphon", "draconic_void_siphon");

    private CreativeTabTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("works_tab_holds_every_item_in_order", 1,
                helper -> WorksTabCheck.inOrder(helper, Voidworks.MOD_ID, WORKS_TAB_ORDER));
        tests.test("no_other_tab_holds_a_voidworks_item", 1,
                helper -> WorksTabCheck.noOtherTab(helper, Voidworks.MOD_ID));
    }
}
