// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Mutation Chamber on a real server: placed Dormant, it soaks Voidworks' shipped cost, then
 * upgrades what its input holds to the next Rung of its Family, taking the Rung's count of items
 * for one result and spending the Rung's cost from a touching mote store by the spend rule at its
 * block; a failed roll keeps the items and spends the motes. The Families are the game-test pack's:
 * clay ball → brick, four to one for 300 void energy at a sure chance, and wheat seeds → melon
 * seeds, one to one for 100 at a chance that never comes up. The tests stand in the Overworld,
 * where the grade above the pressure releases {@link VoidEnergy#BASE} a mote.
 */
final class MutationChamberTests {

    /** The Chamber's shipped Dormant cost, in motes one step above the pressure. */
    private static final int DORMANT_MOTES = 16;
    /** Clay ball → brick's cost, in motes one step above the pressure. */
    private static final int BRICK_MOTES = 3;

    private static final BlockPos CHAMBER = new BlockPos(4, 1, 4);
    /** Where a mote store touches the Chamber. */
    private static final BlockPos BESIDE = CHAMBER.east();

    /** Long enough to wake and make several upgrades at the configured intervals. */
    private static final int TICKS = 300;

    private MutationChamberTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("mutation_chamber_stays_dormant_until_its_shipped_cost_is_paid", TICKS, MutationChamberTests::staysDormantUntilPaid);
        tests.test("mutation_chamber_upgrades_by_the_rungs_count_and_cost", TICKS, MutationChamberTests::upgradesByCountAndCost);
        tests.test("mutation_chamber_keeps_the_items_and_spends_the_motes_on_a_failed_roll", TICKS, MutationChamberTests::failedRoll);
        tests.test("mutation_chamber_without_motes_enough_for_the_rung_spends_nothing", TICKS, MutationChamberTests::cannotAfford);
        tests.test("mutation_chamber_with_an_output_that_cannot_take_the_result_spends_nothing", TICKS,
                MutationChamberTests::outputCannotTakeTheResult);
        tests.test("mutation_chamber_item_face_takes_only_upgradable_items_and_gives_only_the_output", 1, MutationChamberTests::itemFace);
        tests.test("mutation_chamber_takes_items_from_a_hand_and_gives_the_result_to_an_empty_one", 1, MutationChamberTests::byHand);
        tests.test("mutation_chamber_broken_drops_itself_and_what_it_holds", 20, MutationChamberTests::breakingDrops);
        tests.test("mutation_chamber_has_a_recipe", 1, MutationChamberTests::hasARecipe);
    }

    /** Ten motes of the sixteen it costs: it soaks them, and upgrades nothing. */
    private static void staysDormantUntilPaid(GameTestHelper helper) {
        var chamber = place(helper);
        helper.assertTrue(chamber.dormancy().cost() == DORMANT_MOTES * VoidEnergy.BASE,
                "the Chamber's cost is " + chamber.dormancy().cost() + ", not the shipped " + DORMANT_MOTES * VoidEnergy.BASE);
        helper.assertTrue(dormantLook(helper), "a newly placed Chamber does not look Dormant");
        insert(chamber, Items.CLAY_BALL, 4);
        var store = new MoteStore().add(grade(helper), 10);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenIdle(4 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(!chamber.dormancy().awake() && dormantLook(helper), "the Chamber woke paid " + chamber.dormancy().paid());
                    helper.assertTrue(chamber.dormancy().paid() == 10 * VoidEnergy.BASE, "the Chamber was paid " + chamber.dormancy().paid());
                    assertHolds(helper, chamber, Items.CLAY_BALL, 4, ItemStack.EMPTY);
                })
                .thenSucceed();
    }

    /** Nine clay balls make two bricks, four each, and the ninth waits for three more. */
    private static void upgradesByCountAndCost(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.CLAY_BALL, 9);
        int grade = grade(helper);
        var store = new MoteStore().add(grade, DORMANT_MOTES + 2 * BRICK_MOTES + 2);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenWaitUntil(() -> assertHolds(helper, chamber, Items.CLAY_BALL, 1, new ItemStack(Items.BRICK, 2)))
                .thenIdle(3 * VoidworksConfig.mutationInterval())
                .thenExecute(() -> {
                    helper.assertTrue(!dormantLook(helper), "the working Chamber still looks Dormant");
                    assertHolds(helper, chamber, Items.CLAY_BALL, 1, new ItemStack(Items.BRICK, 2));
                    helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade, 2)),
                            "the store holds " + VoidSpender.countByGrade(store) + ", not the 2 left after its cost and two bricks'");
                })
                .thenSucceed();
    }

    /** Three tries, each spending a mote and failing, and the seeds are still there. */
    private static void failedRoll(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.WHEAT_SEEDS, 1);
        var store = new MoteStore().add(grade(helper), DORMANT_MOTES + 3);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VoidSpender.countByGrade(store).isEmpty(),
                        "the store still holds " + VoidSpender.countByGrade(store)))
                .thenExecute(() -> assertHolds(helper, chamber, Items.WHEAT_SEEDS, 1, ItemStack.EMPTY))
                .thenSucceed();
    }

    /** Two motes of the three a brick costs, and worthless motes of the pressure's own grade beside them. */
    private static void cannotAfford(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.CLAY_BALL, 4);
        int grade = grade(helper);
        var store = new MoteStore().add(grade, DORMANT_MOTES + BRICK_MOTES - 1).add(grade - 1, 50);
        SpendTests.placeStore(helper, BESIDE, store);
        awakeThenIdle(helper, chamber, () -> {
            assertHolds(helper, chamber, Items.CLAY_BALL, 4, ItemStack.EMPTY);
            helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade - 1, 50, grade, BRICK_MOTES - 1)),
                    "the store holds " + VoidSpender.countByGrade(store));
        });
    }

    /** An output holding something else stalls it before any mote is spent. */
    private static void outputCannotTakeTheResult(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.CLAY_BALL, 4);
        chamber.inventory().set(MutationChamberBlockEntity.OUTPUT, ItemResource.of(Items.STICK), 1);
        int grade = grade(helper);
        var store = new MoteStore().add(grade, DORMANT_MOTES + BRICK_MOTES);
        SpendTests.placeStore(helper, BESIDE, store);
        awakeThenIdle(helper, chamber, () -> {
            assertHolds(helper, chamber, Items.CLAY_BALL, 4, new ItemStack(Items.STICK));
            helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade, BRICK_MOTES)), "the store holds " + VoidSpender.countByGrade(store));
        });
    }

    private static void itemFace(GameTestHelper helper) {
        var chamber = place(helper);
        var face = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(CHAMBER), Direction.UP);
        helper.assertTrue(face != null, "the Chamber has no item capability");
        chamber.inventory().set(MutationChamberBlockEntity.OUTPUT, ItemResource.of(Items.BRICK), 2);
        try (var transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < face.size(); slot++) {
                helper.assertTrue(face.insert(slot, ItemResource.of(Items.STICK), 1, transaction) == 0, "the face took a stick, which has no upgrade");
                // A brick is on its Family's top Rung, so it has no upgrade either; nothing goes into the output.
                helper.assertTrue(face.insert(slot, ItemResource.of(Items.BRICK), 1, transaction) == 0, "the face took a brick into slot " + slot);
            }
            transaction.commit();
        }
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(face.insert(MutationChamberBlockEntity.INPUT, ItemResource.of(Items.CLAY_BALL), 4, transaction) == 4,
                    "the face did not take four clay balls");
            helper.assertTrue(face.extract(MutationChamberBlockEntity.INPUT, ItemResource.of(Items.CLAY_BALL), 4, transaction) == 0,
                    "the face gave from its input");
            helper.assertTrue(face.extract(MutationChamberBlockEntity.OUTPUT, ItemResource.of(Items.BRICK), 2, transaction) == 2,
                    "the face did not give its output's two bricks");
            transaction.commit();
        }
        assertHolds(helper, chamber, Items.CLAY_BALL, 4, ItemStack.EMPTY);
        helper.succeed();
    }

    private static void byHand(GameTestHelper helper) {
        var chamber = place(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var level = helper.getLevel();
        var pos = helper.absolutePos(CHAMBER);
        var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        var state = level.getBlockState(pos);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 3));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().getCount() == 3 && chamber.input().isEmpty(), "the Chamber took a stick, which has no upgrade");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLAY_BALL, 5));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the hand still holds " + player.getMainHandItem());
        assertHolds(helper, chamber, Items.CLAY_BALL, 5, ItemStack.EMPTY);

        chamber.inventory().set(MutationChamberBlockEntity.OUTPUT, ItemResource.of(Items.BRICK), 2);
        state.useWithoutItem(level, player, hit);
        helper.assertTrue(player.getInventory().countItem(Items.BRICK) == 2, "the empty hand was not given the two bricks");
        assertHolds(helper, chamber, Items.CLAY_BALL, 5, ItemStack.EMPTY);
        // The bricks went into the hand, so it is emptied again.
        player.getInventory().clearContent();

        // With the output empty, an empty hand takes back what is waiting to upgrade.
        state.useWithoutItem(level, player, hit);
        helper.assertTrue(player.getInventory().countItem(Items.CLAY_BALL) == 5, "the empty hand was not given back the clay balls");
        helper.assertTrue(chamber.input().isEmpty(), "the input still holds " + chamber.input());
        helper.succeed();
    }

    private static void breakingDrops(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.CLAY_BALL, 3);
        chamber.inventory().set(MutationChamberBlockEntity.OUTPUT, ItemResource.of(Items.BRICK), 1);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        var pos = helper.absolutePos(CHAMBER);
        float progress = helper.getLevel().getBlockState(pos).getDestroyProgress(player, helper.getLevel(), pos);
        helper.assertTrue(progress > 0, "a survival player with a pickaxe makes no progress breaking the Chamber");
        helper.getLevel().destroyBlock(pos, true, player);
        helper.runAfterDelay(1, () -> {
            for (var expected : new Item[] {VoidworksBlocks.MUTATION_CHAMBER_ITEM.get(), Items.CLAY_BALL, Items.BRICK}) {
                var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2),
                        item -> item.getItem().is(expected));
                helper.assertTrue(!dropped.isEmpty(), "breaking the Chamber did not drop " + expected);
            }
            helper.succeed();
        });
    }

    private static void hasARecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "mutation_chamber"));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no recipe " + key.identifier());
        helper.succeed();
    }

    // -- helpers --------------------------------------------------------------------------------

    private static MutationChamberBlockEntity place(GameTestHelper helper) {
        helper.setBlock(CHAMBER, VoidworksBlocks.MUTATION_CHAMBER.get());
        return helper.getBlockEntity(CHAMBER, MutationChamberBlockEntity.class);
    }

    private static void insert(MutationChamberBlockEntity chamber, Item item, int count) {
        chamber.inventory().set(MutationChamberBlockEntity.INPUT, ItemResource.of(item), count);
    }

    /** Waits for the Chamber to wake, gives it time to try upgrading, then checks {@code check}. */
    private static void awakeThenIdle(GameTestHelper helper, MutationChamberBlockEntity chamber, Runnable check) {
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(chamber.dormancy().awake(), "the Chamber has not woken"))
                .thenIdle(3 * VoidworksConfig.mutationInterval())
                .thenExecute(check)
                .thenSucceed();
    }

    private static void assertHolds(GameTestHelper helper, MutationChamberBlockEntity chamber, Item input, int count, ItemStack output) {
        var held = chamber.input();
        helper.assertTrue(held.is(input) && held.getCount() == count, "the input holds " + held + ", not " + count + " " + input);
        helper.assertTrue(ItemStack.matches(chamber.output(), output), "the output holds " + chamber.output() + ", not " + output);
    }

    private static boolean dormantLook(GameTestHelper helper) {
        return helper.getBlockState(CHAMBER).getValue(Dormancy.DORMANT);
    }

    /** The grade one step above the Void Pressure at the Chamber, so a mote releases {@link VoidEnergy#BASE}. */
    private static int grade(GameTestHelper helper) {
        return DisplacerTests.pressure(helper, CHAMBER) + 1;
    }
}
