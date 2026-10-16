// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksCapabilities;
import net.minecraft.server.level.ServerLevel;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import java.util.TreeMap;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.displacer.DisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.DisplacerReport;
import io.github._5thlayer.voidworks.displacer.OverworldDisplacerBlockEntity;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Overworld Void Displacer on a real server, standing in the middle of the platform's stone
 * floor: what it harvests and in what order, what it refuses, what stalls it, and how it ends as
 * Voidstone. Each test gives it a Zone small enough to stay on its own floor. A harvest's motes are
 * read through the {@code voidworks:motes} of the Voidstone it leaves.
 */
final class DisplacerTests {

    /** On the floor, in the middle: the floor around the Voidstone below it is nearer than what lies beneath. */
    private static final BlockPos DISPLACER = new BlockPos(4, 1, 4);
    private static final BlockPos BELOW = DISPLACER.below();

    /** Long enough for a few harvests at the configured interval. */
    private static final int TICKS = 400;

    private DisplacerTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_displacer_swaps_a_fed_block_for_the_terrain_beside_it_leaving_voidstone_and_motes", TICKS, DisplacerTests::firstHarvest);
        tests.test("a_displacer_harvests_nearest_first_and_its_spent_zone_becomes_voidstone_keeping_its_motes", TICKS, DisplacerTests::nearestFirstUntilSpent);
        tests.test("a_displacer_on_grass_harvests_the_grass", TICKS, DisplacerTests::harvestsGrass);
        tests.test("a_displacer_that_can_reach_no_terrain_before_its_first_harvest_pops_off", TICKS, DisplacerTests::popsOffNeverHarvested);
        tests.test("a_displacer_that_can_reach_no_more_terrain_becomes_voidstone_keeping_its_motes", TICKS, DisplacerTests::exhausted);
        tests.test("a_spent_displacer_turns_to_voidstone_once_a_hopper_has_pulled_its_last_harvest", TICKS, DisplacerTests::hopperTakesTheLastHarvest);
        tests.test("a_displacers_item_face_takes_only_a_density_above_0_and_gives_only_its_output", 1, DisplacerTests::itemFace);
        tests.test("a_displacer_whose_output_is_full_stalls", TICKS, DisplacerTests::fullOutputStalls);
        tests.test("a_displacer_whose_network_cannot_take_the_harvest_stalls", TICKS, DisplacerTests::fullNetworkStalls);
        tests.test("a_displacer_and_voidstone_cannot_be_broken_or_moved", 40, DisplacerTests::cannotBeBrokenOrMoved);
        tests.test("a_displacers_output_gets_what_the_displacement_table_gives_for_the_terrain_and_density", TICKS, DisplacerTests::displacementTable);
        tests.test("a_displacers_output_gets_the_terrain_block_for_a_density_the_table_does_not_name", TICKS, DisplacerTests::displacementTableDefault);
        tests.test("a_displacer_where_the_harvest_kind_is_not_overworld_harvests_nothing_and_pops_off", 1, DisplacerTests::refusesOutsideTheOverworldKind);
        tests.test("an_empty_hand_takes_back_a_displacers_fed_blocks", 1, DisplacerTests::givesBackFed);
        tests.test("an_empty_hand_takes_a_displacers_output_before_its_fed_blocks", 1, DisplacerTests::givesOutputFirst);
        tests.test("a_displacers_report_names_its_fed_block_and_the_harvests_left_in_its_zone", TICKS, DisplacerTests::report);
    }

    private static void firstHarvest(GameTestHelper helper) {
        var displacer = place(helper, DISPLACER, 2);
        feed(displacer, Items.COPPER_BLOCK, 1);
        int grade = pressure(helper, BELOW) + 1;
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), BELOW);
            helper.assertTrue(displacer.inventory().getAmountAsInt(OverworldDisplacerBlockEntity.INPUT) == 0, "the fed copper block was not taken");
            helper.assertTrue(output(displacer, Items.STONE) == 1, "the output holds " + output(displacer, Items.STONE) + " stone, not the 1 swapped out");
            var motes = motes(helper, BELOW);
            helper.assertTrue(motes.equals(Map.of(grade, VoidworksConfig.motes(1))),
                    "the network holds " + motes + ", not a copper block's " + VoidworksConfig.motes(1) + " motes of grade " + grade);
            helper.assertBlockPresent(VoidworksBlocks.OVERWORLD_DISPLACER.get(), DISPLACER);
            helper.assertBlockProperty(BELOW, VoidstoneBlock.KIND, HarvestKind.OVERWORLD);
        });
    }

    /** Below it first, then the four floor positions around that, never what lies beneath them nor a corner. */
    private static void nearestFirstUntilSpent(GameTestHelper helper) {
        var displacer = place(helper, DISPLACER, 5);
        feed(displacer, Items.COPPER_BLOCK, 6);
        int grade = pressure(helper, BELOW) + 1;
        var floor = new BlockPos[] {BELOW, BELOW.north(), BELOW.south(), BELOW.east(), BELOW.west()};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(output(displacer, Items.STONE) == 5, "the output holds " + output(displacer, Items.STONE) + " stone, not five"))
                .thenIdle(3 * VoidworksConfig.harvestInterval())
                .thenExecute(() -> {
                    helper.assertBlockPresent(VoidworksBlocks.OVERWORLD_DISPLACER.get(), DISPLACER);
                    emptyOutput(displacer);
                })
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), DISPLACER);
                    for (var pos : floor) {
                        helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), pos);
                    }
                    helper.assertBlockNotPresent(VoidworksBlocks.VOIDSTONE.get(), BELOW.below());
                    helper.assertBlockPresent(Blocks.STONE, BELOW.north().east());
                    helper.assertTrue(displacer.grown().getFirst().equals(helper.absolutePos(BELOW)),
                            "it harvested " + displacer.grown().getFirst() + " first, not the position below it");
                    var motes = motes(helper, DISPLACER);
                    helper.assertTrue(motes.equals(Map.of(grade, 5 * VoidworksConfig.motes(1))),
                            "the network holds " + motes + ", not five harvests' motes of grade " + grade);
                    // What it held when it went: the sixth copper block. Its five stone were taken first.
                    helper.assertItemEntityPresent(Items.COPPER_BLOCK, DISPLACER, 2);
                    helper.assertItemEntityNotPresent(Items.STONE, DISPLACER, 2);
                })
                .thenSucceed();
    }

    /**
     * Grass is natural terrain: 26.1's {@code #minecraft:dirt} no longer names it, its substrate tag
     * does. The Displacer floats beside the grass, not on it: grass under an opaque block turns to
     * dirt on a random tick, and it would harvest that dirt instead.
     */
    private static void harvestsGrass(GameTestHelper helper) {
        var grass = new BlockPos(4, 2, 4);
        var beside = grass.east();
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        var displacer = place(helper, beside, 2);
        feed(displacer, Items.COPPER_BLOCK, 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), grass);
            helper.assertTrue(output(displacer, Items.GRASS_BLOCK) == 1, "the output holds no grass");
        });
    }

    /** Floating in the air above the floor, touching nothing: it pops off as its item with its fed block, leaving no Voidstone. */
    private static void popsOffNeverHarvested(GameTestHelper helper) {
        var floating = new BlockPos(4, 3, 4);
        var displacer = place(helper, floating, 64);
        feed(displacer, Items.COPPER_BLOCK, 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, floating);
            helper.assertItemEntityPresent(VoidworksBlocks.OVERWORLD_DISPLACER_ITEM.get(), floating, 2);
            helper.assertItemEntityPresent(Items.COPPER_BLOCK, floating, 2);
        });
    }

    /** Floating above the floor over a single stone: once that is harvested, nothing touches it or its Voidstone. */
    private static void exhausted(GameTestHelper helper) {
        var stone = new BlockPos(4, 2, 4);
        var above = stone.above();
        helper.setBlock(stone, Blocks.STONE);
        var displacer = place(helper, above, 64);
        feed(displacer, Items.COPPER_BLOCK, 2);
        int grade = pressure(helper, stone) + 1;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), stone))
                .thenIdle(3 * VoidworksConfig.harvestInterval())
                .thenExecute(() -> {
                    helper.assertBlockPresent(VoidworksBlocks.OVERWORLD_DISPLACER.get(), above);
                    helper.assertTrue(output(displacer, Items.STONE) == 1, "the spent Displacer's output holds " + output(displacer, Items.STONE) + " stone, not the 1 it waits on");
                    var stall = DisplacerReport.of(displacer, helper.getLevel()).stall();
                    helper.assertTrue(stall.equals(Optional.of(DisplacerBlockEntity.Stall.OUTPUT_UNEMPTIED)), "it reports " + stall + ", not waiting on its output");
                    emptyOutput(displacer);
                })
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), above);
                    // Its body joined the network of the Voidstone it left, which kept the harvest's motes.
                    var motes = motes(helper, above);
                    helper.assertTrue(motes.equals(Map.of(grade, VoidworksConfig.motes(1))), "the network holds " + motes + " after its one harvest");
                    helper.assertItemEntityPresent(Items.COPPER_BLOCK, above, 2);
                })
                .thenSucceed();
    }

    private static void itemFace(GameTestHelper helper) {
        var displacer = place(helper, DISPLACER, 1);
        var face = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(DISPLACER), Direction.UP);
        helper.assertTrue(face != null, "the Displacer has no item capability");
        displacer.inventory().set(1, ItemResource.of(Items.DIRT), 2);
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(face.insert(ItemResource.of(Items.COBBLESTONE), 4, transaction) == 0, "the input took cobblestone, of Density 0");
            helper.assertTrue(face.insert(ItemResource.of(Items.STONE), 4, transaction) == 0, "the input took stone, of Density 0");
            helper.assertTrue(face.insert(ItemResource.of(Items.DIRT), 4, transaction) == 0, "the output took dirt in");
            helper.assertTrue(face.insert(ItemResource.of(Items.IRON_BLOCK), 4, transaction) == 4, "the input refused iron blocks");
            transaction.commit();
        }
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(face.extract(ItemResource.of(Items.IRON_BLOCK), 4, transaction) == 0, "the input gave back what was fed");
            helper.assertTrue(face.extract(ItemResource.of(Items.DIRT), 4, transaction) == 2, "the output did not give its dirt");
            transaction.commit();
        }
        helper.assertTrue(displacer.inventory().getAmountAsInt(OverworldDisplacerBlockEntity.INPUT) == 4, "the input does not hold the 4 iron blocks");
        helper.succeed();
    }

    private static void fullOutputStalls(GameTestHelper helper) {
        var displacer = place(helper, DISPLACER, 2);
        for (int slot = 1; slot <= OverworldDisplacerBlockEntity.OUTPUT_SLOTS; slot++) {
            displacer.inventory().set(slot, ItemResource.of(Items.DIRT), 64);
        }
        feed(displacer, Items.COPPER_BLOCK, 1);
        assertStalled(helper, displacer, Items.COPPER_BLOCK, DisplacerBlockEntity.Stall.OUTPUT_FULL);
    }

    /** A fresh network holds one Voidstone's motes; a diamond block yields more than that. */
    private static void fullNetworkStalls(GameTestHelper helper) {
        int motes = VoidworksConfig.motes(4);
        helper.assertTrue(motes > VoidworksConfig.voidstoneCapacity(), "this test needs a diamond block's " + motes
                + " motes to be more than a Voidstone holds, " + VoidworksConfig.voidstoneCapacity());
        var displacer = place(helper, DISPLACER, 2);
        feed(displacer, Items.DIAMOND_BLOCK, 1);
        assertStalled(helper, displacer, Items.DIAMOND_BLOCK, DisplacerBlockEntity.Stall.NETWORK_FULL);
    }

    private static void assertStalled(GameTestHelper helper, OverworldDisplacerBlockEntity displacer, Item fed, DisplacerBlockEntity.Stall stall) {
        helper.runAfterDelay(3L * VoidworksConfig.harvestInterval() + 5, () -> {
            helper.assertBlockPresent(Blocks.STONE, BELOW);
            helper.assertBlockPresent(VoidworksBlocks.OVERWORLD_DISPLACER.get(), DISPLACER);
            helper.assertTrue(displacer.inventory().getResource(OverworldDisplacerBlockEntity.INPUT).is(fed)
                    && displacer.inventory().getAmountAsInt(OverworldDisplacerBlockEntity.INPUT) == 1, "the fed block was taken");
            var report = DisplacerReport.of(displacer, helper.getLevel());
            helper.assertTrue(report.stall().equals(Optional.of(stall)), "it reports " + report.stall() + ", not stalled by " + stall);
            helper.succeed();
        });
    }

    /** A survival player makes no progress breaking either, and a piston pushes neither. */
    private static void cannotBeBrokenOrMoved(GameTestHelper helper) {
        var voidstone = new BlockPos(4, 1, 1);
        place(helper, DISPLACER, 64);
        helper.setBlock(voidstone, VoidworksBlocks.VOIDSTONE.get());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (var pos : new BlockPos[] {DISPLACER, voidstone}) {
            var absolute = helper.absolutePos(pos);
            float progress = helper.getLevel().getBlockState(absolute).getDestroyProgress(player, helper.getLevel(), absolute);
            helper.assertTrue(progress == 0, "a survival player makes " + progress + " progress a tick breaking " + helper.getLevel().getBlockState(absolute));
            // A piston to the west, pushing east, powered from behind.
            helper.setBlock(pos.west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
            helper.setBlock(pos.west(2), Blocks.REDSTONE_BLOCK);
        }
        helper.runAfterDelay(10, () -> {
            helper.assertBlockPresent(VoidworksBlocks.OVERWORLD_DISPLACER.get(), DISPLACER);
            helper.assertBlockPresent(Blocks.AIR, DISPLACER.east());
            helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), voidstone);
            helper.assertBlockPresent(Blocks.AIR, voidstone.east());
            helper.succeed();
        });
    }

    /** The game-test pack's table gives two flint for tuff displaced by a block of Density 1. */
    private static void displacementTable(GameTestHelper helper) {
        helper.setBlock(BELOW, Blocks.TUFF);
        var displacer = place(helper, DISPLACER, 1);
        feed(displacer, Items.COPPER_BLOCK, 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), BELOW);
            helper.assertTrue(output(displacer, Items.FLINT) == 2, "the output holds " + output(displacer, Items.FLINT) + " flint, not the table's 2");
            helper.assertTrue(output(displacer, Items.TUFF) == 0, "the output holds the tuff itself");
        });
    }

    /** The pack's tuff entry names Density 1 only, so an iron block, of Density 2, gives the tuff. */
    private static void displacementTableDefault(GameTestHelper helper) {
        helper.setBlock(BELOW, Blocks.TUFF);
        var displacer = place(helper, DISPLACER, 1);
        feed(displacer, Items.IRON_BLOCK, 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), BELOW);
            helper.assertTrue(output(displacer, Items.TUFF) == 1, "the output holds " + output(displacer, Items.TUFF) + " tuff, not the 1 swapped out");
            helper.assertTrue(output(displacer, Items.FLINT) == 0, "the output holds the table's flint for another Density");
        });
    }

    /**
     * In the Nether, whose harvest kind is {@code nether}, on a stone floor of its own: it harvests
     * nothing and pops off as its item with its fed block, so it never stands there unbreakable. The
     * game test server ticks no block entity in the Nether, even in a force-loaded chunk, so the test
     * ticks it itself.
     */
    private static void refusesOutsideTheOverworldKind(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var pos = new BlockPos(8, 100, 8);
        var stone = pos.below();
        // The Nether loads no entities here, so its drops are caught as they are added.
        var dropped = new ArrayList<Item>();
        Consumer<EntityJoinLevelEvent> drops = event -> {
            if (event.getLevel() == nether && event.getEntity() instanceof ItemEntity item) {
                dropped.add(item.getItem().getItem());
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(drops);
        nether.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        nether.setBlockAndUpdate(pos, VoidworksBlocks.OVERWORLD_DISPLACER.get().defaultBlockState());
        try {
            var displacer = (OverworldDisplacerBlockEntity) nether.getBlockEntity(pos);
            displacer.setZone(2);
            displacer.inventory().set(OverworldDisplacerBlockEntity.INPUT, ItemResource.of(Items.COPPER_BLOCK), 1);
            displacer.serverTick(nether);
            helper.assertTrue(nether.getBlockState(pos).isAir(), "it still stands in the Nether: " + nether.getBlockState(pos));
            helper.assertTrue(nether.getBlockState(stone).is(Blocks.STONE), "it harvested the stone below it: " + nether.getBlockState(stone));
            helper.assertTrue(dropped.contains(VoidworksBlocks.OVERWORLD_DISPLACER_ITEM.get()) && dropped.contains(Items.COPPER_BLOCK),
                    "it should drop itself and its fed block, dropped " + dropped);
        } finally {
            NeoForge.EVENT_BUS.unregister(drops);
            nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            nether.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /** Fed a diamond block its fresh network cannot hold the motes of, it stalls; an empty hand takes the block back. */
    private static void givesBackFed(GameTestHelper helper) {
        var displacer = place(helper, DISPLACER, 2);
        feed(displacer, Items.DIAMOND_BLOCK, 3);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(displacer.inventory().getAmountAsInt(OverworldDisplacerBlockEntity.INPUT) == 0, "the input still holds its fed blocks");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_BLOCK) == 3,
                "the player got " + player.getInventory().countItem(Items.DIAMOND_BLOCK) + " diamond blocks back, not 3");
        helper.succeed();
    }

    /** One use takes the output, the fed blocks staying; the next, with the output empty, the fed blocks. */
    private static void givesOutputFirst(GameTestHelper helper) {
        var displacer = place(helper, DISPLACER, 2);
        feed(displacer, Items.DIAMOND_BLOCK, 3);
        displacer.inventory().set(1, ItemResource.of(Items.STONE), 5);
        displacer.inventory().set(2, ItemResource.of(Items.FLINT), 2);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(output(displacer, Items.STONE) == 0 && output(displacer, Items.FLINT) == 0, "the output still holds what it gave");
        helper.assertTrue(player.getInventory().countItem(Items.STONE) == 5 && player.getInventory().countItem(Items.FLINT) == 2,
                "the player got " + player.getInventory().countItem(Items.STONE) + " stone and " + player.getInventory().countItem(Items.FLINT) + " flint, not 5 and 2");
        helper.assertTrue(displacer.inventory().getAmountAsInt(OverworldDisplacerBlockEntity.INPUT) == 3, "the fed blocks went with the output");
        // What it gave fills the selected slot first: the hand is emptied for the second use.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_BLOCK) == 3, "the second use gave back no fed blocks");
        helper.succeed();
    }

    /** Zone 5, fed three copper blocks: once it has harvested below it, two are left and four harvests, and nothing stalls it. */
    private static void report(GameTestHelper helper) {
        var displacer = place(helper, DISPLACER, 5);
        feed(displacer, Items.COPPER_BLOCK, 3);
        var before = DisplacerReport.of(displacer, helper.getLevel());
        helper.assertTrue(before.harvestsLeft() == 5, "it reports " + before.harvestsLeft() + " harvests left before its first, not 5");
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), BELOW);
            var report = DisplacerReport.of(displacer, helper.getLevel());
            helper.assertTrue(report.fed().is(Items.COPPER_BLOCK) && report.fed().getCount() == 2,
                    "it reports " + report.fed() + " fed, not the 2 copper blocks left");
            helper.assertTrue(report.harvestsLeft() == 4, "it reports " + report.harvestsLeft() + " harvests left, not 4");
            helper.assertTrue(report.stall().isEmpty(), "it reports stalled by " + report.stall());
        });
    }

    private static OverworldDisplacerBlockEntity place(GameTestHelper helper, BlockPos pos, int zone) {
        helper.setBlock(pos, VoidworksBlocks.OVERWORLD_DISPLACER.get());
        var displacer = helper.getBlockEntity(pos, OverworldDisplacerBlockEntity.class);
        displacer.setZone(zone);
        return displacer;
    }

    private static void feed(OverworldDisplacerBlockEntity displacer, Item item, int count) {
        displacer.inventory().set(OverworldDisplacerBlockEntity.INPUT, ItemResource.of(item), count);
    }

    private static void emptyOutput(OverworldDisplacerBlockEntity displacer) {
        for (int slot = 1; slot <= OverworldDisplacerBlockEntity.OUTPUT_SLOTS; slot++) {
            displacer.inventory().set(slot, ItemResource.EMPTY, 0);
        }
    }

    /** A hopper under it pulls every harvest, the last one too: it becomes Voidstone only once its output is empty. */
    private static void hopperTakesTheLastHarvest(GameTestHelper helper) {
        var hopper = new BlockPos(4, 1, 4);
        var above = hopper.above();
        var stone = above.east();
        helper.setBlock(hopper, Blocks.HOPPER);
        helper.setBlock(stone, Blocks.STONE);
        var displacer = place(helper, above, 1);
        feed(displacer, Items.COPPER_BLOCK, 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), above);
            helper.assertContainerContains(hopper, Items.STONE);
            helper.assertItemEntityNotPresent(Items.STONE, above, 3);
        });
    }

    /** How many of {@code item} the output holds, over all its slots. */
    private static int output(OverworldDisplacerBlockEntity displacer, Item item) {
        int count = 0;
        for (int slot = 1; slot <= OverworldDisplacerBlockEntity.OUTPUT_SLOTS; slot++) {
            if (displacer.inventory().getResource(slot).is(item)) {
                count += displacer.inventory().getAmountAsInt(slot);
            }
        }
        return count;
    }

    static int pressure(GameTestHelper helper, BlockPos pos) {
        return VoidPressure.at(helper.getLevel(), helper.absolutePos(pos));
    }

    /** The motes, grade to count, of the network of the Voidstone at {@code pos}. */
    static Map<Integer, Integer> motes(GameTestHelper helper, BlockPos pos) {
        return networkMotes(helper.getLevel(), helper.absolutePos(pos));
    }

    /** The motes, grade to count, of the network at the absolute {@code pos}, in every store of it together. */
    static Map<Integer, Integer> networkMotes(ServerLevel level, BlockPos pos) {
        var counts = new TreeMap<Integer, Integer>();
        VoidstoneNetworks.of(level).at(pos).ifPresent(network -> network.countByGrade()
                .forEach((grade, count) -> counts.put(grade, Math.toIntExact(count))));
        return counts;
    }
}
