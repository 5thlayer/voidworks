// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.siphon.SiphonCharging;
import io.github._5thlayer.voidworks.siphon.SiphonHarvest;
import io.github._5thlayer.voidworks.siphon.SiphonHarvest.Outcome;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.SiphonMotes;
import io.github._5thlayer.voidworks.siphon.SiphonTier;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Void Siphon on a real server, held by a survival player standing on the platform: a harvest
 * by hand for each harvest kind, what it refuses, charging from and emptying into the Voidstone its
 * holder stands on, its toggle, and paying a Dormant machine. The platform is in the Overworld, so a
 * harvest there gives grade 3; the End's and the Nether's harvests are asked for with their kind,
 * as the item asks with the dimension's own.
 */
final class SiphonTests {

    /** The floor beneath the player, which the player aims at. */
    private static final BlockPos FLOOR = new BlockPos(4, 0, 4);
    private static final BlockPos ABOVE = FLOOR.above();

    private SiphonTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_siphon_in_the_overworld_swaps_a_fed_block_for_the_natural_block_aimed_at", 1, SiphonTests::overworldHarvest);
        tests.test("a_siphon_refuses_a_fed_block_of_density_0", 1, SiphonTests::refusesDensity0);
        tests.test("a_siphon_refuses_what_its_harvest_kind_does_not_harvest", 1, SiphonTests::refusesWhatItsKindDoesNotHarvest);
        tests.test("a_siphon_where_the_harvest_kind_is_end_turns_its_fed_block_to_voidstone_in_the_air_aimed_at", 1, SiphonTests::endHarvest);
        tests.test("a_siphon_where_the_harvest_kind_is_nether_displaces_a_lava_source_into_a_bucket", 1, SiphonTests::netherHarvest);
        tests.test("a_siphon_where_the_harvest_kind_is_nether_refuses_with_no_bucket", 1, SiphonTests::netherRefusesWithNoBucket);
        tests.test("a_siphon_where_the_harvest_kind_is_none_refuses", 1, SiphonTests::refusesWhereNone);
        tests.test("a_full_siphon_refuses_a_harvest", 1, SiphonTests::fullRefuses);
        tests.test("sneak_use_flips_a_siphons_toggle", 1, SiphonTests::sneakUseFlips);
        tests.test("a_charging_siphon_on_voidstone_takes_the_highest_grade_first", 1, SiphonTests::charges);
        tests.test("an_emptying_siphon_on_voidstone_empties_into_the_network", 1, SiphonTests::empties);
        tests.test("a_siphon_off_voidstone_neither_charges_nor_empties", 1, SiphonTests::offVoidstone);
        tests.test("an_emptying_siphon_used_on_a_dormant_machine_pays_its_cost", 200, SiphonTests::paysADormantMachine);
        tests.test("a_siphon_is_crafted_from_a_ghast_tear_crying_obsidian_and_a_blaze_rod", 1, SiphonTests::hasARecipe);
        tests.test("a_siphon_smithed_with_an_echo_shard_becomes_resonant_keeping_its_motes_and_toggle", 1, SiphonTests::smithsToResonant);
        tests.test("a_resonant_siphon_smithed_with_dragons_breath_becomes_draconic_keeping_its_motes_and_toggle", 1, SiphonTests::smithsToDraconic);
        tests.test("a_resonant_siphon_holds_more_than_a_siphon", 1, SiphonTests::resonantHoldsMore);
        tests.test("void_takes_the_terrain_a_siphon_harvests", 1, SiphonTests::takesTerrain);
        tests.test("void_takes_the_lava_a_siphon_harvests", 1, SiphonTests::takesLava);
        tests.test("void_takes_the_air_a_siphon_harvests", 1, SiphonTests::takesAir);
        tests.test("void_takes_nothing_from_a_refused_siphon_harvest", 1, SiphonTests::takesNothingRefused);
        tests.test("a_draconic_siphon_on_voidstone_charges_at_its_own_rate", 1, SiphonTests::draconicChargesFaster);
    }

    /** Used for real, aimed straight down at the stone floor beneath the player. */
    private static void overworldHarvest(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.COPPER_BLOCK, 2));
        player.setXRot(90);
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), FLOOR);
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "the fed copper block was not taken");
        helper.assertTrue(player.getInventory().countItem(Items.STONE) == 1, "the swapped-out stone did not come back to the player");
        assertMotes(helper, player, Map.of(grade(helper), 1));
        helper.succeed();
    }

    private static void refusesDensity0(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.COBBLESTONE, 2));
        assertRefused(helper, player, harvest(helper, player, FLOOR, HarvestKind.OVERWORLD), Outcome.NOTHING_TO_FEED);
        helper.assertTrue(player.getOffhandItem().getCount() == 2, "the cobblestone was taken");
        helper.succeed();
    }

    private static void refusesWhatItsKindDoesNotHarvest(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.COPPER_BLOCK, 2));
        helper.setBlock(FLOOR, Blocks.OAK_PLANKS);
        assertRefused(helper, player, harvest(helper, player, FLOOR, HarvestKind.OVERWORLD), Outcome.NOTHING_TO_HARVEST);
        helper.setBlock(ABOVE, Blocks.STONE);
        assertRefused(helper, player, harvest(helper, player, FLOOR, HarvestKind.END), Outcome.NOTHING_TO_HARVEST);
        assertRefused(helper, player, harvest(helper, player, ABOVE, HarvestKind.NETHER), Outcome.NOTHING_TO_HARVEST);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, FLOOR);
        helper.assertBlockPresent(Blocks.STONE, ABOVE);
        helper.assertTrue(player.getOffhandItem().getCount() == 2, "a refused harvest took the copper block");
        helper.succeed();
    }

    /** A diamond block yields a Displacer's gold block: one Density step below. */
    private static void endHarvest(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.DIAMOND_BLOCK));
        var outcome = harvest(helper, player, FLOOR, HarvestKind.END);
        helper.assertTrue(outcome == Outcome.HARVESTED, "the harvest was refused: " + outcome);
        helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), ABOVE);
        helper.assertBlockPresent(Blocks.STONE, FLOOR);
        helper.assertTrue(player.getOffhandItem().isEmpty(), "the fed diamond block was not taken");
        helper.assertTrue(player.getInventory().countItem(Items.STONE) == 0, "an End harvest gave something back");
        assertMotes(helper, player, Map.of(grade(helper), VoidworksConfig.motes(3)));
        helper.succeed();
    }

    private static void netherHarvest(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.IRON_BLOCK));
        player.getInventory().setItem(3, new ItemStack(Items.BUCKET, 2));
        helper.setBlock(FLOOR, Blocks.LAVA);
        var outcome = harvest(helper, player, FLOOR, HarvestKind.NETHER);
        helper.assertTrue(outcome == Outcome.HARVESTED, "the harvest was refused: " + outcome);
        helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), FLOOR);
        helper.assertTrue(player.getInventory().countItem(Items.LAVA_BUCKET) == 1 && player.getInventory().countItem(Items.BUCKET) == 1,
                "the lava did not fill one of the two buckets");
        helper.assertTrue(player.getOffhandItem().isEmpty(), "the fed iron block was not taken");
        assertMotes(helper, player, Map.of(grade(helper), VoidworksConfig.motes(1)));
        helper.succeed();
    }

    private static void netherRefusesWithNoBucket(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.IRON_BLOCK));
        helper.setBlock(FLOOR, Blocks.LAVA);
        assertRefused(helper, player, harvest(helper, player, FLOOR, HarvestKind.NETHER), Outcome.NO_CONTAINER);
        helper.assertBlockPresent(Blocks.LAVA, FLOOR);
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "the iron block was taken");
        helper.succeed();
    }

    private static void refusesWhereNone(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.COPPER_BLOCK));
        assertRefused(helper, player, harvest(helper, player, FLOOR, HarvestKind.NONE), Outcome.NO_HARVEST_HERE);
        helper.assertBlockPresent(Blocks.STONE, FLOOR);
        helper.succeed();
    }

    private static void fullRefuses(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.COPPER_BLOCK));
        var full = SiphonMotes.EMPTY.with(grade(helper), VoidworksConfig.siphonCapacity(SiphonTier.BASE));
        player.getMainHandItem().set(VoidworksItems.SIPHON_MOTES.get(), full);
        var outcome = harvest(helper, player, FLOOR, HarvestKind.OVERWORLD);
        helper.assertTrue(outcome == Outcome.FULL, "a full Siphon's harvest came to " + outcome);
        helper.assertBlockPresent(Blocks.STONE, FLOOR);
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "the copper block was taken");
        helper.assertTrue(VoidSiphonItem.heldMotes(player.getMainHandItem()).equals(full), "the full Siphon's motes changed");
        helper.succeed();
    }

    private static void sneakUseFlips(GameTestHelper helper) {
        var player = player(helper, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        var siphon = player.getMainHandItem();
        helper.assertTrue(VoidSiphonItem.mode(siphon) == SiphonMode.CHARGE, "a new Siphon does not charge");
        siphon.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(VoidSiphonItem.mode(siphon) == SiphonMode.EMPTY, "sneak-use did not set it to empty");
        siphon.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(VoidSiphonItem.mode(siphon) == SiphonMode.CHARGE, "sneak-use did not set it back to charge");
        helper.assertBlockPresent(Blocks.STONE, FLOOR);
        helper.succeed();
    }

    /** Five of grade 4 and five of grade 3 in the network: one exchange takes the 4s, then the 3s, up to the rate. */
    private static void charges(GameTestHelper helper) {
        int rate = VoidworksConfig.siphonRate(SiphonTier.BASE);
        helper.assertTrue(rate > 5 && rate < 10, "this test needs a rate between 5 and 10 motes, not " + rate);
        helper.setBlock(FLOOR, VoidworksBlocks.VOIDSTONE.get());
        VoidstoneTests.insert(helper, FLOOR, 4, 5);
        VoidstoneTests.insert(helper, FLOOR, 3, 5);
        var player = player(helper, ItemStack.EMPTY);
        int moved = SiphonCharging.exchange(helper.getLevel(), player);
        helper.assertTrue(moved == rate, "it moved " + moved + " motes, not the rate's " + rate);
        assertMotes(helper, player, Map.of(4, 5, 3, rate - 5));
        var network = DisplacerTests.motes(helper, FLOOR);
        helper.assertTrue(network.equals(Map.of(3, 10 - rate)), "the network holds " + network);
        helper.succeed();
    }

    private static void empties(GameTestHelper helper) {
        int rate = VoidworksConfig.siphonRate(SiphonTier.BASE);
        helper.setBlock(FLOOR, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper, ItemStack.EMPTY);
        var siphon = player.getMainHandItem();
        siphon.set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        siphon.set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(3, rate + 2));
        SiphonCharging.exchange(helper.getLevel(), player);
        assertMotes(helper, player, Map.of(3, 2));
        var network = DisplacerTests.motes(helper, FLOOR);
        helper.assertTrue(network.equals(Map.of(3, rate)), "the network holds " + network);
        helper.succeed();
    }

    private static void offVoidstone(GameTestHelper helper) {
        var player = player(helper, ItemStack.EMPTY);
        player.getMainHandItem().set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        player.getMainHandItem().set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(3, 5));
        int moved = SiphonCharging.exchange(helper.getLevel(), player);
        helper.assertTrue(moved == 0, "it moved " + moved + " motes standing on stone");
        assertMotes(helper, player, Map.of(3, 5));
        helper.succeed();
    }

    /** The test machine costs four motes one step above the pressure; the Siphon holds ten, and pays only once set to empty. */
    private static void paysADormantMachine(GameTestHelper helper) {
        var machinePos = new BlockPos(4, 1, 6);
        helper.setBlock(machinePos, DormantTestMachine.BLOCK.get());
        var machine = helper.getBlockEntity(machinePos, DormantTestMachine.Entity.class);
        var player = player(helper, ItemStack.EMPTY);
        int grade = grade(helper);
        player.getMainHandItem().set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(grade, 10));
        var absolute = helper.absolutePos(machinePos);
        var hit = new BlockHitResult(absolute.getCenter(), Direction.NORTH, absolute, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(machine.dormancy().paid() == 0, "a charging Siphon paid the machine " + machine.dormancy().paid());
        player.getMainHandItem().set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(machine.dormancy().paid() == 4 * VoidEnergy.BASE, "the machine was paid " + machine.dormancy().paid());
        assertMotes(helper, player, Map.of(grade, 6));
        helper.succeedWhen(() -> helper.assertTrue(machine.dormancy().awake(), "the paid machine has not woken"));
    }

    private static void hasARecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_siphon"));
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(key);
        helper.assertTrue(recipe.isPresent(), "there is no recipe " + key.identifier());
        helper.succeed();
    }

    private static void smithsToResonant(GameTestHelper helper) {
        var siphon = new ItemStack(VoidworksItems.VOID_SIPHON.get());
        siphon.set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(3, 40).with(4, 2));
        siphon.set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        var smithed = smith(helper, siphon, new ItemStack(Items.ECHO_SHARD));
        helper.assertTrue(smithed.is(VoidworksItems.RESONANT_VOID_SIPHON.get()), "an echo shard smithed it into " + smithed);
        helper.assertTrue(VoidSiphonItem.heldMotes(smithed).byGrade().equals(Map.of(3, 40, 4, 2)), "the motes did not carry over: " + VoidSiphonItem.heldMotes(smithed));
        helper.assertTrue(VoidSiphonItem.mode(smithed) == SiphonMode.EMPTY, "the toggle did not carry over");
        helper.succeed();
    }

    private static void smithsToDraconic(GameTestHelper helper) {
        var siphon = new ItemStack(VoidworksItems.RESONANT_VOID_SIPHON.get());
        siphon.set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(5, 9));
        siphon.set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        var smithed = smith(helper, siphon, new ItemStack(Items.DRAGON_BREATH));
        helper.assertTrue(smithed.is(VoidworksItems.DRACONIC_VOID_SIPHON.get()), "dragon's breath smithed it into " + smithed);
        helper.assertTrue(VoidSiphonItem.heldMotes(smithed).byGrade().equals(Map.of(5, 9)), "the motes did not carry over: " + VoidSiphonItem.heldMotes(smithed));
        helper.assertTrue(VoidSiphonItem.mode(smithed) == SiphonMode.EMPTY, "the toggle did not carry over");
        helper.assertTrue(smith(helper, new ItemStack(VoidworksItems.VOID_SIPHON.get()), new ItemStack(Items.DRAGON_BREATH)).isEmpty(),
                "dragon's breath skipped the Resonant tier");
        helper.succeed();
    }

    /** A Resonant Siphon holding a full Siphon's motes still harvests; at its own capacity it is full. */
    private static void resonantHoldsMore(GameTestHelper helper) {
        var player = player(helper, new ItemStack(Items.COPPER_BLOCK, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidworksItems.RESONANT_VOID_SIPHON.get()));
        player.getMainHandItem().set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(grade(helper), VoidworksConfig.siphonCapacity(SiphonTier.BASE)));
        var outcome = harvest(helper, player, FLOOR, HarvestKind.OVERWORLD);
        helper.assertTrue(outcome == Outcome.HARVESTED, "a Resonant Siphon holding a Siphon's capacity came to " + outcome);
        player.getMainHandItem().set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(grade(helper), VoidworksConfig.siphonCapacity(SiphonTier.RESONANT)));
        helper.setBlock(FLOOR, Blocks.STONE);
        outcome = harvest(helper, player, FLOOR, HarvestKind.OVERWORLD);
        helper.assertTrue(outcome == Outcome.FULL, "a Resonant Siphon at its capacity came to " + outcome);
        helper.succeed();
    }

    private static void draconicChargesFaster(GameTestHelper helper) {
        int rate = VoidworksConfig.siphonRate(SiphonTier.DRACONIC);
        helper.assertTrue(rate > VoidworksConfig.siphonRate(SiphonTier.BASE), "the Draconic rate " + rate + " is no faster than the Siphon's");
        // enough Voidstone to hold more than the rate
        for (int x = 0; x * VoidworksConfig.voidstoneCapacity() <= rate; x++) {
            helper.setBlock(FLOOR.east(x), VoidworksBlocks.VOIDSTONE.get());
        }
        int inserted = VoidstoneTests.insert(helper, FLOOR, 3, rate + 5);
        helper.assertTrue(inserted > rate, "the network took only " + inserted + " motes");
        var player = player(helper, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidworksItems.DRACONIC_VOID_SIPHON.get()));
        int moved = SiphonCharging.exchange(helper.getLevel(), player);
        helper.assertTrue(moved == rate, "it moved " + moved + " motes, not the Draconic rate's " + rate);
        assertMotes(helper, player, Map.of(3, rate));
        helper.succeed();
    }

    private static void takesTerrain(GameTestHelper helper) {
        var takings = Takings.watch(helper);
        var player = player(helper, new ItemStack(Items.COPPER_BLOCK));
        harvest(helper, player, FLOOR, HarvestKind.OVERWORLD);
        takings.stop();
        takings.assertTook(FLOOR, Blocks.STONE.defaultBlockState());
        helper.succeed();
    }

    private static void takesLava(GameTestHelper helper) {
        var takings = Takings.watch(helper);
        var player = player(helper, new ItemStack(Items.IRON_BLOCK));
        player.getInventory().setItem(3, new ItemStack(Items.BUCKET));
        helper.setBlock(FLOOR, Blocks.LAVA);
        harvest(helper, player, FLOOR, HarvestKind.NETHER);
        takings.stop();
        takings.assertTook(FLOOR, Blocks.LAVA.defaultBlockState());
        helper.succeed();
    }

    private static void takesAir(GameTestHelper helper) {
        var takings = Takings.watch(helper);
        var player = player(helper, new ItemStack(Items.DIAMOND_BLOCK));
        harvest(helper, player, FLOOR, HarvestKind.END);
        takings.stop();
        takings.assertTook(ABOVE, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    private static void takesNothingRefused(GameTestHelper helper) {
        var takings = Takings.watch(helper);
        var player = player(helper, new ItemStack(Items.COBBLESTONE));
        harvest(helper, player, FLOOR, HarvestKind.OVERWORLD);
        takings.stop();
        helper.assertTrue(takings.at(FLOOR) == null, "a refused harvest took " + takings.at(FLOOR));
        helper.succeed();
    }

    // -- helpers --------------------------------------------------------------------------------

    /** A survival player standing on the floor's middle, a Void Siphon in the main hand and {@code fed} in the off hand. */
    private static Player player(GameTestHelper helper, ItemStack fed) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var feet = helper.absoluteVec(ABOVE.getBottomCenter());
        player.setPos(feet.x, feet.y, feet.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidworksItems.VOID_SIPHON.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, fed);
        return player;
    }

    /** What the smithing table makes of {@code base} and {@code addition} with no template, or nothing. */
    private static ItemStack smith(GameTestHelper helper, ItemStack base, ItemStack addition) {
        var input = new SmithingRecipeInput(ItemStack.EMPTY, base, addition);
        var level = helper.getLevel();
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level)
                .map(recipe -> recipe.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }

    /** Harvests at the top face of {@code aimed}, relative to the test, as a Siphon of harvest kind {@code kind}. */
    private static Outcome harvest(GameTestHelper helper, Player player, BlockPos aimed, HarvestKind kind) {
        var absolute = helper.absolutePos(aimed);
        return SiphonHarvest.harvest(helper.getLevel(), player, new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false), kind);
    }

    private static void assertRefused(GameTestHelper helper, Player player, Outcome outcome, Outcome expected) {
        helper.assertTrue(outcome == expected, "the harvest came to " + outcome + ", not " + expected);
        assertMotes(helper, player, Map.of());
    }

    private static void assertMotes(GameTestHelper helper, Player player, Map<Integer, Integer> expected) {
        var held = VoidSiphonItem.heldMotes(player.getMainHandItem()).byGrade();
        helper.assertTrue(held.equals(expected), "the Siphon holds " + held + ", not " + expected);
    }

    /** The grade the platform harvests: one above its Void Pressure. */
    private static int grade(GameTestHelper helper) {
        return DisplacerTests.pressure(helper, FLOOR) + 1;
    }
}
