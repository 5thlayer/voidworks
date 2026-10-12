// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import io.github._5thlayer.voidworks.mutation.MutationItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The item face and hand every Mutation block shares, on a block of nine outputs whose input takes
 * only clay balls, as the Unmaking Chamber's nine would; the Mutation Chamber's one output stands on
 * it in {@link MutationChamberTests}.
 */
final class MutationItemsTests {

    private static final int OUTPUTS = 9;

    private static final BlockPos AT = new BlockPos(4, 1, 4);

    private MutationItemsTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("mutation_items_take_into_the_input_only_what_the_rule_accepts_and_give_from_every_output", 1, MutationItemsTests::face);
        tests.test("mutation_items_give_an_empty_hand_every_output_or_else_the_input", 1, MutationItemsTests::byHand);
        tests.test("mutation_items_drop_everything_they_hold", 20, MutationItemsTests::drop);
    }

    private static void face(GameTestHelper helper) {
        var items = nineOutputs();
        helper.assertTrue(items.inventory().size() == 1 + OUTPUTS, "the items hold " + items.inventory().size() + " slots, not an input and nine outputs");
        var face = items.itemFace();
        for (int slot = MutationItems.FIRST_OUTPUT; slot <= OUTPUTS; slot++) {
            items.inventory().set(slot, ItemResource.of(Items.BRICK), slot);
        }
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(face.insert(MutationItems.INPUT, ItemResource.of(Items.STICK), 1, transaction) == 0,
                    "the face took a stick, which the rule refuses");
            for (int slot = MutationItems.FIRST_OUTPUT; slot <= OUTPUTS; slot++) {
                helper.assertTrue(face.insert(slot, ItemResource.of(Items.CLAY_BALL), 1, transaction) == 0, "the face put a clay ball into output " + slot);
            }
            helper.assertTrue(face.insert(MutationItems.INPUT, ItemResource.of(Items.CLAY_BALL), 4, transaction) == 4,
                    "the face did not take four clay balls");
            helper.assertTrue(face.extract(MutationItems.INPUT, ItemResource.of(Items.CLAY_BALL), 4, transaction) == 0,
                    "the face gave from its input");
            for (int slot = MutationItems.FIRST_OUTPUT; slot <= OUTPUTS; slot++) {
                helper.assertTrue(face.extract(slot, ItemResource.of(Items.BRICK), slot, transaction) == slot,
                        "the face did not give output " + slot + "'s bricks");
            }
            transaction.commit();
        }
        helper.assertTrue(ItemStack.matches(items.held(MutationItems.INPUT), new ItemStack(Items.CLAY_BALL, 4)),
                "the input holds " + items.held(MutationItems.INPUT) + ", not four clay balls");
        helper.succeed();
    }

    private static void byHand(GameTestHelper helper) {
        var items = nineOutputs();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);

        var sticks = new ItemStack(Items.STICK, 3);
        helper.assertTrue(items.insertFromHand(sticks) == 0 && sticks.getCount() == 3, "the input took sticks, which the rule refuses");
        var clay = new ItemStack(Items.CLAY_BALL, 5);
        helper.assertTrue(items.insertFromHand(clay) == 5 && clay.isEmpty(), "the input did not take the hand's five clay balls");

        items.inventory().set(MutationItems.FIRST_OUTPUT, ItemResource.of(Items.BRICK), 2);
        items.inventory().set(OUTPUTS, ItemResource.of(Items.FLINT), 3);
        helper.assertTrue(items.giveTo(player), "an empty hand was given nothing");
        helper.assertTrue(player.getInventory().countItem(Items.BRICK) == 2 && player.getInventory().countItem(Items.FLINT) == 3,
                "an empty hand was not given every output");
        helper.assertTrue(player.getInventory().countItem(Items.CLAY_BALL) == 0, "an empty hand took the input with outputs to take");

        helper.assertTrue(items.giveTo(player), "an empty hand was not given back the input");
        helper.assertTrue(player.getInventory().countItem(Items.CLAY_BALL) == 5, "an empty hand was not given back the five clay balls");
        helper.assertTrue(!items.giveTo(player), "empty items gave something");
        helper.succeed();
    }

    private static void drop(GameTestHelper helper) {
        var items = nineOutputs();
        items.inventory().set(MutationItems.INPUT, ItemResource.of(Items.CLAY_BALL), 3);
        items.inventory().set(MutationItems.FIRST_OUTPUT, ItemResource.of(Items.BRICK), 1);
        items.inventory().set(OUTPUTS, ItemResource.of(Items.FLINT), 2);
        var pos = helper.absolutePos(AT);
        items.drop(helper.getLevel(), pos);
        helper.runAfterDelay(1, () -> {
            for (var expected : new ItemStack[] {new ItemStack(Items.CLAY_BALL, 3), new ItemStack(Items.BRICK, 1), new ItemStack(Items.FLINT, 2)}) {
                int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), item -> item.getItem().is(expected.getItem()))
                        .stream().mapToInt(item -> item.getItem().getCount()).sum();
                helper.assertTrue(dropped == expected.getCount(), "the items dropped " + dropped + ", not " + expected);
            }
            helper.succeed();
        });
    }

    private static MutationItems nineOutputs() {
        return new MutationItems(OUTPUTS, resource -> resource.is(Items.CLAY_BALL), () -> {
        });
    }
}
