// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.network.MoteStore;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.VoidWells;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * A Void Dragon carrying motes between its owner's Void Wells in two dimensions on its record alone,
 * with neither Well's chunk loaded (ADR 0005): each Well is only its listing and its store in its
 * dimension's networks, as a whole, awake Well far from any player is. The rules of a trip are
 * {@code RoutingTest}'s; this is the server running them.
 */
final class RoutingTests {

    /** An Overworld Well far out, where nothing loads a chunk. */
    private static final BlockPos OVERWORLD_WELL = new BlockPos(80_000, 64, 8_000);
    /** A Nether Well at the same place in Overworld blocks, so the dive is the fixed time alone. */
    private static final BlockPos NETHER_WELL = new BlockPos(10_000, 64, 1_000);
    /** How many motes the Nether Well is short of its target: less than a load, so one trip meets it. */
    private static final int SHORT = 100;

    /** One trip: a stop to load, a dive and a stop to unload, at the default numbers, with room to spare. */
    private static final int TICKS = 1_000;

    private RoutingTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_void_dragon_carries_motes_from_a_reservoir_to_a_well_below_its_target_in_another_dimension_with_neither_chunk_loaded",
                TICKS, RoutingTests::deliversAcrossDimensions);
    }

    private static void deliversAcrossDimensions(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var nether = server.getLevel(Level.NETHER);
        var owner = UUID.randomUUID();
        var reservoir = Place.of(overworld, OVERWORLD_WELL);
        var shortWell = Place.of(nether, NETHER_WELL);
        long target = FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow();
        assertUnloaded(helper, overworld, OVERWORLD_WELL);
        assertUnloaded(helper, nether, NETHER_WELL);
        VoidstoneNetworks.of(overworld).rejoin(OVERWORLD_WELL, StoreKind.WELL, Set.of(), Map.of(4, 1_000L));
        VoidstoneNetworks.of(nether).rejoin(NETHER_WELL, StoreKind.WELL, Set.of(), Map.of(4, target - SHORT));
        var wells = SavedVoidWells.of(server);
        wells.put(new VoidWells.Entry(reservoir, owner, FillTarget.NONE));
        wells.put(new VoidWells.Entry(shortWell, owner, FillTarget.QUARTER));
        var dragon = SavedVoidDragons.of(server).add(owner, reservoir);
        helper.succeedWhen(() -> {
            helper.assertTrue(total(nether, NETHER_WELL) == target, "the Nether Well holds " + total(nether, NETHER_WELL) + ", not its target " + target);
            helper.assertTrue(total(overworld, OVERWORLD_WELL) == 1_000 - SHORT, "the reservoir holds " + total(overworld, OVERWORLD_WELL));
            var after = SavedVoidDragons.of(server).get(dragon.id()).orElseThrow();
            helper.assertTrue(after.trip().isEmpty() && after.load().isEmpty(), "the dragon is still on its trip");
            helper.assertTrue(after.rest().equals(shortWell), "the dragon rests at " + after.rest() + ", not the Well it last stopped at");
            assertUnloaded(helper, overworld, OVERWORLD_WELL);
            assertUnloaded(helper, nether, NETHER_WELL);
            // The dragon stays, idle, with no Well of its owner's left to serve.
            VoidstoneNetworks.of(overworld).leave(OVERWORLD_WELL);
            VoidstoneNetworks.of(nether).leave(NETHER_WELL);
            wells.remove(reservoir);
            wells.remove(shortWell);
        });
    }

    private static long total(ServerLevel level, BlockPos pos) {
        return VoidstoneNetworks.of(level).store(pos).map(MoteStore::total).orElse(0L);
    }

    private static void assertUnloaded(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        helper.assertTrue(!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4), "the chunk of " + pos + " in " + level.dimension().identifier() + " is loaded");
    }
}
