// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;

/**
 * A Density item's tooltip, built on a real server with the server's config: the line under its
 * name, with the motes a Displacer yields from it, and no line for a block of Density 0.
 */
final class DensityTooltipTests {

    private DensityTooltipTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("density_tooltip_shows_a_diamond_blocks_density_and_motes", 1,
                helper -> showsUnderTheName(helper, Items.DIAMOND_BLOCK, "voidworks.density.tooltip.motes", 4, 64));
        tests.test("density_tooltip_shows_a_copper_blocks_density_and_motes", 1,
                helper -> showsUnderTheName(helper, Items.COPPER_BLOCK, "voidworks.density.tooltip.mote", 1, 1));
        tests.test("density_tooltip_shows_nothing_for_cobblestone", 1, DensityTooltipTests::showsNothingForDensityZero);
    }

    private static void showsUnderTheName(GameTestHelper helper, Item item, String key, int density, int motes) {
        var lines = tooltip(helper, item);
        if (lines.size() < 2 || !(lines.get(1).getContents() instanceof TranslatableContents line) || !key.equals(line.getKey())
                || !List.of(density, motes).equals(List.of(line.getArgs()))) {
            helper.fail(item + " should show Density " + density + " and " + motes + " motes right under its name, showed " + lines);
            return;
        }
        helper.succeed();
    }

    private static void showsNothingForDensityZero(GameTestHelper helper) {
        var lines = tooltip(helper, Items.COBBLESTONE);
        if (lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents line && line.getKey().startsWith("voidworks.density."))) {
            helper.fail("cobblestone, of Density 0, showed a Density line: " + lines);
            return;
        }
        helper.succeed();
    }

    private static List<Component> tooltip(GameTestHelper helper, Item item) {
        return new ItemStack(item).getTooltipLines(Item.TooltipContext.of(helper.getLevel()), helper.makeMockPlayer(GameType.SURVIVAL),
                TooltipFlag.NORMAL);
    }
}
