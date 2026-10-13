// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.command.Showcase;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlockEntity;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;

/**
 * Conduction and the Void Well, on a real server: motes crawl through Voidstone a step at a time to
 * where they are spent, and a Void Well, placed Dormant, joins its network once paid and soaks up
 * the network's motes. The tests stand in the Overworld.
 */
final class ConductionTests {

    private static final BlockPos WEST = new BlockPos(2, 1, 4);
    /** How many Voidstone the row is, from {@link #WEST} east. */
    private static final int ROW = 5;
    private static final BlockPos MACHINE = WEST.east(ROW);

    /** The Well's core, in the middle of its north wall's middle course, its floor on the platform. */
    private static final BlockPos WELL = new BlockPos(4, 3, 2);
    private static final BlockPos BESIDE_WELL = WELL.north();
    /** The Well's shipped cost, in void energy. */
    private static final long WELL_COST = 25_600;
    /** Grades above the pressure that pay the Well's cost in as many motes as one Voidstone holds. */
    private static final int WELL_STEPS = 5;

    private static final int TICKS = 400;
    /** Long enough for the showcase's motes to crawl its whole line. */
    private static final int SHOWCASE_TICKS = 600;

    private ConductionTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("motes_conduct_through_voidstone_to_a_dormant_machine_at_its_far_end", TICKS, ConductionTests::reachesTheFarEnd);
        tests.test("a_void_well_placed_dormant_soaks_its_cost_then_joins_its_network_and_soaks_up_its_motes", TICKS,
                ConductionTests::wellSoaksUp);
        tests.test("a_broken_void_well_loses_its_motes", TICKS, ConductionTests::brokenWellLosesItsMotes);
        tests.test("a_void_well_core_with_no_well_around_it_soaks_nothing", TICKS, ConductionTests::coreAloneSoaksNothing);
        tests.test("a_void_well_no_longer_whole_leaves_its_network_keeping_its_motes_until_it_is_whole_again", TICKS,
                ConductionTests::unwholeWellKeepsItsMotes);
        tests.test("the_void_well_has_a_recipe", 1, ConductionTests::hasARecipe);
        tests.test("the_showcase_s_motes_crawl_to_its_chamber_and_wake_it", SHOWCASE_TICKS, ConductionTests::showcaseWakesItsChamber);
    }

    /** Motes put in at the west end are not at the east at once, and reach the machine there in time. */
    private static void reachesTheFarEnd(GameTestHelper helper) {
        for (int x = 0; x < ROW; x++) {
            helper.setBlock(WEST.east(x), VoidworksBlocks.VOIDSTONE.get());
        }
        helper.setBlock(MACHINE, DormantTestMachine.BLOCK.get());
        int grade = DisplacerTests.pressure(helper, WEST) + 1;
        VoidstoneTests.insert(helper, WEST, grade, 10);
        helper.assertTrue(held(helper, MACHINE.west()).isEmpty(), "motes reached the far end at once: " + held(helper, MACHINE.west()));
        var machine = helper.getBlockEntity(MACHINE, DormantTestMachine.Entity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.dormancy().awake(), "the machine is still Dormant, paid " + machine.dormancy().paid());
            var motes = DisplacerTests.motes(helper, WEST);
            helper.assertTrue(motes.equals(Map.of(grade, 6)), "the network holds " + motes + ", not the 6 of 10 left after the machine's cost");
        });
    }

    private static void wellSoaksUp(GameTestHelper helper) {
        var well = placeWell(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(well.dormancy().awake(), "the Well is still Dormant, paid " + well.dormancy().paid()))
                .thenExecute(() -> {
                    helper.assertTrue(!helper.getBlockState(WELL).getValue(Dormancy.DORMANT), "the Well woke but still looks Dormant");
                    var networks = VoidstoneNetworks.of(helper.getLevel());
                    var store = networks.store(helper.absolutePos(WELL));
                    helper.assertTrue(store.isPresent() && store.get().kind() == StoreKind.WELL, "the awake Well is not in a network");
                    helper.assertTrue(networks.at(helper.absolutePos(WELL)).equals(networks.at(helper.absolutePos(BESIDE_WELL))),
                            "the Well did not join the network of the Voidstone it touches");
                    VoidstoneTests.insert(helper, BESIDE_WELL, DisplacerTests.pressure(helper, WELL) + 1, 10);
                })
                .thenWaitUntil(() -> {
                    var inWell = held(helper, WELL);
                    helper.assertTrue(inWell.values().stream().mapToInt(Integer::intValue).sum() == 9,
                            "the Well holds " + inWell + ", not the 9 of the network's 10 motes it soaks up");
                })
                .thenSucceed();
    }

    private static void brokenWellLosesItsMotes(GameTestHelper helper) {
        var well = placeWell(helper);
        int grade = DisplacerTests.pressure(helper, WELL) + 1;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(well.dormancy().awake(), "the Well is still Dormant"))
                .thenExecute(() -> VoidstoneTests.insert(helper, BESIDE_WELL, grade, 10))
                .thenWaitUntil(() -> helper.assertTrue(!held(helper, WELL).isEmpty(), "no motes reached the Well"))
                .thenExecute(() -> {
                    int left = held(helper, BESIDE_WELL).getOrDefault(grade, 0);
                    helper.setBlock(WELL, Blocks.AIR);
                    helper.assertTrue(VoidstoneNetworks.of(helper.getLevel()).store(helper.absolutePos(WELL)).isEmpty(),
                            "the broken Well is still in a network");
                    var motes = DisplacerTests.motes(helper, BESIDE_WELL);
                    helper.assertTrue(motes.equals(left == 0 ? Map.of() : Map.of(grade, left)),
                            "the network holds " + motes + ", not only the " + left + " motes the Voidstone held");
                })
                .thenSucceed();
    }

    /** The showcase fills the platform's width: its line from the west edge, its Chamber on the east. */
    private static void showcaseWakesItsChamber(GameTestHelper helper) {
        var start = new BlockPos(0, 1, 4);
        int motes = Showcase.build(helper.getLevel(), helper.absolutePos(start), Direction.EAST);
        helper.assertTrue(motes == Showcase.MOTES, "the showcase put in " + motes + " motes, not " + Showcase.MOTES);
        var chamber = helper.getBlockEntity(start.east(Showcase.LENGTH), MutationChamberBlockEntity.class);
        helper.succeedWhen(() -> helper.assertTrue(chamber.dormancy().awake(),
                "the showcase's Chamber is still Dormant, paid " + chamber.dormancy().paid()));
    }

    private static void coreAloneSoaksNothing(GameTestHelper helper) {
        var well = placeWell(helper, false);
        helper.startSequence()
                .thenIdle(3 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(well.dormancy().paid() == 0, "a core with no well around it was paid " + well.dormancy().paid());
                    helper.assertTrue(!well.whole(helper.getLevel()), "a core alone counts as a whole well");
                })
                .thenSucceed();
    }

    /** A wall casing taken out: the Well leaves with its motes; put back, it rejoins with them. */
    private static void unwholeWellKeepsItsMotes(GameTestHelper helper) {
        var well = placeWell(helper);
        int grade = DisplacerTests.pressure(helper, WELL) + 1;
        var wall = WELL.east();
        var held = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(well.dormancy().awake(), "the Well is still Dormant"))
                .thenExecute(() -> VoidstoneTests.insert(helper, BESIDE_WELL, grade, 10))
                .thenWaitUntil(() -> helper.assertTrue(held(helper, WELL).getOrDefault(grade, 0) == 9, "the Well holds " + held(helper, WELL)))
                .thenExecute(() -> {
                    held[0] = held(helper, WELL).get(grade);
                    helper.setBlock(wall, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(VoidstoneNetworks.of(helper.getLevel()).store(helper.absolutePos(WELL)).isEmpty(),
                        "the Well is no longer whole but still in a network"))
                .thenExecute(() -> helper.setBlock(wall, VoidworksBlocks.WELL_CASING.get()))
                .thenWaitUntil(() -> helper.assertTrue(held(helper, WELL).getOrDefault(grade, 0) == held[0],
                        "the whole Well again holds " + held(helper, WELL) + ", not the " + held[0] + " it kept"))
                .thenSucceed();
    }

    private static void hasARecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_well"));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no recipe " + key.identifier());
        helper.succeed();
    }

    private static VoidWellBlockEntity placeWell(GameTestHelper helper) {
        return placeWell(helper, true);
    }

    /**
     * A Dormant Well's core facing north beside a Voidstone holding exactly its cost, in motes
     * {@link #WELL_STEPS} above the pressure, and the well around it when {@code whole}.
     */
    private static VoidWellBlockEntity placeWell(GameTestHelper helper, boolean whole) {
        if (whole) {
            WellTests.buildWell(helper, WELL, Direction.NORTH);
        }
        helper.setBlock(BESIDE_WELL, VoidworksBlocks.VOIDSTONE.get());
        helper.setBlock(WELL, VoidworksBlocks.VOID_WELL.get().defaultBlockState().setValue(VoidWellBlock.FACING, Direction.NORTH));
        var well = helper.getBlockEntity(WELL, VoidWellBlockEntity.class);
        helper.assertTrue(well.dormancy().cost() == WELL_COST, "the Well's cost is " + well.dormancy().cost() + ", not the shipped " + WELL_COST);
        helper.assertTrue(helper.getBlockState(WELL).getValue(Dormancy.DORMANT), "a newly placed Well does not look Dormant");
        int grade = DisplacerTests.pressure(helper, WELL) + WELL_STEPS;
        long each = VoidEnergy.release(grade, DisplacerTests.pressure(helper, WELL)).orElseThrow();
        VoidstoneTests.insert(helper, BESIDE_WELL, grade, (int) (WELL_COST / each));
        return well;
    }

    /** What the store at {@code pos} holds itself, not its network. */
    private static Map<Integer, Integer> held(GameTestHelper helper, BlockPos pos) {
        return VoidstoneNetworks.of(helper.getLevel()).store(helper.absolutePos(pos)).map(VoidSpender::countByGrade).<Map<Integer, Integer>>map(m -> m)
                .orElse(Map.of());
    }
}
