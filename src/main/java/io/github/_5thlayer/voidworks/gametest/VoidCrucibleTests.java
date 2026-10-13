// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Optional;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.mutation.CrucibleReport;
import io.github._5thlayer.voidworks.mutation.CrucibleTime;
import io.github._5thlayer.voidworks.mutation.VoidCrucibleBlockEntity;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Void Crucible on a real server: placed, it works at once with no motes, and upgrades what its
 * input holds to the next Rung of its Family, one result at a time, after the Rung's cost times the
 * configured ticks per void energy, halved per step of Void Pressure, and never fails. The Family is
 * the game-test pack's two snowballs → a snow block, for 1 void energy at a chance that never comes
 * up in a Mutation Chamber, so a result takes a few seconds here in the Overworld.
 */
final class VoidCrucibleTests {

    private static final BlockPos CRUCIBLE = new BlockPos(4, 1, 4);
    /** Within the sharing radius of {@link #CRUCIBLE}, not touching it. */
    private static final BlockPos NEAR = CRUCIBLE.east(3);
    private static final BlockPos ALSO_NEAR = CRUCIBLE.west(3);
    private static final BlockPos STILL_NEAR = CRUCIBLE.north(3);

    /** Long enough for a few snow blocks at the configured rate. */
    private static final int TICKS = 400;

    /** A few ticks either way, for the tick a Crucible starts and the one it finishes on. */
    private static final int SLACK = 5;

    private VoidCrucibleTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("void_crucible_works_at_once_with_no_motes_and_never_fails", TICKS, VoidCrucibleTests::worksAtOnce);
        tests.test("void_crucible_upgrades_after_the_rungs_cost_times_the_configured_ticks", TICKS, VoidCrucibleTests::upgradesInTime);
        tests.test("two_working_void_crucibles_near_each_other_each_take_twice_as_long", 2 * TICKS, VoidCrucibleTests::sharesItsRate);
        tests.test("a_stalled_or_empty_void_crucible_beside_one_does_not_slow_it", TICKS, VoidCrucibleTests::idleNeighboursDoNotShare);
        tests.test("void_crucible_starts_again_when_emptied_or_given_another_item", TICKS, VoidCrucibleTests::startsAgain);
        tests.test("void_crucible_with_a_full_output_waits_done", TICKS, VoidCrucibleTests::fullOutputHoldsItDone);
        tests.test("void_crucible_keeps_its_progress_across_a_save", TICKS, VoidCrucibleTests::keepsProgress);
        tests.test("void_crucible_shows_its_watchers_the_result_it_works_on", TICKS, VoidCrucibleTests::showsTheResult);
        tests.test("void_crucible_item_face_takes_only_upgradable_items_and_gives_only_the_output", 1, VoidCrucibleTests::itemFace);
        tests.test("void_crucible_takes_items_from_a_hand_and_gives_the_result_to_an_empty_one", 1, VoidCrucibleTests::byHand);
        tests.test("void_crucible_broken_drops_itself_and_what_it_holds", 20, VoidCrucibleTests::breakingDrops);
        tests.test("void_crucible_has_a_recipe", 1, VoidCrucibleTests::hasARecipe);
    }

    /** Placed with no mote store anywhere, it makes snow from snowballs whose Rung a Chamber would never roll. */
    private static void worksAtOnce(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        insert(crucible, Items.SNOWBALL, 4);
        helper.succeedWhen(() -> assertHolds(helper, crucible, Items.SNOWBALL, 0, new ItemStack(Items.SNOW_BLOCK, 2)));
    }

    /** Not before the Rung's time, and soon after it; the report shows the upgrade, the progress and the time left on the way. */
    private static void upgradesInTime(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        insert(crucible, Items.SNOWBALL, 3);
        int ticks = soloTicks(helper);
        helper.startSequence()
                .thenIdle(ticks / 2)
                .thenExecute(() -> {
                    var report = report(helper, crucible);
                    helper.assertTrue(report.next().isPresent() && report.next().get().result().is(Items.SNOW_BLOCK)
                            && report.next().get().count() == 2, "the Crucible reports " + report.next() + ", not two snowballs to a snow block");
                    helper.assertTrue(report.progress() > 0.3 && report.progress() < 0.7, "halfway, the Crucible is " + report.progress() + " done");
                    helper.assertTrue(Math.abs(report.ticksLeft() - ticks / 2) <= SLACK,
                            "halfway, the Crucible has " + report.ticksLeft() + " ticks left of " + ticks);
                    helper.assertTrue(report.sharing() == 1 && report.stall().isEmpty(), "a lone Crucible reports " + report);
                })
                .thenIdle(ticks / 2 - SLACK)
                .thenExecute(() -> assertHolds(helper, crucible, Items.SNOWBALL, 3, ItemStack.EMPTY))
                .thenExecuteAfter(2 * SLACK, () -> {
                    assertHolds(helper, crucible, Items.SNOWBALL, 1, new ItemStack(Items.SNOW_BLOCK));
                    var report = report(helper, crucible);
                    helper.assertTrue(report.stall().equals(Optional.of(VoidCrucibleBlockEntity.Stall.TOO_FEW)),
                            "with one snowball left the Crucible reports " + report.stall() + ", not too few");
                })
                .thenSucceed();
    }

    /** Two fed Crucibles three blocks apart: each is still working where one alone would be done, and both finish by twice its time. */
    private static void sharesItsRate(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        var near = place(helper, NEAR);
        insert(crucible, Items.SNOWBALL, 2);
        insert(near, Items.SNOWBALL, 2);
        int ticks = soloTicks(helper);
        helper.startSequence()
                .thenIdle(ticks + SLACK)
                .thenExecute(() -> {
                    helper.assertTrue(crucible.output().isEmpty() && near.output().isEmpty(), "two Crucibles near each other took one's time");
                    var report = report(helper, crucible);
                    helper.assertTrue(report.sharing() == 2, "the Crucible reports " + report.sharing() + " sharing its rate, not 2");
                })
                .thenIdle(ticks - 2 * SLACK)
                .thenExecute(() -> helper.assertTrue(crucible.output().isEmpty(), "two Crucibles near each other finished early"))
                .thenExecuteAfter(2 * SLACK, () -> {
                    assertHolds(helper, crucible, Items.SNOWBALL, 0, new ItemStack(Items.SNOW_BLOCK));
                    assertHolds(helper, near, Items.SNOWBALL, 0, new ItemStack(Items.SNOW_BLOCK));
                })
                .thenSucceed();
    }

    /** One near it holds too few snowballs, one sticks, which have no upgrade, and one nothing: none counts, so it takes one Crucible's time. */
    private static void idleNeighboursDoNotShare(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        insert(place(helper, NEAR), Items.SNOWBALL, 1);
        place(helper, ALSO_NEAR);
        place(helper, STILL_NEAR).inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.of(Items.STICK), 4);
        insert(crucible, Items.SNOWBALL, 2);
        helper.startSequence()
                .thenExecuteAfter(1, () -> helper.assertTrue(report(helper, crucible).sharing() == 1,
                        "stalled and empty Crucibles share its rate: " + report(helper, crucible).sharing()))
                .thenIdle(soloTicks(helper) + SLACK)
                .thenExecute(() -> assertHolds(helper, crucible, Items.SNOWBALL, 0, new ItemStack(Items.SNOW_BLOCK)))
                .thenSucceed();
    }

    /** What it has done belongs to what its input holds: emptied, or given clay balls, it starts again; more snowballs keep it going. */
    private static void startsAgain(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        insert(crucible, Items.SNOWBALL, 2);
        int ticks = soloTicks(helper);
        helper.startSequence()
                .thenIdle(ticks / 2)
                .thenExecute(() -> {
                    double before = crucible.progress();
                    crucible.inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.of(Items.SNOWBALL), 3);
                    helper.assertTrue(before > 0, "the Crucible made no progress");
                })
                .thenExecuteAfter(1, () -> helper.assertTrue(crucible.progress() > 0.4, "one more snowball started it again"))
                .thenExecute(() -> crucible.inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.of(Items.CLAY_BALL), 4))
                .thenExecuteAfter(1, () -> helper.assertTrue(crucible.progress() < 0.1, "given clay balls, it kept " + crucible.progress()))
                .thenIdle(ticks / 2)
                .thenExecute(() -> crucible.inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.EMPTY, 0))
                .thenExecuteAfter(1, () -> helper.assertTrue(crucible.progress() == 0, "emptied, it kept " + crucible.progress()))
                .thenExecute(() -> insert(crucible, Items.SNOWBALL, 2))
                .thenIdle(ticks - 2 * SLACK)
                .thenExecute(() -> helper.assertTrue(crucible.output().isEmpty(), "refilled, it finished on the old snowballs' progress"))
                .thenSucceed();
    }

    /** Its output full of dirt, it finishes the work and waits, done, and gives the result the tick the output has room. */
    private static void fullOutputHoldsItDone(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        crucible.inventory().set(VoidCrucibleBlockEntity.OUTPUT, ItemResource.of(Items.DIRT), 64);
        insert(crucible, Items.SNOWBALL, 2);
        helper.startSequence()
                .thenIdle(soloTicks(helper) + SLACK)
                .thenExecute(() -> {
                    helper.assertTrue(crucible.progress() >= 1, "with its output full it is " + crucible.progress() + " done, not done");
                    assertHolds(helper, crucible, Items.SNOWBALL, 2, new ItemStack(Items.DIRT, 64));
                    var report = report(helper, crucible);
                    helper.assertTrue(report.stall().equals(Optional.of(VoidCrucibleBlockEntity.Stall.OUTPUT_FULL)),
                            "the Crucible reports " + report.stall() + ", not a full output");
                    helper.assertTrue(report.ticksLeft() == 0, "done, it has " + report.ticksLeft() + " ticks left");
                    crucible.inventory().set(VoidCrucibleBlockEntity.OUTPUT, ItemResource.EMPTY, 0);
                })
                .thenExecuteAfter(2, () -> assertHolds(helper, crucible, Items.SNOWBALL, 0, new ItemStack(Items.SNOW_BLOCK)))
                .thenSucceed();
    }

    private static void keepsProgress(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        insert(crucible, Items.SNOWBALL, 2);
        int ticks = soloTicks(helper);
        helper.startSequence()
                .thenIdle(ticks / 2)
                .thenExecute(() -> {
                    double before = crucible.progress();
                    var reloaded = reload(helper);
                    helper.assertTrue(Math.abs(reloaded.progress() - before) < 0.05,
                            "reloaded, the Crucible is " + reloaded.progress() + " done, not the " + before + " saved");
                    assertHolds(helper, reloaded, Items.SNOWBALL, 2, ItemStack.EMPTY);
                })
                .thenIdle(ticks / 2 + SLACK)
                .thenExecute(() -> assertHolds(helper, crucible(helper, CRUCIBLE), Items.SNOWBALL, 0, new ItemStack(Items.SNOW_BLOCK)))
                .thenSucceed();
    }

    /**
     * The result floats above it while it works on it, glimmering, and holds still while it waits
     * with it, done, with no glimmer; nothing
     * floats over an empty input, one with too few, or one with no upgrade. What floats is in what
     * its watchers are sent.
     */
    private static void showsTheResult(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        crucible.inventory().set(VoidCrucibleBlockEntity.OUTPUT, ItemResource.of(Items.DIRT), 64);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertFloating(helper, crucible, ItemStack.EMPTY, "an empty Crucible");
                    helper.assertTrue(!crucible.glimmers(), "an empty Crucible glimmers");
                })
                .thenExecute(() -> insert(crucible, Items.SNOWBALL, 1))
                .thenExecuteAfter(1, () -> {
                    assertFloating(helper, crucible, ItemStack.EMPTY, "a Crucible with too few snowballs");
                    helper.assertTrue(!crucible.glimmers(), "a Crucible with too few snowballs glimmers");
                })
                .thenExecute(() -> insert(crucible, Items.STICK, 4))
                .thenExecuteAfter(1, () -> {
                    assertFloating(helper, crucible, ItemStack.EMPTY, "a Crucible holding sticks");
                    helper.assertTrue(!crucible.glimmers(), "a Crucible holding sticks glimmers");
                })
                .thenExecute(() -> insert(crucible, Items.SNOWBALL, 2))
                .thenExecuteAfter(1, () -> {
                    assertFloating(helper, crucible, new ItemStack(Items.SNOW_BLOCK), "a working Crucible");
                    helper.assertTrue(!crucible.floatingStill(), "a working Crucible holds its result still");
                    helper.assertTrue(crucible.glimmers(), "a working Crucible does not glimmer");
                    assertWatcherSees(helper, crucible, new ItemStack(Items.SNOW_BLOCK), false);
                })
                .thenIdle(soloTicks(helper) + SLACK)
                .thenExecute(() -> {
                    assertFloating(helper, crucible, new ItemStack(Items.SNOW_BLOCK), "a Crucible waiting, done,");
                    helper.assertTrue(crucible.floatingStill(), "a Crucible waiting, done, does not hold its result still");
                    helper.assertTrue(!crucible.glimmers(), "a Crucible waiting, done, glimmers");
                    assertWatcherSees(helper, crucible, new ItemStack(Items.SNOW_BLOCK), true);
                })
                .thenExecute(() -> crucible.inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.EMPTY, 0))
                .thenExecuteAfter(1, () -> assertFloating(helper, crucible, ItemStack.EMPTY, "an emptied Crucible"))
                .thenSucceed();
    }

    /** Reads what {@code crucible} sends its watchers into a Crucible of their own, and checks it floats {@code expected}, still or moving. */
    private static void assertWatcherSees(GameTestHelper helper, VoidCrucibleBlockEntity crucible, ItemStack expected, boolean still) {
        var sent = crucible.getUpdateTag(helper.getLevel().registryAccess());
        var watched = new VoidCrucibleBlockEntity(crucible.getBlockPos(), crucible.getBlockState());
        watched.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), sent));
        boolean glimmers = !expected.isEmpty() && !still;
        helper.assertTrue(watched.glimmers() == glimmers, glimmers ? "a watcher sees no glimmer over a working Crucible"
                : "a watcher sees a glimmer over a Crucible with nothing working");
        helper.assertTrue(watched.floatingStill() == still && ItemStack.matches(watched.floating(), expected),
                "a watcher is sent " + watched.floating() + (watched.floatingStill() ? " still" : " moving") + ", not " + expected
                        + (still ? " still" : " moving"));
    }

    private static void assertFloating(GameTestHelper helper, VoidCrucibleBlockEntity crucible, ItemStack expected, String which) {
        helper.assertTrue(ItemStack.matches(crucible.floating(), expected), which + " shows " + crucible.floating() + " floating, not " + expected);
    }

    private static void itemFace(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        var face = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(CRUCIBLE), Direction.UP);
        helper.assertTrue(face != null, "the Crucible has no item capability");
        for (var side : Direction.values()) {
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(CRUCIBLE), side) == face,
                    "the Crucible's item face differs on its " + side + " side");
        }
        crucible.inventory().set(VoidCrucibleBlockEntity.OUTPUT, ItemResource.of(Items.SNOW_BLOCK), 2);
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(face.insert(VoidCrucibleBlockEntity.INPUT, ItemResource.of(Items.STICK), 1, transaction) == 0,
                    "the face took a stick, which has no upgrade");
            helper.assertTrue(face.insert(VoidCrucibleBlockEntity.OUTPUT, ItemResource.of(Items.SNOWBALL), 1, transaction) == 0,
                    "the face put a snowball into its output");
            helper.assertTrue(face.insert(VoidCrucibleBlockEntity.INPUT, ItemResource.of(Items.SNOWBALL), 4, transaction) == 4,
                    "the face did not take four snowballs");
            helper.assertTrue(face.extract(VoidCrucibleBlockEntity.INPUT, ItemResource.of(Items.SNOWBALL), 4, transaction) == 0,
                    "the face gave from its input");
            helper.assertTrue(face.extract(VoidCrucibleBlockEntity.OUTPUT, ItemResource.of(Items.SNOW_BLOCK), 2, transaction) == 2,
                    "the face did not give its output's two snow blocks");
            transaction.commit();
        }
        assertHolds(helper, crucible, Items.SNOWBALL, 4, ItemStack.EMPTY);
        helper.succeed();
    }

    private static void byHand(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var level = helper.getLevel();
        var pos = helper.absolutePos(CRUCIBLE);
        var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        var state = level.getBlockState(pos);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 3));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().getCount() == 3 && crucible.input().isEmpty(), "the Crucible took a stick, which has no upgrade");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL, 5));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the hand still holds " + player.getMainHandItem());
        assertHolds(helper, crucible, Items.SNOWBALL, 5, ItemStack.EMPTY);

        crucible.inventory().set(VoidCrucibleBlockEntity.OUTPUT, ItemResource.of(Items.SNOW_BLOCK), 2);
        state.useWithoutItem(level, player, hit);
        helper.assertTrue(player.getInventory().countItem(Items.SNOW_BLOCK) == 2, "the empty hand was not given the two snow blocks");
        player.getInventory().clearContent();

        state.useWithoutItem(level, player, hit);
        helper.assertTrue(player.getInventory().countItem(Items.SNOWBALL) == 5, "the empty hand was not given back the snowballs");
        helper.assertTrue(crucible.input().isEmpty(), "the input still holds " + crucible.input());
        helper.succeed();
    }

    private static void breakingDrops(GameTestHelper helper) {
        var crucible = place(helper, CRUCIBLE);
        insert(crucible, Items.SNOWBALL, 3);
        crucible.inventory().set(VoidCrucibleBlockEntity.OUTPUT, ItemResource.of(Items.SNOW_BLOCK), 1);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        var pos = helper.absolutePos(CRUCIBLE);
        helper.getLevel().destroyBlock(pos, true, player);
        helper.runAfterDelay(1, () -> {
            for (var expected : new Item[] {VoidworksBlocks.VOID_CRUCIBLE_ITEM.get(), Items.SNOWBALL, Items.SNOW_BLOCK}) {
                var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), item -> item.getItem().is(expected));
                helper.assertTrue(!dropped.isEmpty(), "breaking the Crucible did not drop " + expected);
            }
            helper.succeed();
        });
    }

    private static void hasARecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_crucible"));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no recipe " + key.identifier());
        helper.succeed();
    }

    // -- helpers --------------------------------------------------------------------------------

    private static VoidCrucibleBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, VoidworksBlocks.VOID_CRUCIBLE.get());
        return crucible(helper, pos);
    }

    private static VoidCrucibleBlockEntity crucible(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, VoidCrucibleBlockEntity.class);
    }

    /** Saves it, takes it away with nothing to drop, puts it back and loads what was saved. */
    private static VoidCrucibleBlockEntity reload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CRUCIBLE);
        var state = level.getBlockState(pos);
        var crucible = crucible(helper, CRUCIBLE);
        var saved = crucible.saveWithFullMetadata(level.registryAccess());
        crucible.inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.EMPTY, 0);
        level.removeBlock(pos, false);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var reloaded = crucible(helper, CRUCIBLE);
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        return reloaded;
    }

    private static CrucibleReport report(GameTestHelper helper, VoidCrucibleBlockEntity crucible) {
        return CrucibleReport.of(crucible, helper.getLevel());
    }

    /** The ticks one snow block takes a lone Crucible here: its 1 void energy at the configured rate and the pressure where it stands. */
    private static int soloTicks(GameTestHelper helper) {
        int pressure = VoidPressure.at(helper.getLevel(), helper.absolutePos(CRUCIBLE));
        return (int) Math.ceil(new CrucibleTime(VoidworksConfig.crucibleTicks()).ticks(1, pressure, 1));
    }

    private static void insert(VoidCrucibleBlockEntity crucible, Item item, int count) {
        crucible.inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.of(item), count);
    }

    private static void assertHolds(GameTestHelper helper, VoidCrucibleBlockEntity crucible, Item input, int count, ItemStack output) {
        var held = crucible.input();
        helper.assertTrue(count == 0 ? held.isEmpty() : held.is(input) && held.getCount() == count,
                "the input holds " + held + ", not " + count + " " + input);
        helper.assertTrue(ItemStack.matches(crucible.output(), output), "the output holds " + crucible.output() + ", not " + output);
    }
}
