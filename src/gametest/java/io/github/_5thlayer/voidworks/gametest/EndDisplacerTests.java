// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.displacer.EndDisplacerBlock;
import io.github._5thlayer.voidworks.displacer.EndDisplacerBlockEntity;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.SiphonMotes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The End Void Displacer on a real server. Most tests stand in the End itself, whose harvest kind
 * the game-test pack keeps {@code end} at a base of {@link #PRESSURE}, each at a site of its own in
 * the void between the main island and the outer ones, a chunk apart. The game test server ticks no
 * block entity outside the Overworld, so those tests tick the Displacer themselves
 * ({@link DimensionSite}). What it shares with the Overworld's is in
 * {@link DisplacerTests}.
 */
final class EndDisplacerTests {

    /** The End's base Void Pressure in the game-test pack. */
    private static final int PRESSURE = 9;

    /** Where a Displacer stands relative to a site's corner: the middle of the chunk, high above the floor a test may lay. */
    private static final BlockPos DISPLACER = new BlockPos(8, 64, 8);
    private static final BlockPos BELOW = DISPLACER.below();
    /** A block far beneath, so the column below the Displacer is not open to the void. */
    private static final BlockPos FLOOR = DISPLACER.below(24);
    /** How far around the Displacer a test's blocks reach, cleared afterwards. */
    private static final int REACH = 4;

    /** Where the Displacer stands in the Overworld's tests, on the platform. */
    private static final BlockPos ON_PLATFORM = new BlockPos(4, 1, 4);

    private EndDisplacerTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("an_end_displacer_places_a_fed_block_into_the_air_beside_it_leaving_voidstone_and_motes_until_its_zone_is_spent", 1,
                helper -> inTheEnd(helper, 0, EndDisplacerTests::placesIntoTheAir));
        tests.test("an_end_displacers_harvest_over_the_open_void_grades_one_step_higher_and_its_voidstone_closes_the_column", 1,
                helper -> inTheEnd(helper, 1, EndDisplacerTests::openVoid));
        tests.test("an_end_displacer_placed_dormant_harvests_nothing_until_it_has_soaked_its_cost_from_touching_voidstone", 1,
                helper -> inTheEnd(helper, 2, EndDisplacerTests::dormantUntilPaid));
        tests.test("a_dormant_end_displacer_that_can_reach_no_air_pops_off_before_soaking_its_cost", 1,
                helper -> inTheEnd(helper, 3, EndDisplacerTests::popsOffBeforeSoaking));
        tests.test("an_end_displacer_grows_only_ahead_of_its_front_face_leaving_its_other_faces_open", 1,
                helper -> inTheEnd(helper, 4, EndDisplacerTests::growsOnlyAhead));
        tests.test("an_end_displacer_placed_faces_where_its_player_looks", 1, EndDisplacerTests::facesWhereThePlayerLooks);
        tests.test("an_end_displacer_where_the_harvest_kind_is_not_end_harvests_nothing_and_pops_off", 40, EndDisplacerTests::refusesOutsideTheEnd);
        tests.test("an_emptying_siphon_used_on_a_dormant_end_displacer_pays_it_and_an_empty_hand_takes_back_its_fed_blocks", 1,
                EndDisplacerTests::siphonPaysAndEmptyHandTakesBack);
        tests.test("the_end_displacer_has_a_recipe", 1, EndDisplacerTests::hasARecipe);
    }

    /** Above a floor, so not over the open void; a Zone of one, fed two copper blocks. */
    private static void placesIntoTheAir(DimensionSite site) {
        site.set(FLOOR, Blocks.END_STONE.defaultBlockState());
        var displacer = place(site, 1);
        site.feed(displacer, 2);
        site.payInFull(displacer);
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        var first = displacer.grown().isEmpty() ? null : displacer.grown().getFirst();
        site.assertTrue(site.abs(BELOW).equals(first), "it harvested " + first + " first, not the air below it");
        site.assertVoidstone(BELOW, HarvestKind.END);
        // Its Zone spent, it became Voidstone, joining the network that keeps the harvest's motes.
        site.assertVoidstone(DISPLACER, HarvestKind.END);
        var motes = site.motes(DISPLACER);
        site.assertTrue(motes.equals(Map.of(PRESSURE + 1, VoidworksConfig.motes(1))),
                "the network holds " + motes + ", not a copper block's " + VoidworksConfig.motes(1) + " motes of grade " + (PRESSURE + 1));
        site.assertTrue(site.dropped().equals(List.of(Items.COPPER_BLOCK)),
                "it should drop only the copper block it did not harvest, dropped " + site.dropped());
    }

    private static void openVoid(DimensionSite site) {
        var displacer = place(site, 2);
        site.assertTrue(site.pressure(DISPLACER) == PRESSURE + 1, "the void beneath the Displacer is not open: pressure " + site.pressure(DISPLACER));
        site.feed(displacer, 1);
        site.payInFull(displacer);
        site.tick(displacer, 2 * VoidworksConfig.harvestInterval());
        site.assertVoidstone(BELOW);
        var motes = site.motes(BELOW);
        site.assertTrue(motes.equals(Map.of(PRESSURE + 2, VoidworksConfig.motes(1))),
                "the network holds " + motes + ", not a copper block's motes of grade " + (PRESSURE + 2) + ", over the open void");
        site.assertTrue(site.pressure(DISPLACER) == PRESSURE, "the Voidstone below did not close the column above it: pressure " + site.pressure(DISPLACER));
    }

    /**
     * Fed, it waits Dormant with nothing to pay it. Then Voidstone touching it holds motes three
     * steps above the pressure, enough for its cost, and it soaks them, wakes and harvests.
     */
    private static void dormantUntilPaid(DimensionSite site) {
        site.set(FLOOR, Blocks.END_STONE.defaultBlockState());
        var displacer = place(site, 2);
        site.feed(displacer, 1);
        long cost = displacer.dormancy().cost();
        site.assertTrue(cost > 0, "the End Displacer ships no Dormant cost");
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        site.assertTrue(displacer.grown().isEmpty(), "the Dormant Displacer harvested " + displacer.grown());
        site.assertTrue(site.dormantLook(), "the unpaid Displacer does not look Dormant");

        int grade = PRESSURE + 3;
        long each = VoidEnergy.release(grade, PRESSURE).orElseThrow();
        int owed = (int) ((cost + each - 1) / each);
        // A row of Voidstone running north from it, long enough to hold what it owes.
        int row = (owed + VoidworksConfig.voidstoneCapacity() - 1) / VoidworksConfig.voidstoneCapacity();
        for (int i = 1; i <= row; i++) {
            site.set(DISPLACER.north(i), VoidworksBlocks.VOIDSTONE.get().defaultBlockState());
        }
        site.insert(DISPLACER.north(), grade, owed);
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        site.assertTrue(displacer.dormancy().awake(), "the Displacer is still Dormant, paid " + displacer.dormancy().paid() + " of " + cost);
        site.assertTrue(!site.dormantLook(), "the paid Displacer still looks Dormant");
        site.assertTrue(site.motes(DISPLACER.north()).isEmpty(), "the Voidstone still holds " + site.motes(DISPLACER.north()));
        site.assertTrue(displacer.grown().size() == 1, "the woken Displacer harvested " + displacer.grown() + ", not its one fed block");
    }

    /** Boxed in by end stone, with Voidstone holding motes enough to pay it on one side: it pays nothing and pops off. */
    private static void popsOffBeforeSoaking(DimensionSite site) {
        var displacer = place(site, 2);
        site.feed(displacer, 1);
        for (var side : new BlockPos[] {DISPLACER.above(), DISPLACER.below(), DISPLACER.south(), DISPLACER.east(), DISPLACER.west()}) {
            site.set(side, Blocks.END_STONE.defaultBlockState());
        }
        site.set(DISPLACER.north(), VoidworksBlocks.VOIDSTONE.get().defaultBlockState());
        site.insert(DISPLACER.north(), PRESSURE + 3, VoidworksConfig.voidstoneCapacity());
        site.tick(displacer, 3 * VoidworksConfig.soakInterval());
        site.assertTrue(site.state(DISPLACER).isAir(), "it still stands: " + site.state(DISPLACER));
        site.assertTrue(site.dropped().contains(VoidworksBlocks.END_DISPLACER_ITEM.get()) && site.dropped().contains(Items.COPPER_BLOCK),
                "it should drop itself and its fed block, dropped " + site.dropped());
        site.assertTrue(site.motes(DISPLACER.north()).equals(Map.of(PRESSURE + 3, VoidworksConfig.voidstoneCapacity())),
                "it soaked motes before it popped off: the Voidstone holds " + site.motes(DISPLACER.north()));
    }

    /** Facing east in open air, with eight harvests to make: every one lies east of it, and its other five faces stay open. */
    private static void growsOnlyAhead(DimensionSite site) {
        var displacer = place(site, 8, Direction.EAST);
        site.feed(displacer, 8);
        site.payInFull(displacer);
        site.tick(displacer, 10 * VoidworksConfig.harvestInterval());
        site.assertTrue(displacer.grown().size() == 8, "it harvested " + displacer.grown().size() + " of its 8 fed blocks");
        site.assertTrue(site.abs(DISPLACER.east()).equals(displacer.grown().getFirst()),
                "it harvested " + displacer.grown().getFirst() + " first, not the air ahead of its front face");
        for (var pos : displacer.grown()) {
            site.assertTrue(pos.getX() > site.abs(DISPLACER).getX(), "it harvested " + pos + ", not ahead of its east face");
        }
        for (var side : new BlockPos[] {DISPLACER.above(), DISPLACER.below(), DISPLACER.north(), DISPLACER.south(), DISPLACER.west()}) {
            site.assertTrue(site.state(side).isAir(), "it walled in its face at " + side + " with " + site.state(side));
        }
    }

    /** Looking east, then straight down: its front face looks the same way. */
    private static void facesWhereThePlayerLooks(GameTestHelper helper) {
        assertPlacedFacing(helper, -90F, 0F, Direction.EAST);
        assertPlacedFacing(helper, 0F, 90F, Direction.DOWN);
        helper.succeed();
    }

    /** Places it, on the platform, as a player looking by {@code yaw} and {@code pitch} would. */
    private static void assertPlacedFacing(GameTestHelper helper, float yaw, float pitch, Direction expected) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        var pos = helper.absolutePos(ON_PLATFORM);
        var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos.below(), false);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(VoidworksBlocks.END_DISPLACER_ITEM.get()), hit);
        var facing = VoidworksBlocks.END_DISPLACER.get().getStateForPlacement(context).getValue(EndDisplacerBlock.FACING);
        helper.assertTrue(facing == expected, "placed looking " + expected + ", it faces " + facing);
    }

    /** On the Overworld's platform, where the harvest kind is {@code overworld}. */
    private static void refusesOutsideTheEnd(GameTestHelper helper) {
        helper.setBlock(ON_PLATFORM, VoidworksBlocks.END_DISPLACER.get());
        helper.getBlockEntity(ON_PLATFORM, EndDisplacerBlockEntity.class).inventory()
                .set(EndDisplacerBlockEntity.INPUT, ItemResource.of(Items.COPPER_BLOCK), 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, ON_PLATFORM);
            helper.assertBlockPresent(Blocks.STONE, ON_PLATFORM.below());
            helper.assertItemEntityPresent(VoidworksBlocks.END_DISPLACER_ITEM.get(), ON_PLATFORM, 2);
            helper.assertItemEntityPresent(Items.COPPER_BLOCK, ON_PLATFORM, 2);
        });
    }

    /**
     * Used as a player uses it, the block first: with fed blocks in it, a Siphon set to empty still
     * pays it, and only an empty hand takes the fed blocks back. It is used before its first tick,
     * which in the Overworld pops it off.
     */
    private static void siphonPaysAndEmptyHandTakesBack(GameTestHelper helper) {
        helper.setBlock(ON_PLATFORM, VoidworksBlocks.END_DISPLACER.get());
        var displacer = helper.getBlockEntity(ON_PLATFORM, EndDisplacerBlockEntity.class);
        displacer.inventory().set(EndDisplacerBlockEntity.INPUT, ItemResource.of(Items.COPPER_BLOCK), 3);
        int grade = DisplacerTests.pressure(helper, ON_PLATFORM) + 1;
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var siphon = new ItemStack(VoidworksItems.VOID_SIPHON.get());
        siphon.set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        siphon.set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(grade, 5));
        player.setItemInHand(InteractionHand.MAIN_HAND, siphon);
        helper.useBlock(ON_PLATFORM, player);
        helper.assertTrue(displacer.dormancy().paid() == 5 * VoidEnergy.BASE, "the Siphon paid " + displacer.dormancy().paid() + ", not its five motes");
        helper.assertTrue(displacer.inventory().getAmountAsInt(EndDisplacerBlockEntity.INPUT) == 3, "using the Siphon took back the fed blocks");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(ON_PLATFORM, player);
        helper.assertTrue(player.getInventory().countItem(Items.COPPER_BLOCK) == 3,
                "the empty hand got " + player.getInventory().countItem(Items.COPPER_BLOCK) + " copper blocks back, not 3");
        helper.succeed();
    }

    private static void hasARecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "end_void_displacer"));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no recipe " + key.identifier());
        helper.succeed();
    }

    // -- a site in the End ------------------------------------------------------------------------

    /** Runs {@code body} at site {@code index} in the End, a chunk of its own over the void. */
    private static void inTheEnd(GameTestHelper helper, int index, Consumer<DimensionSite> body) {
        // Out past the main island, short of the outer ones: nothing below but the void.
        DimensionSite.run(helper, Level.END, new BlockPos(16 * index, 0, 512), DISPLACER, REACH, body);
    }

    private static EndDisplacerBlockEntity place(DimensionSite site, int zone) {
        return place(site, zone, Direction.DOWN);
    }

    private static EndDisplacerBlockEntity place(DimensionSite site, int zone, Direction facing) {
        return site.place(VoidworksBlocks.END_DISPLACER.get().defaultBlockState().setValue(EndDisplacerBlock.FACING, facing), zone,
                EndDisplacerBlockEntity.class);
    }
}
