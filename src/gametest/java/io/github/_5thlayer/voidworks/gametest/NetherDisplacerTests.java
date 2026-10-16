// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.displacer.DisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.NetherDisplacerBlockEntity;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Nether Void Displacer on a real server. Most tests stand in the Nether itself, whose harvest
 * kind is {@code nether} at a base of {@link #PRESSURE}, each at a site of its own above the
 * bedrock roof, a chunk apart, so no lava but a test's own is in reach ({@link DimensionSite}). What
 * it shares with the other Displacers is in {@link DisplacerTests} and {@link EndDisplacerTests}.
 */
final class NetherDisplacerTests {

    /** The Nether's base Void Pressure, as Voidworks ships it. */
    private static final int PRESSURE = 1;

    /** Where a Displacer stands relative to a site's corner: the middle of the chunk, above the Nether's roof. */
    private static final BlockPos DISPLACER = new BlockPos(8, 160, 8);
    private static final BlockPos BELOW = DISPLACER.below();
    /** How far around the Displacer a test's blocks reach, cleared afterwards. */
    private static final int REACH = 4;

    /** Where the Displacer stands in the Overworld's tests, on the platform. */
    private static final BlockPos ON_PLATFORM = new BlockPos(4, 1, 4);

    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    private NetherDisplacerTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_nether_displacer_displaces_a_lava_source_touching_it_into_its_tank_for_a_fed_block_leaving_voidstone_and_motes", 1,
                helper -> inTheNether(helper, 0, NetherDisplacerTests::displacesIntoItsTank));
        tests.test("a_nether_displacer_grows_along_the_lava_sources_its_voidstone_touches_nearest_first_and_not_through_flowing_lava", 1,
                helper -> inTheNether(helper, 1, NetherDisplacerTests::growsAlongLava));
        tests.test("a_nether_displacers_full_tank_stalls_it_until_its_fluid_face_is_drained", 1,
                helper -> inTheNether(helper, 2, NetherDisplacerTests::fullTankStalls));
        tests.test("a_nether_displacer_placed_dormant_harvests_nothing_until_it_has_soaked_its_cost", 1,
                helper -> inTheNether(helper, 3, NetherDisplacerTests::dormantUntilPaid));
        tests.test("a_nether_displacer_that_can_reach_no_lava_source_pops_off", 1,
                helper -> inTheNether(helper, 4, NetherDisplacerTests::popsOffWithNoLava));
        tests.test("a_nether_displacers_fluid_face_gives_its_lava_and_takes_none", 1, NetherDisplacerTests::fluidFace);
        tests.test("an_empty_bucket_used_on_a_nether_displacer_takes_a_bucket_of_its_lava", 1, NetherDisplacerTests::bucketDrains);
        tests.test("a_nether_displacer_where_the_harvest_kind_is_not_nether_harvests_nothing_and_pops_off", 40,
                NetherDisplacerTests::refusesOutsideTheNether);
        tests.test("the_nether_displacer_has_a_recipe", 1, NetherDisplacerTests::hasARecipe);
    }

    /** Lava below it and below that, a Zone of two and one copper block fed: one harvest, and it waits for more. */
    private static void displacesIntoItsTank(DimensionSite site) {
        site.set(BELOW, LAVA);
        site.set(BELOW.below(), LAVA);
        var displacer = place(site, 2);
        site.feed(displacer, 1);
        site.payInFull(displacer);
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        site.assertTrue(displacer.grown().equals(List.of(site.abs(BELOW))), "it harvested " + displacer.grown() + ", not the lava below it alone");
        site.assertVoidstone(BELOW, HarvestKind.NETHER);
        site.assertTrue(site.state(BELOW.below()).is(Blocks.LAVA), "it harvested the lava it had no fed block for: " + site.state(BELOW.below()));
        site.assertTrue(displacer.fed().isEmpty(), "it still holds " + displacer.fed());
        assertLava(site, displacer, FluidType.BUCKET_VOLUME);
        var motes = site.motes(BELOW);
        site.assertTrue(motes.equals(Map.of(PRESSURE + 1, VoidworksConfig.motes(1))),
                "the network holds " + motes + ", not a copper block's " + VoidworksConfig.motes(1) + " motes of grade " + (PRESSURE + 1));
    }

    /**
     * A row of three lava sources running east from below it, a fourth beyond a block of netherrack
     * to the west, and flowing lava to its north: it takes the row in order, nothing else, and with
     * nothing left in reach becomes Voidstone.
     */
    private static void growsAlongLava(DimensionSite site) {
        for (int i = 0; i < 3; i++) {
            site.set(BELOW.east(i), LAVA);
        }
        site.set(BELOW.west(), Blocks.NETHERRACK.defaultBlockState());
        site.set(BELOW.west(2), LAVA);
        site.set(DISPLACER.north(), LAVA.setValue(LiquidBlock.LEVEL, 2));
        var displacer = place(site, 8);
        site.feed(displacer, 8);
        site.payInFull(displacer);
        site.tick(displacer, 10 * VoidworksConfig.harvestInterval());
        var row = List.of(site.abs(BELOW), site.abs(BELOW.east()), site.abs(BELOW.east(2)));
        site.assertTrue(displacer.grown().equals(row), "it harvested " + displacer.grown() + ", not the row " + row + " in order");
        site.assertTrue(site.state(BELOW.west(2)).is(Blocks.LAVA), "it reached the lava past the netherrack");
        site.assertTrue(site.state(DISPLACER.north()).is(Blocks.LAVA), "it harvested the flowing lava");
        assertLava(site, displacer, 3 * FluidType.BUCKET_VOLUME);
        site.assertVoidstone(DISPLACER, HarvestKind.NETHER);
    }

    /** Its tank full, it harvests nothing and says why; a bucket's worth drained from its fluid face, it harvests. */
    private static void fullTankStalls(DimensionSite site) {
        site.set(BELOW, LAVA);
        site.set(BELOW.below(), LAVA);
        var displacer = place(site, 2);
        site.feed(displacer, 1);
        site.payInFull(displacer);
        displacer.tank().set(0, FluidResource.of(Fluids.LAVA), NetherDisplacerBlockEntity.TANK_CAPACITY);
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        site.assertTrue(displacer.grown().isEmpty(), "with a full tank it harvested " + displacer.grown());
        var stall = displacer.stall(site.level());
        site.assertTrue(stall.equals(Optional.of(DisplacerBlockEntity.Stall.TANK_FULL)), "it says it is stalled by " + stall + ", not a full tank");

        var face = site.level().getCapability(Capabilities.Fluid.BLOCK, site.abs(DISPLACER), Direction.UP);
        site.assertTrue(face != null, "it has no fluid face");
        try (var transaction = Transaction.openRoot()) {
            int drained = face.extract(FluidResource.of(Fluids.LAVA), FluidType.BUCKET_VOLUME, transaction);
            site.assertTrue(drained == FluidType.BUCKET_VOLUME, "its fluid face gave " + drained + " mB, not a bucket's worth");
            transaction.commit();
        }
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        site.assertTrue(displacer.grown().size() == 1, "drained, it harvested " + displacer.grown() + ", not its one fed block");
        assertLava(site, displacer, NetherDisplacerBlockEntity.TANK_CAPACITY);
    }

    /** Fed, beside lava, it waits Dormant until it is paid, then harvests. */
    private static void dormantUntilPaid(DimensionSite site) {
        site.set(BELOW, LAVA);
        site.set(BELOW.below(), LAVA);
        var displacer = place(site, 2);
        site.feed(displacer, 1);
        site.assertTrue(displacer.dormancy().cost() > 0, "the Nether Displacer ships no Dormant cost");
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        site.assertTrue(displacer.grown().isEmpty(), "the Dormant Displacer harvested " + displacer.grown());
        site.assertTrue(site.dormantLook(), "the unpaid Displacer does not look Dormant");
        site.payInFull(displacer);
        site.tick(displacer, 3 * VoidworksConfig.harvestInterval());
        site.assertTrue(!site.dormantLook(), "the paid Displacer still looks Dormant");
        site.assertTrue(displacer.grown().size() == 1, "the woken Displacer harvested " + displacer.grown() + ", not its one fed block");
    }

    /** Netherrack below it and air all round: nothing it can displace, so it gives itself and its fed block back. */
    private static void popsOffWithNoLava(DimensionSite site) {
        site.set(BELOW, Blocks.NETHERRACK.defaultBlockState());
        var displacer = place(site, 2);
        site.feed(displacer, 1);
        site.tick(displacer, 1);
        site.assertTrue(site.state(DISPLACER).isAir(), "it still stands: " + site.state(DISPLACER));
        site.assertTrue(site.dropped().contains(VoidworksBlocks.NETHER_DISPLACER_ITEM.get()) && site.dropped().contains(Items.COPPER_BLOCK),
                "it should drop itself and its fed block, dropped " + site.dropped());
    }

    /** On every side, its fluid face gives the lava in its tank and refuses lava offered to it. Used before its first tick. */
    private static void fluidFace(GameTestHelper helper) {
        helper.setBlock(ON_PLATFORM, VoidworksBlocks.NETHER_DISPLACER.get());
        var displacer = helper.getBlockEntity(ON_PLATFORM, NetherDisplacerBlockEntity.class);
        displacer.tank().set(0, FluidResource.of(Fluids.LAVA), 2 * FluidType.BUCKET_VOLUME);
        var lava = FluidResource.of(Fluids.LAVA);
        for (var side : Direction.values()) {
            var face = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(ON_PLATFORM), side);
            helper.assertTrue(face != null, "it has no fluid face on its " + side + " side");
            try (var transaction = Transaction.openRoot()) {
                helper.assertTrue(face.insert(lava, FluidType.BUCKET_VOLUME, transaction) == 0, "its " + side + " face took lava");
                helper.assertTrue(face.extract(lava, FluidType.BUCKET_VOLUME, transaction) == FluidType.BUCKET_VOLUME,
                        "its " + side + " face gave no bucket of lava");
            }
        }
        helper.succeed();
    }

    /** A survival player's empty bucket, used on it, comes back full, and its tank is a bucket lighter. Used before its first tick. */
    private static void bucketDrains(GameTestHelper helper) {
        helper.setBlock(ON_PLATFORM, VoidworksBlocks.NETHER_DISPLACER.get());
        var displacer = helper.getBlockEntity(ON_PLATFORM, NetherDisplacerBlockEntity.class);
        displacer.tank().set(0, FluidResource.of(Fluids.LAVA), 2 * FluidType.BUCKET_VOLUME);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        helper.useBlock(ON_PLATFORM, player);
        helper.assertTrue(player.getInventory().countItem(Items.LAVA_BUCKET) == 1, "the player has no lava bucket");
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 0, "the player still has an empty bucket");
        int left = displacer.tank().getAmountAsInt(0);
        helper.assertTrue(left == FluidType.BUCKET_VOLUME, "its tank holds " + left + " mB, not one bucket's worth");
        helper.succeed();
    }

    /** On the Overworld's platform, where the harvest kind is {@code overworld}. */
    private static void refusesOutsideTheNether(GameTestHelper helper) {
        helper.setBlock(ON_PLATFORM, VoidworksBlocks.NETHER_DISPLACER.get());
        helper.getBlockEntity(ON_PLATFORM, NetherDisplacerBlockEntity.class).inventory()
                .set(DisplacerBlockEntity.INPUT, ItemResource.of(Items.COPPER_BLOCK), 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, ON_PLATFORM);
            helper.assertBlockPresent(Blocks.STONE, ON_PLATFORM.below());
            helper.assertItemEntityPresent(VoidworksBlocks.NETHER_DISPLACER_ITEM.get(), ON_PLATFORM, 2);
            helper.assertItemEntityPresent(Items.COPPER_BLOCK, ON_PLATFORM, 2);
        });
    }

    private static void hasARecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "nether_void_displacer"));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no recipe " + key.identifier());
        helper.succeed();
    }

    // -- a site in the Nether ---------------------------------------------------------------------

    /** Runs {@code body} at site {@code index} in the Nether, a chunk of its own above the roof. */
    private static void inTheNether(GameTestHelper helper, int index, Consumer<DimensionSite> body) {
        DimensionSite.run(helper, Level.NETHER, new BlockPos(16 * index, 0, 512), DISPLACER, REACH, body);
    }

    private static NetherDisplacerBlockEntity place(DimensionSite site, int zone) {
        return site.place(VoidworksBlocks.NETHER_DISPLACER.get().defaultBlockState(), zone, NetherDisplacerBlockEntity.class);
    }

    private static void assertLava(DimensionSite site, NetherDisplacerBlockEntity displacer, int amount) {
        var held = displacer.tank().getResource(0);
        int count = displacer.tank().getAmountAsInt(0);
        site.assertTrue(held.equals(FluidResource.of(Fluids.LAVA)) && count == amount, "its tank holds " + count + " mB of " + held + ", not " + amount + " mB of lava");
    }
}
