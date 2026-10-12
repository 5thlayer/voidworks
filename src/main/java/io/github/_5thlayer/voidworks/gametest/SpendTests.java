// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github._5thlayer.voidworks.VoidworksCapabilities;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;

/**
 * Spending motes from a store at a position: a spend takes the lowest worthwhile grade first,
 * refuses motes that are not above the local Void Pressure, and leaves a store that cannot afford it
 * untouched. The tests stand in the Overworld and read its pressure there, 2 by default, so grade 3
 * releases {@link VoidEnergy#BASE} per mote and grade 4 twice that. A store is a plain
 * {@code ResourceHandler<MoteResource>}, and one is reached through a block's
 * {@code voidworks:motes} capability.
 */
final class SpendTests {

    /** The block that stands in for a void machine: the game test run gives it {@code voidworks:motes}. */
    static final Block MACHINE = Blocks.LODESTONE;

    /** The store each placed machine holds, by its position; a machine with none provides no capability. */
    private static final Map<BlockPos, ResourceHandler<MoteResource>> MACHINES = new ConcurrentHashMap<>();

    private SpendTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("spend_refuses_motes_not_above_the_pressure", 1, SpendTests::refusesMotesNotAboveThePressure);
        tests.test("spend_takes_the_lowest_worthwhile_grade_first", 1, SpendTests::takesTheLowestWorthwhileGradeFirst);
        tests.test("spend_moves_up_a_grade_when_the_lower_runs_out", 1, SpendTests::movesUpAGradeWhenTheLowerRunsOut);
        tests.test("spend_leaves_a_store_that_cannot_afford_it_untouched", 1, SpendTests::cannotAffordLeavesStoreUntouched);
        tests.test("spend_takes_the_lowest_grade_in_the_nether", 1, SpendTests::takesTheLowestGradeInTheNether);
        tests.test("spend_takes_motes_of_a_grade_from_several_slots", 1, SpendTests::takesMotesFromSeveralSlots);
        tests.test("spend_takes_from_a_block_through_the_motes_capability", 1, SpendTests::takesFromABlockThroughTheCapability);
        tests.test("spend_from_several_stores_takes_the_lowest_grade_from_any_of_them", 1, SpendTests::severalStoresTakeTheLowestGradeFirst);
        tests.test("spend_from_several_stores_that_cannot_afford_it_together_leaves_every_one_untouched", 1,
                SpendTests::severalStoresThatCannotAffordAreUntouched);
    }

    /** Gives {@link #MACHINE} the {@code voidworks:motes} capability, for the game test run only. */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(VoidworksCapabilities.MOTES, (level, pos, state, blockEntity, side) -> MACHINES.get(pos.immutable()), MACHINE);
    }

    /**
     * Places {@link #MACHINE} at {@code pos}, relative to the test, providing {@code store} as its
     * {@code voidworks:motes}: a mote holder another test's machine can touch.
     */
    static void placeStore(GameTestHelper helper, BlockPos pos, ResourceHandler<MoteResource> store) {
        var absolute = helper.absolutePos(pos);
        MACHINES.put(absolute, store);
        helper.getLevel().setBlockAndUpdate(absolute, MACHINE.defaultBlockState());
    }

    /** A Consumer's void machine, as the spender finds it: a block that provides {@code voidworks:motes}. */
    private static void takesFromABlockThroughTheCapability(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos, MACHINE.defaultBlockState());
        int pressure = VoidPressure.at(helper.getLevel(), pos);
        MACHINES.put(pos, new MoteStore().add(pressure + 1, 5));

        var handler = helper.getLevel().getCapability(VoidworksCapabilities.MOTES, pos, null);
        if (handler == null) {
            helper.fail("the machine at " + pos + " provides no voidworks:motes");
            return;
        }
        var spend = VoidSpender.spend(handler, helper.getLevel(), pos, 2 * VoidEnergy.BASE);

        if (spend.isEmpty() || !spend.get().motes().equals(Map.of(pressure + 1, 2))) {
            helper.fail("a spend of " + 2 * VoidEnergy.BASE + " should take two grade " + (pressure + 1) + " motes, but gave " + spend);
            return;
        }
        if (!VoidSpender.countByGrade(MACHINES.get(pos)).equals(Map.of(pressure + 1, 3))) {
            helper.fail("the machine should hold 3 motes after the spend, holds " + VoidSpender.countByGrade(MACHINES.get(pos)));
            return;
        }
        MACHINES.remove(pos);
        helper.succeed();
    }

    /** One grade can lie in several slots, as a store that does not pool its motes keeps them. */
    private static void takesMotesFromSeveralSlots(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var store = new MoteStore();
        store.set(0, MoteResource.of(pressure + 1), 1);
        store.set(4, MoteResource.of(pressure + 1), 1);

        var spend = VoidSpender.spend(store, helper.getLevel(), here(helper), 2 * VoidEnergy.BASE);

        if (spend.isEmpty() || !spend.get().motes().equals(Map.of(pressure + 1, 2))) {
            helper.fail("a spend of " + 2 * VoidEnergy.BASE + " should take both grade " + (pressure + 1)
                    + " motes, from both slots, but gave " + spend);
            return;
        }
        if (!VoidSpender.countByGrade(store).isEmpty()) {
            helper.fail("the spend left " + VoidSpender.countByGrade(store));
            return;
        }
        helper.succeed();
    }

    /** A store holding only motes the pressure here makes worthless: the lowest grade, at the pressure. */
    private static void refusesMotesNotAboveThePressure(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var store = new MoteStore().add(pressure, 40);
        var before = store.snapshot();

        var spend = VoidSpender.spend(store, helper.getLevel(), here(helper), 1);

        if (spend.isPresent()) {
            helper.fail("motes not above the pressure " + pressure + " should be refused, but the spend released "
                    + spend.get().released());
            return;
        }
        if (!store.snapshot().equals(before)) {
            helper.fail("a refused spend changed the store: " + store.snapshot());
            return;
        }
        helper.succeed();
    }

    /** A store holding a worthless grade and two worthwhile grades. */
    private static void takesTheLowestWorthwhileGradeFirst(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var store = new MoteStore().add(pressure, 50).add(pressure + 2, 10).add(pressure + 1, 10);

        var spend = VoidSpender.spend(store, helper.getLevel(), here(helper), 3 * VoidEnergy.BASE);

        if (spend.isEmpty() || spend.get().released() != 3 * VoidEnergy.BASE) {
            helper.fail("a spend of " + 3 * VoidEnergy.BASE + " should release exactly that from grade " + (pressure + 1)
                    + " motes, but gave " + spend);
            return;
        }
        var expected = Map.of(pressure, 50, pressure + 1, 7, pressure + 2, 10);
        if (!VoidSpender.countByGrade(store).equals(expected)) {
            helper.fail("the motes left should be " + expected + " (grade to count), but are " + VoidSpender.countByGrade(store));
            return;
        }
        helper.succeed();
    }

    private static void movesUpAGradeWhenTheLowerRunsOut(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var store = new MoteStore().add(pressure + 1, 2).add(pressure + 2, 5);

        // The two grade 3 motes release 2 x BASE, then one grade 4 mote releases 2 x BASE more.
        var spend = VoidSpender.spend(store, helper.getLevel(), here(helper), 4 * VoidEnergy.BASE);

        if (spend.isEmpty() || spend.get().released() != 4 * VoidEnergy.BASE) {
            helper.fail("a spend of " + 4 * VoidEnergy.BASE + " should release exactly that, but gave " + spend);
            return;
        }
        var expected = Map.of(pressure + 2, 4);
        if (!VoidSpender.countByGrade(store).equals(expected)) {
            helper.fail("the motes left should be " + expected + " (grade to count), but are " + VoidSpender.countByGrade(store));
            return;
        }
        helper.succeed();
    }

    /** All the worthwhile motes together release less than asked, and the worthless ones do not count. */
    private static void cannotAffordLeavesStoreUntouched(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var store = new MoteStore().add(pressure, 64).add(pressure + 1, 2).add(pressure + 2, 1);
        var before = store.snapshot();

        // 2 x BASE from the grade 3 motes and 2 x BASE from the grade 4 mote: 4 x BASE in all.
        var spend = VoidSpender.spend(store, helper.getLevel(), here(helper), 4 * VoidEnergy.BASE + 1);

        if (spend.isPresent()) {
            helper.fail("the store cannot release " + (4 * VoidEnergy.BASE + 1) + ", but the spend released " + spend.get().released());
            return;
        }
        if (!store.snapshot().equals(before)) {
            helper.fail("a refused spend changed the store: " + store.snapshot());
            return;
        }
        helper.succeed();
    }

    /**
     * The first store holds one mote two steps above the pressure, the second two motes one step above:
     * the spend of {@code 2 × BASE} takes the second's, the lowest worthwhile grade of either, and
     * leaves the first's.
     */
    private static void severalStoresTakeTheLowestGradeFirst(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var first = new MoteStore().add(pressure + 2, 1);
        var second = new MoteStore().add(pressure + 1, 2);
        var taken = VoidSpender.spendTogether(List.of(first, second), helper.getLevel(), here(helper), 2 * VoidEnergy.BASE);
        var expected = List.of(Map.<Integer, Integer>of(), Map.of(pressure + 1, 2));
        helper.assertTrue(taken.equals(Optional.of(expected)), "the spend took " + taken + ", not " + expected);
        helper.assertTrue(VoidSpender.countByGrade(first).equals(Map.of(pressure + 2, 1)), "the first store holds " + VoidSpender.countByGrade(first));
        helper.assertTrue(VoidSpender.countByGrade(second).isEmpty(), "the second store holds " + VoidSpender.countByGrade(second));
        helper.succeed();
    }

    /** Two stores worth {@code 2 × BASE} each, asked for one more than both together: neither gives anything. */
    private static void severalStoresThatCannotAffordAreUntouched(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var first = new MoteStore().add(pressure + 1, 2);
        var second = new MoteStore().add(pressure + 2, 1);
        var before = List.of(first.snapshot(), second.snapshot());
        var taken = VoidSpender.spendTogether(List.of(first, second), helper.getLevel(), here(helper), 4 * VoidEnergy.BASE + 1);
        helper.assertTrue(taken.isEmpty(), "the stores cannot release " + (4 * VoidEnergy.BASE + 1) + ", but the spend took " + taken);
        helper.assertTrue(List.of(first.snapshot(), second.snapshot()).equals(before), "a refused spend changed the stores");
        helper.succeed();
    }

    /** A mote of the lowest grade is worth something at the lowest default pressure, the Nether's. */
    private static void takesTheLowestGradeInTheNether(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var store = new MoteStore().add(VoidEnergy.MIN_GRADE, 1);

        var spend = VoidSpender.spend(store, nether, BlockPos.ZERO, 1);

        if (spend.isEmpty() || spend.get().released() != VoidEnergy.BASE) {
            helper.fail("a lowest grade mote spent at the Nether's pressure " + VoidPressure.at(nether, BlockPos.ZERO)
                    + " should release " + VoidEnergy.BASE + ", but gave " + spend);
            return;
        }
        if (!VoidSpender.countByGrade(store).isEmpty()) {
            helper.fail("the spend left " + VoidSpender.countByGrade(store));
            return;
        }
        helper.succeed();
    }

    /** Where the spender stands: the platform's floor, in the Overworld. */
    private static BlockPos here(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(0, 2, 0));
    }
}
