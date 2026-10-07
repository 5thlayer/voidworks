// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.item.MoteItem;
import io.github._5thlayer.voidworks.item.VoidworksItems;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * Spending motes from a real inventory at a position: a spend takes the lowest worthwhile grade
 * first, refuses motes that are not above the local Void Pressure, and leaves an inventory that
 * cannot afford it untouched. The tests stand in the Overworld and read its pressure there, 2 by
 * default, so grade 3 releases {@link VoidEnergy#BASE} per mote and grade 4 twice that. A
 * machine's inventory is a plain container behind the item handler, and a player's is the real
 * thing.
 */
final class SpendTests {

    private SpendTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("spend_refuses_motes_not_above_the_pressure", 1, SpendTests::refusesMotesNotAboveThePressure);
        tests.test("spend_takes_the_lowest_worthwhile_grade_first", 1, SpendTests::takesTheLowestWorthwhileGradeFirst);
        tests.test("spend_moves_up_a_grade_when_the_lower_runs_out", 1, SpendTests::movesUpAGradeWhenTheLowerRunsOut);
        tests.test("spend_leaves_an_inventory_that_cannot_afford_it_untouched", 1, SpendTests::cannotAffordLeavesInventoryUntouched);
        tests.test("spend_takes_a_bare_mote_in_the_nether", 1, SpendTests::takesABareMoteInTheNether);
        tests.test("spend_takes_motes_of_a_grade_that_differ_in_another_component", 1, SpendTests::takesMotesThatDifferInAnotherComponent);
    }

    /** A mote named in an anvil is still a mote of its grade, though it no longer stacks with the others. */
    private static void takesMotesThatDifferInAnotherComponent(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var named = MoteItem.stack(pressure + 1, 1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Kept"));
        var machine = new SimpleContainer(9);
        machine.addItem(MoteItem.stack(pressure + 1, 1));
        machine.addItem(named);

        var spend = VoidSpender.spend(VanillaContainerWrapper.of(machine), helper.getLevel(), here(helper), 2 * VoidEnergy.BASE);

        if (spend.isEmpty() || !spend.get().motes().equals(Map.of(pressure + 1, 2))) {
            helper.fail("a spend of " + 2 * VoidEnergy.BASE + " should take both grade " + (pressure + 1)
                    + " motes, the named one too, but gave " + spend);
            return;
        }
        if (!machine.isEmpty()) {
            helper.fail("the spend left " + describe(machine));
            return;
        }
        helper.succeed();
    }

    /** A machine's inventory holding only motes the pressure here makes worthless: the lowest grade, at the pressure. */
    private static void refusesMotesNotAboveThePressure(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var machine = new SimpleContainer(9);
        machine.addItem(MoteItem.stack(pressure, 40));
        var before = snapshot(machine);

        var spend = VoidSpender.spend(VanillaContainerWrapper.of(machine), helper.getLevel(), here(helper), 1);

        if (spend.isPresent()) {
            helper.fail("motes not above the pressure " + pressure + " should be refused, but the spend released "
                    + spend.get().released());
            return;
        }
        if (!unchanged(machine, before)) {
            helper.fail("a refused spend changed the inventory: " + describe(machine));
            return;
        }
        helper.succeed();
    }

    /** A player's inventory holding a worthless grade, two worthwhile grades and an item that is no mote. */
    private static void takesTheLowestWorthwhileGradeFirst(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var inventory = player.getInventory();
        inventory.add(MoteItem.stack(pressure, 50));
        inventory.add(MoteItem.stack(pressure + 2, 10));
        inventory.add(MoteItem.stack(pressure + 1, 10));
        inventory.add(new ItemStack(Items.COBBLESTONE, 64));

        var spend = VoidSpender.spend(PlayerInventoryWrapper.of(player), helper.getLevel(), here(helper), 3 * VoidEnergy.BASE);

        if (spend.isEmpty() || spend.get().released() != 3 * VoidEnergy.BASE) {
            helper.fail("a spend of " + 3 * VoidEnergy.BASE + " should release exactly that from grade " + (pressure + 1)
                    + " motes, but gave " + spend);
            return;
        }
        var expected = Map.of(pressure, 50, pressure + 1, 7, pressure + 2, 10);
        if (!moteCounts(inventory).equals(expected)) {
            helper.fail("the motes left should be " + expected + " (grade to count), but are " + moteCounts(inventory));
            return;
        }
        if (inventory.countItem(Items.COBBLESTONE) != 64) {
            helper.fail("a spend of motes took something else: " + inventory.countItem(Items.COBBLESTONE) + " cobblestone left");
            return;
        }
        helper.succeed();
    }

    private static void movesUpAGradeWhenTheLowerRunsOut(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var machine = new SimpleContainer(9);
        machine.addItem(MoteItem.stack(pressure + 1, 2));
        machine.addItem(MoteItem.stack(pressure + 2, 5));

        // The two grade 3 motes release 2 x BASE, then one grade 4 mote releases 2 x BASE more.
        var spend = VoidSpender.spend(VanillaContainerWrapper.of(machine), helper.getLevel(), here(helper), 4 * VoidEnergy.BASE);

        if (spend.isEmpty() || spend.get().released() != 4 * VoidEnergy.BASE) {
            helper.fail("a spend of " + 4 * VoidEnergy.BASE + " should release exactly that, but gave " + spend);
            return;
        }
        var expected = Map.of(pressure + 2, 4);
        if (!moteCounts(machine).equals(expected)) {
            helper.fail("the motes left should be " + expected + " (grade to count), but are " + moteCounts(machine));
            return;
        }
        helper.succeed();
    }

    /** All the worthwhile motes together release less than asked, and the worthless ones do not count. */
    private static void cannotAffordLeavesInventoryUntouched(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), here(helper));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var inventory = player.getInventory();
        inventory.add(MoteItem.stack(pressure, 64));
        inventory.add(MoteItem.stack(pressure + 1, 2));
        inventory.add(MoteItem.stack(pressure + 2, 1));
        var before = snapshot(inventory);

        // 2 x BASE from the grade 3 motes and 2 x BASE from the grade 4 mote: 4 x BASE in all.
        var spend = VoidSpender.spend(PlayerInventoryWrapper.of(player), helper.getLevel(), here(helper), 4 * VoidEnergy.BASE + 1);

        if (spend.isPresent()) {
            helper.fail("the inventory cannot release " + (4 * VoidEnergy.BASE + 1) + ", but the spend released " + spend.get().released());
            return;
        }
        if (!unchanged(inventory, before)) {
            helper.fail("a refused spend changed the inventory: " + describe(inventory));
            return;
        }
        helper.succeed();
    }

    /**
     * A mote nothing has graded, as {@code /give} makes it, is worth something at the lowest default
     * pressure, the Nether's: it has the lowest grade any harvest gives.
     */
    private static void takesABareMoteInTheNether(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var machine = new SimpleContainer(9);
        machine.addItem(new ItemStack(VoidworksItems.MOTE.get()));

        var spend = VoidSpender.spend(VanillaContainerWrapper.of(machine), nether, BlockPos.ZERO, 1);

        if (spend.isEmpty() || spend.get().released() != VoidEnergy.BASE) {
            helper.fail("a bare mote spent at the Nether's pressure " + VoidPressure.at(nether, BlockPos.ZERO)
                    + " should release " + VoidEnergy.BASE + ", but gave " + spend);
            return;
        }
        if (!machine.isEmpty()) {
            helper.fail("the spend left " + describe(machine));
            return;
        }
        helper.succeed();
    }

    /** Where the spender stands: the platform's floor, in the Overworld. */
    private static BlockPos here(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(0, 2, 0));
    }

    private static List<ItemStack> snapshot(Container container) {
        var stacks = new ArrayList<ItemStack>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            stacks.add(container.getItem(slot).copy());
        }
        return stacks;
    }

    private static boolean unchanged(Container container, List<ItemStack> before) {
        return ItemStack.listMatches(snapshot(container), before);
    }

    private static Map<Integer, Integer> moteCounts(Container container) {
        return MoteItem.countByGrade(VanillaContainerWrapper.of(container));
    }

    private static String describe(Container container) {
        return moteCounts(container).toString();
    }
}
