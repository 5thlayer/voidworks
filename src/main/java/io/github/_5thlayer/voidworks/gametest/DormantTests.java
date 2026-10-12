// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * A void machine placed Dormant, on a real server: it soaks its cost from the mote stores touching
 * it, by the spend rule at its own block, and works once it is paid, for good. The machine is
 * {@link DormantTestMachine}, which opts into {@link Dormancy} as a Consumer's machine would, and
 * its cost is the game-test pack's {@link #COST}. The tests stand in the Overworld, where the grade
 * above the pressure releases {@link VoidEnergy#BASE} a mote.
 */
final class DormantTests {

    /** The test machine's cost, as the game-test pack's {@code dormant_cost} names it: four motes one step above the pressure. */
    private static final long COST = 4 * VoidEnergy.BASE;

    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    /** Where a mote store touches the machine. */
    private static final BlockPos BESIDE = MACHINE.east();

    /** Long enough for several soaks at the configured interval. */
    private static final int TICKS = 200;

    private DormantTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_dormant_machine_beside_motes_above_the_pressure_soaks_its_cost_and_wakes", TICKS, DormantTests::soaksAndWakes);
        tests.test("a_dormant_machine_beside_only_motes_at_or_below_the_pressure_stays_dormant", TICKS, DormantTests::staysDormantOnWorthlessMotes);
        tests.test("a_dormant_machine_keeps_a_partial_payment_across_a_save_and_reload_and_wakes_once_the_rest_arrives", TICKS,
                DormantTests::partialPaymentSurvivesAReload);
        tests.test("a_woken_machine_never_goes_back_to_dormant", TICKS, DormantTests::neverDormantAgain);
        tests.test("a_dormant_machine_soaks_its_cost_from_a_touching_voidstone_network", TICKS, DormantTests::soaksFromAVoidstoneNetwork);
    }

    private static void soaksAndWakes(GameTestHelper helper) {
        var machine = place(helper);
        int grade = pressure(helper) + 1;
        var store = new MoteStore().add(grade, 10);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.dormancy().awake(), "the machine is still Dormant, paid " + machine.dormancy().paid());
            helper.assertTrue(!dormantLook(helper), "the machine woke but still looks Dormant");
            helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade, 6)),
                    "the store holds " + VoidSpender.countByGrade(store) + ", not the 6 of 10 left after a cost of " + COST);
            helper.assertTrue(machine.worked() > 0, "the woken machine has not worked");
        });
    }

    /** Motes of the pressure's own grade, the lowest here, are worth nothing, so none is taken. */
    private static void staysDormantOnWorthlessMotes(GameTestHelper helper) {
        var machine = place(helper);
        int pressure = pressure(helper);
        var store = new MoteStore().add(pressure, 50);
        var before = store.snapshot();
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenIdle(3 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(!machine.dormancy().awake() && dormantLook(helper), "the machine woke on worthless motes");
                    helper.assertTrue(machine.dormancy().paid() == 0, "the machine was paid " + machine.dormancy().paid() + " in worthless motes");
                    helper.assertTrue(store.snapshot().equals(before), "the store changed: " + store.snapshot());
                    helper.assertTrue(machine.worked() == 0, "the Dormant machine worked");
                })
                .thenSucceed();
    }

    /** Three motes pay 300 of the 400; the fourth, put in after the reload, pays the rest. */
    private static void partialPaymentSurvivesAReload(GameTestHelper helper) {
        place(helper);
        int grade = pressure(helper) + 1;
        var store = new MoteStore().add(grade, 3);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VoidSpender.countByGrade(store).isEmpty(), "the machine has not soaked the three motes"))
                .thenExecute(() -> {
                    var reloaded = reload(helper);
                    helper.assertTrue(reloaded.dormancy().paid() == 3 * VoidEnergy.BASE,
                            "the reloaded machine was paid " + reloaded.dormancy().paid() + ", not the " + 3 * VoidEnergy.BASE + " soaked before");
                    helper.assertTrue(!reloaded.dormancy().awake() && dormantLook(helper), "a machine paid in part woke");
                    var status = Component.translatable("voidworks.dormant.status", 3 * VoidEnergy.BASE, COST);
                    helper.assertTrue(reloaded.dormancy().status().equals(status),
                            "the machine tells a player " + reloaded.dormancy().status() + ", not " + status);
                    store.add(grade, 1);
                })
                .thenWaitUntil(() -> {
                    var machine = machine(helper);
                    helper.assertTrue(machine.dormancy().awake() && !dormantLook(helper), "the machine did not wake once the rest arrived");
                    helper.assertTrue(VoidSpender.countByGrade(store).isEmpty(), "the store holds " + VoidSpender.countByGrade(store));
                })
                .thenSucceed();
    }

    /** Woken, reloaded, and given motes again: it takes none and keeps working. */
    private static void neverDormantAgain(GameTestHelper helper) {
        place(helper);
        int grade = pressure(helper) + 1;
        var store = new MoteStore().add(grade, 4);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine(helper).dormancy().awake(), "the machine has not woken"))
                .thenExecute(() -> {
                    var reloaded = reload(helper);
                    helper.assertTrue(reloaded.dormancy().awake(), "the reloaded machine is Dormant again");
                    store.add(grade, 5);
                })
                .thenIdle(3 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    var machine = machine(helper);
                    helper.assertTrue(machine.dormancy().awake() && !dormantLook(helper), "the woken machine went back to Dormant");
                    helper.assertTrue(machine.worked() > 0, "the reloaded machine has not worked");
                    helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade, 5)),
                            "the woken machine took motes: the store holds " + VoidSpender.countByGrade(store));
                })
                .thenSucceed();
    }

    private static void soaksFromAVoidstoneNetwork(GameTestHelper helper) {
        var machine = place(helper);
        int grade = pressure(helper) + 1;
        helper.setBlock(BESIDE, VoidworksBlocks.VOIDSTONE.get());
        VoidstoneTests.insert(helper, BESIDE, grade, 10);
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.dormancy().awake(), "the machine is still Dormant, paid " + machine.dormancy().paid());
            var motes = DisplacerTests.motes(helper, BESIDE);
            helper.assertTrue(motes.equals(Map.of(grade, 6)), "the network holds " + motes + ", not the 6 of 10 left after a cost of " + COST);
        });
    }

    private static DormantTestMachine.Entity place(GameTestHelper helper) {
        helper.setBlock(MACHINE, DormantTestMachine.BLOCK.get());
        var machine = machine(helper);
        helper.assertTrue(machine.dormancy().cost() == COST,
                "the machine's cost is " + machine.dormancy().cost() + ", not the game-test pack's " + COST);
        helper.assertTrue(dormantLook(helper), "a newly placed machine does not look Dormant");
        return machine;
    }

    private static DormantTestMachine.Entity machine(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, DormantTestMachine.Entity.class);
    }

    /** Saves the machine, removes it and puts it back from what was saved, as a chunk unloading and loading does. */
    private static DormantTestMachine.Entity reload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(MACHINE);
        var state = level.getBlockState(pos);
        var saved = machine(helper).saveWithFullMetadata(level.registryAccess());
        level.removeBlock(pos, false);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var reloaded = machine(helper);
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        return reloaded;
    }

    private static boolean dormantLook(GameTestHelper helper) {
        return helper.getBlockState(MACHINE).getValue(Dormancy.DORMANT);
    }

    private static int pressure(GameTestHelper helper) {
        return DisplacerTests.pressure(helper, MACHINE);
    }
}
