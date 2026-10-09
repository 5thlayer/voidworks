// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A mote's grade is what the mote is: motes are a resource, one per grade, so motes of one grade
 * pool in a store and motes of different grades never do. Every mote has a grade from
 * {@link VoidEnergy#MIN_GRADE} to {@link VoidEnergy#MAX_GRADE}.
 */
final class MoteTests {

    private MoteTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("no_item_is_a_mote", 1, MoteTests::noItemIsAMote);
        tests.test("motes_of_one_grade_pool", 1, MoteTests::oneGradePools);
        tests.test("motes_of_different_grades_stay_apart", 1, MoteTests::differentGradesStayApart);
        tests.test("a_saved_mote_stays_between_the_lowest_and_the_highest_grade", 1, MoteTests::savedGradeStaysInRange);
        tests.test("a_mote_is_made_only_between_the_lowest_and_the_highest_grade", 1, MoteTests::moteOnlyInRange);
    }

    /** Motes are never items (ADR 0002), so {@code /give} cannot make one. */
    private static void noItemIsAMote(GameTestHelper helper) {
        var items = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals(Voidworks.MOD_ID))
                .toList();
        if (!items.isEmpty()) {
            helper.fail("Voidworks registers no item yet, but found " + items);
            return;
        }
        helper.succeed();
    }

    private static void oneGradePools(GameTestHelper helper) {
        var store = new MoteStore().add(3, 10).add(3, 20);

        long slotsUsed = store.snapshot().stream().filter(stack -> !stack.isEmpty()).count();
        if (slotsUsed != 1 || !VoidSpender.countByGrade(store).equals(Map.of(3, 30))) {
            helper.fail("two lots of grade 3 should pool into one slot of 30, found " + store.snapshot());
            return;
        }
        if (MoteResource.of(3) != MoteResource.of(3)) {
            helper.fail("there should be one resource per grade");
            return;
        }
        helper.succeed();
    }

    private static void differentGradesStayApart(GameTestHelper helper) {
        var store = new MoteStore().add(2, 10).add(3, 10);

        long slotsUsed = store.snapshot().stream().filter(stack -> !stack.isEmpty()).count();
        if (slotsUsed != 2 || !VoidSpender.countByGrade(store).equals(Map.of(2, 10, 3, 10))) {
            helper.fail("grades 2 and 3 should stay two slots of 10, found " + store.snapshot());
            return;
        }
        if (MoteResource.of(2).equals(MoteResource.of(3))) {
            helper.fail("motes of grades 2 and 3 are the same resource");
            return;
        }
        helper.succeed();
    }

    /** The codec reads back the lowest and the highest grade, and refuses one beyond either. */
    private static void savedGradeStaysInRange(GameTestHelper helper) {
        for (int grade : List.of(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE)) {
            var read = parse(grade);
            if (!read.result().equals(Optional.of(MoteResource.of(grade)))) {
                helper.fail("grade " + grade + " reads back as " + read);
                return;
            }
        }
        for (int grade : List.of(VoidEnergy.MIN_GRADE - 1, VoidEnergy.MAX_GRADE + 1)) {
            var read = parse(grade);
            if (read.isSuccess()) {
                helper.fail("no mote has grade " + grade + ", but it reads back as " + read);
                return;
            }
        }
        helper.succeed();
    }

    private static void moteOnlyInRange(GameTestHelper helper) {
        for (int grade : List.of(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE)) {
            if (MoteResource.of(grade).grade() != grade || MoteResource.of(grade).isEmpty()) {
                helper.fail("a mote of grade " + grade + " is " + MoteResource.of(grade));
                return;
            }
        }
        for (int grade : List.of(VoidEnergy.MIN_GRADE - 1, VoidEnergy.MAX_GRADE + 1)) {
            try {
                var mote = MoteResource.of(grade);
                helper.fail("no mote has grade " + grade + ", but one was made: " + mote);
                return;
            } catch (IllegalArgumentException refused) {
                // As it should be.
            }
        }
        if (!MoteResource.EMPTY.isEmpty()) {
            helper.fail("the empty resource should be empty");
            return;
        }
        helper.succeed();
    }

    private static DataResult<MoteResource> parse(int grade) {
        return MoteResource.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(grade));
    }
}
