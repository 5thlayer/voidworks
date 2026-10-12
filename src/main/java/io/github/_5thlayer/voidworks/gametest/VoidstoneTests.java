// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksCapabilities;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Voidstone on a real server, however it is placed: touching Voidstone is one network, reached
 * through any of its Voidstone's {@code voidworks:motes}, holding a fixed amount per Voidstone, and
 * a spend takes from it like any mote store. Voidstone is never an item.
 */
final class VoidstoneTests {

    private VoidstoneTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("touching_voidstone_is_one_network_holding_a_fixed_amount_per_voidstone", 1, VoidstoneTests::oneNetwork);
        tests.test("voidstone_placed_between_two_networks_merges_them", 1, VoidstoneTests::merges);
        tests.test("voidstone_of_different_looks_joins_one_network", 1, VoidstoneTests::joinsAcrossLooks);
        tests.test("a_spend_takes_from_a_voidstone_network", 1, VoidstoneTests::spends);
        tests.test("voidstone_is_never_an_item", 1, VoidstoneTests::neverAnItem);
    }

    private static void oneNetwork(GameTestHelper helper) {
        var west = new BlockPos(2, 1, 2);
        var east = west.east(2);
        for (var pos : new BlockPos[] {west, west.east(), east}) {
            helper.setBlock(pos, VoidworksBlocks.VOIDSTONE.get());
        }
        int capacity = 3 * VoidworksConfig.voidstoneCapacity();
        helper.assertTrue(insert(helper, west, 3, capacity + 5) == capacity,
                "three Voidstone should hold " + capacity + " motes between them");
        helper.assertTrue(DisplacerTests.motes(helper, east).equals(Map.of(3, capacity)),
                "the motes put in at one end are not all at the other: " + DisplacerTests.motes(helper, east));
        helper.assertTrue(DisplacerTests.motes(helper, west.north()).isEmpty(), "a stone beside the network has motes");
        helper.succeed();
    }

    /** An End-looking Voidstone between two Overworld-looking ones: one network, as the look is all that differs. */
    private static void joinsAcrossLooks(GameTestHelper helper) {
        var west = new BlockPos(2, 1, 2);
        var east = west.east(2);
        helper.setBlock(west, VoidworksBlocks.VOIDSTONE.get());
        helper.setBlock(west.east(), VoidworksBlocks.VOIDSTONE.get().defaultBlockState().setValue(VoidstoneBlock.KIND, HarvestKind.END));
        helper.setBlock(east, VoidworksBlocks.VOIDSTONE.get());
        insert(helper, west, 3, 4);
        helper.assertTrue(DisplacerTests.motes(helper, east).equals(Map.of(3, 4)),
                "the motes put in at the west end are not at the east, past the End-looking Voidstone: " + DisplacerTests.motes(helper, east));
        helper.succeed();
    }

    private static void merges(GameTestHelper helper) {
        var west = new BlockPos(2, 1, 2);
        var east = west.east(2);
        helper.setBlock(west, VoidworksBlocks.VOIDSTONE.get());
        helper.setBlock(east, VoidworksBlocks.VOIDSTONE.get());
        insert(helper, west, 3, 4);
        insert(helper, east, 5, 2);

        helper.setBlock(west.east(), VoidworksBlocks.VOIDSTONE.get());

        var expected = Map.of(3, 4, 5, 2);
        helper.assertTrue(DisplacerTests.motes(helper, west).equals(expected) && DisplacerTests.motes(helper, east).equals(expected),
                "the merged network should hold " + expected + ", holds " + DisplacerTests.motes(helper, west)
                        + " at the west end and " + DisplacerTests.motes(helper, east) + " at the east");
        helper.succeed();
    }

    /** The Overworld's pressure here, so the grade above it releases {@link VoidEnergy#BASE} a mote. */
    private static void spends(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, VoidworksBlocks.VOIDSTONE.get());
        var absolute = helper.absolutePos(pos);
        int grade = VoidPressure.at(helper.getLevel(), absolute) + 1;
        insert(helper, pos, grade, 5);

        var spend = VoidSpender.spend(network(helper, pos), helper.getLevel(), absolute, 2 * VoidEnergy.BASE);

        helper.assertTrue(spend.isPresent() && spend.get().motes().equals(Map.of(grade, 2)),
                "a spend of " + 2 * VoidEnergy.BASE + " should take two grade " + grade + " motes, took " + spend);
        helper.assertTrue(DisplacerTests.motes(helper, pos).equals(Map.of(grade, 3)), "the network holds " + DisplacerTests.motes(helper, pos));
        helper.succeed();
    }

    private static void neverAnItem(GameTestHelper helper) {
        Item item = VoidworksBlocks.VOIDSTONE.get().asItem();
        helper.assertTrue(item == Items.AIR, "Voidstone has an item, " + item);
        helper.succeed();
    }

    private static ResourceHandler<MoteResource> network(GameTestHelper helper, BlockPos pos) {
        var network = helper.getLevel().getCapability(VoidworksCapabilities.MOTES, helper.absolutePos(pos), null);
        helper.assertTrue(network != null, "the Voidstone at " + pos + " has no voidworks:motes");
        return network;
    }

    static int insert(GameTestHelper helper, BlockPos pos, int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = network(helper, pos).insert(MoteResource.of(grade), count, transaction);
            transaction.commit();
            return inserted;
        }
    }
}
