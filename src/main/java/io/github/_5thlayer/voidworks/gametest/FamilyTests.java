// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.voidworks.mutation.Families;
import io.github._5thlayer.voidworks.mutation.Family;
import io.github._5thlayer.voidworks.mutation.Upgrade;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Families on a real server: the shipped ladders, read from the {@code voidworks:family}
 * registry with their Rung tags bound, and a datapack adding an item to a Rung by tag, adding a
 * Family and replacing a shipped one. The datapack is the game-test pack ({@code gametest_pack/}),
 * which adds coal to copper's ingot Rung, adds clay ball → brick, replaces the passive drops with
 * feather → rabbit's foot, and has two Families disagree on flint. Its wheat seeds → melon seeds,
 * at a chance that never comes up, is the Mutation Chamber's ({@link MutationChamberTests}).
 */
final class FamilyTests {

    private FamilyTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("family_shipped_metal_rungs_upgrade", 1, FamilyTests::shippedMetalRungsUpgrade);
        tests.test("family_shipped_steep_rungs_lead_to_gate_items", 1, FamilyTests::shippedSteepRungsLeadToGateItems);
        tests.test("family_top_rung_and_no_family_have_no_upgrade", 1, FamilyTests::topRungAndNoFamilyHaveNoUpgrade);
        tests.test("family_datapack_adds_an_item_to_a_rung_by_tag", 1, FamilyTests::datapackAddsAnItemToARung);
        tests.test("family_datapack_adds_a_family", 1, FamilyTests::datapackAddsAFamily);
        tests.test("family_datapack_replaces_a_shipped_family", 1, FamilyTests::datapackReplacesAShippedFamily);
        tests.test("family_disagreeing_families_leave_no_upgrade", 1, FamilyTests::disagreeingFamiliesLeaveNoUpgrade);
        tests.test("family_bottom_rung_naming_an_upgrade_is_refused", 1, FamilyTests::bottomRungNamingAnUpgradeIsRefused);
        tests.test("family_rung_above_the_bottom_missing_its_cost_is_refused", 1, FamilyTests::rungMissingItsCostIsRefused);
    }

    private static void shippedMetalRungsUpgrade(GameTestHelper helper) {
        expect(helper, Items.COPPER_INGOT, new Upgrade<>(Items.IRON_INGOT, 1, 100, 0.8));
        expect(helper, Items.IRON_INGOT, new Upgrade<>(Items.GOLD_INGOT, 1, 200, 0.8));
        expect(helper, Items.COPPER_NUGGET, new Upgrade<>(Items.IRON_NUGGET, 1, 100, 0.8));
        expect(helper, Items.RAW_IRON_BLOCK, new Upgrade<>(Items.RAW_GOLD_BLOCK, 1, 200, 0.8));
        helper.succeed();
    }

    /** A gate item is reached only by a steep Rung: count 8, eight times the cost, half the chance. */
    private static void shippedSteepRungsLeadToGateItems(GameTestHelper helper) {
        expect(helper, Items.DIAMOND, new Upgrade<>(Items.NETHERITE_SCRAP, 8, 4000, 0.5));
        expect(helper, Items.GLOWSTONE_DUST, new Upgrade<>(Items.BLAZE_POWDER, 8, 1600, 0.5));
        expect(helper, Items.MAGMA_CREAM, new Upgrade<>(Items.BLAZE_ROD, 8, 7200, 0.5));
        expect(helper, Items.BLAZE_ROD, new Upgrade<>(Items.GHAST_TEAR, 8, 8000, 0.5));
        expect(helper, Items.ENDER_PEARL, new Upgrade<>(Items.SHULKER_SHELL, 8, 10400, 0.5));
        expect(helper, Items.ROTTEN_FLESH, new Upgrade<>(Items.BONE, 2, 100, 0.8));
        helper.succeed();
    }

    private static void topRungAndNoFamilyHaveNoUpgrade(GameTestHelper helper) {
        expectNone(helper, Items.GOLD_INGOT);
        expectNone(helper, Items.SHULKER_SHELL);
        expectNone(helper, Items.STICK);
        expectNone(helper, Items.IRON_ORE);
        if (Families.next(ItemStack.EMPTY).isPresent()) {
            helper.fail("an empty stack should have no upgrade");
            return;
        }
        helper.succeed();
    }

    private static void datapackAddsAnItemToARung(GameTestHelper helper) {
        expect(helper, Items.COAL, new Upgrade<>(Items.IRON_INGOT, 1, 100, 0.8));
        helper.succeed();
    }

    private static void datapackAddsAFamily(GameTestHelper helper) {
        expect(helper, Items.CLAY_BALL, new Upgrade<>(Items.BRICK, 4, 300, 1.0));
        helper.succeed();
    }

    private static void datapackReplacesAShippedFamily(GameTestHelper helper) {
        expect(helper, Items.FEATHER, new Upgrade<>(Items.RABBIT_FOOT, 3, 50, 1.0));
        expectNone(helper, Items.LEATHER);
        helper.succeed();
    }

    private static void disagreeingFamiliesLeaveNoUpgrade(GameTestHelper helper) {
        expectNone(helper, Items.FLINT);
        var named = Families.disagreements().get(Items.FLINT);
        var expected = Set.of("voidworks:test_flint_to_arrow", "voidworks:test_flint_to_gravel");
        if (named == null || !named.equals(expected)) {
            helper.fail("flint's disagreement should name " + expected + ", but names " + named);
            return;
        }
        helper.succeed();
    }

    private static void bottomRungNamingAnUpgradeIsRefused(GameTestHelper helper) {
        var read = parse("{\"rungs\": [{\"items\": \"#voidworks:rung/metal_ingots/copper\", \"result\": \"minecraft:copper_ingot\","
                + " \"cost\": 100, \"chance\": 0.8}]}");
        if (read.isPresent()) {
            helper.fail("a bottom Rung naming an upgrade should be refused, but reads as " + read.get());
            return;
        }
        helper.succeed();
    }

    private static void rungMissingItsCostIsRefused(GameTestHelper helper) {
        var read = parse("{\"rungs\": [{\"items\": \"#voidworks:rung/metal_ingots/copper\"},"
                + " {\"items\": \"#voidworks:rung/metal_ingots/iron\", \"result\": \"minecraft:iron_ingot\", \"chance\": 0.8}]}");
        if (read.isPresent()) {
            helper.fail("a Rung above the bottom with no cost should be refused, but reads as " + read.get());
            return;
        }
        helper.succeed();
    }

    private static Optional<Family> parse(String json) {
        return Family.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result();
    }

    /** Fails the test, by {@link GameTestHelper#fail} throwing, unless {@code item} upgrades by {@code expected}. */
    private static void expect(GameTestHelper helper, Item item, Upgrade<Item> expected) {
        var next = Families.next(new ItemStack(item));
        if (!next.equals(Optional.of(expected))) {
            helper.fail(item + " should upgrade by " + expected + ", but its next Rung is " + next);
        }
    }

    /** Fails the test, by {@link GameTestHelper#fail} throwing, unless {@code item} has no upgrade. */
    private static void expectNone(GameTestHelper helper, Item item) {
        var next = Families.next(new ItemStack(item));
        if (next.isPresent()) {
            helper.fail(item + " should have no upgrade, but its next Rung is " + next.get());
        }
    }
}
