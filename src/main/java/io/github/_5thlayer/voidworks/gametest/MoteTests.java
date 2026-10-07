// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.item.MoteItem;
import io.github._5thlayer.voidworks.item.VoidworksDataComponents;
import io.github._5thlayer.voidworks.item.VoidworksItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * A mote's grade is part of what the mote is: motes of one grade stack in a real inventory, motes
 * of different grades never merge, and the stack's name says which grade it is. Every mote has a
 * grade from {@link VoidEnergy#MIN_GRADE}, the bare item's, to {@link VoidEnergy#MAX_GRADE}.
 */
final class MoteTests {

    private MoteTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("motes_of_one_grade_merge", 1, MoteTests::oneGradeMerges);
        tests.test("motes_of_different_grades_stay_apart", 1, MoteTests::differentGradesStayApart);
        tests.test("a_mote_is_named_by_its_grade", 1, MoteTests::nameShowsGrade);
        tests.test("a_bare_mote_has_the_lowest_grade", 1, MoteTests::bareMoteHasTheLowestGrade);
        tests.test("a_saved_grade_stays_between_the_lowest_and_the_highest", 1, MoteTests::savedGradeStaysInRange);
        tests.test("a_stack_is_made_only_between_the_lowest_and_the_highest_grade", 1, MoteTests::stackOnlyInRange);
    }

    /** The bare item, as {@code /give} makes it, is a mote of the lowest grade and stacks with one. */
    private static void bareMoteHasTheLowestGrade(GameTestHelper helper) {
        var bare = new ItemStack(VoidworksItems.MOTE.get());
        if (MoteItem.gradeOf(bare) != VoidEnergy.MIN_GRADE) {
            helper.fail("a bare mote has grade " + MoteItem.gradeOf(bare) + ", not the lowest, " + VoidEnergy.MIN_GRADE);
            return;
        }
        if (!ItemStack.isSameItemSameComponents(bare, MoteItem.stack(VoidEnergy.MIN_GRADE, 1))) {
            helper.fail("a bare mote and a mote of grade " + VoidEnergy.MIN_GRADE + " should be the same mote");
            return;
        }
        helper.succeed();
    }

    /** The grade component reads back the lowest and the highest grade, and refuses one beyond either. */
    private static void savedGradeStaysInRange(GameTestHelper helper) {
        for (int grade : List.of(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE)) {
            var read = parseGrade(grade);
            if (!read.result().equals(Optional.of(grade))) {
                helper.fail("grade " + grade + " reads back as " + read);
                return;
            }
        }
        for (int grade : List.of(VoidEnergy.MIN_GRADE - 1, VoidEnergy.MAX_GRADE + 1)) {
            var read = parseGrade(grade);
            if (read.isSuccess()) {
                helper.fail("no mote has grade " + grade + ", but it reads back as " + read);
                return;
            }
        }
        helper.succeed();
    }

    private static void stackOnlyInRange(GameTestHelper helper) {
        for (int grade : List.of(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE)) {
            if (MoteItem.gradeOf(MoteItem.stack(grade, 1)) != grade) {
                helper.fail("a stack of grade " + grade + " has grade " + MoteItem.gradeOf(MoteItem.stack(grade, 1)));
                return;
            }
        }
        for (int grade : List.of(VoidEnergy.MIN_GRADE - 1, VoidEnergy.MAX_GRADE + 1)) {
            try {
                var stack = MoteItem.stack(grade, 1);
                helper.fail("no mote has grade " + grade + ", but a stack of it was made: " + stack);
                return;
            } catch (IllegalArgumentException refused) {
                // As it should be.
            }
        }
        helper.succeed();
    }

    private static DataResult<Integer> parseGrade(int grade) {
        return VoidworksDataComponents.GRADE.get().codecOrThrow().parse(JsonOps.INSTANCE, new JsonPrimitive(grade));
    }

    private static void oneGradeMerges(GameTestHelper helper) {
        Inventory inventory = helper.makeMockPlayer(GameType.SURVIVAL).getInventory();
        inventory.add(MoteItem.stack(3, 10));
        inventory.add(MoteItem.stack(3, 20));

        var stacks = stacksIn(inventory);
        if (stacks.size() != 1 || stacks.getFirst().getCount() != 30 || MoteItem.gradeOf(stacks.getFirst()) != 3) {
            helper.fail("two stacks of grade 3 should merge into one of 30, found " + describe(stacks));
            return;
        }
        helper.succeed();
    }

    private static void differentGradesStayApart(GameTestHelper helper) {
        Inventory inventory = helper.makeMockPlayer(GameType.SURVIVAL).getInventory();
        inventory.add(MoteItem.stack(2, 10));
        inventory.add(MoteItem.stack(3, 10));

        var stacks = stacksIn(inventory);
        if (stacks.size() != 2 || stacks.stream().anyMatch(stack -> stack.getCount() != 10)) {
            helper.fail("grades 2 and 3 should stay two stacks of 10, found " + describe(stacks));
            return;
        }
        if (MoteItem.gradeOf(stacks.get(0)) == MoteItem.gradeOf(stacks.get(1))) {
            helper.fail("the two stacks lost their grades: " + describe(stacks));
            return;
        }
        helper.succeed();
    }

    private static void nameShowsGrade(GameTestHelper helper) {
        String name = MoteItem.stack(7, 1).getHoverName().getString();
        if (!name.contains("7")) {
            helper.fail("a grade 7 mote's name should show 7, but reads: " + name);
            return;
        }
        if (name.equals(MoteItem.stack(8, 1).getHoverName().getString())) {
            helper.fail("motes of grades 7 and 8 share the name: " + name);
            return;
        }
        helper.succeed();
    }

    private static List<ItemStack> stacksIn(Inventory inventory) {
        return inventory.getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
    }

    private static String describe(List<ItemStack> stacks) {
        return stacks.stream().map(stack -> stack.getCount() + " x grade " + MoteItem.gradeOf(stack)).toList().toString();
    }
}
