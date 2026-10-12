// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.mojang.serialization.JsonOps;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

/**
 * Voidstone networks: touching Voidstone is one network, a single pool of motes whose capacity
 * grows by a fixed amount per Voidstone, and networks that come to touch merge.
 */
class VoidNetworksTest {

    private static final int PER_VOIDSTONE = 16;
    private static final BlockPos A = new BlockPos(0, 64, 0);

    private final VoidNetworks networks = new VoidNetworks(() -> PER_VOIDSTONE);

    @Test
    void aLoneVoidstoneIsANetworkHoldingOneVoidstonesWorth() {
        networks.join(A);
        assertEquals(PER_VOIDSTONE, networks.at(A).orElseThrow().capacity());
    }

    @Test
    void aPositionWithNoVoidstoneIsInNoNetwork() {
        networks.join(A);
        assertTrue(networks.at(A.east()).isEmpty());
    }

    @Test
    void touchingVoidstoneIsOneNetworkWhoseCapacityGrowsWithEach() {
        networks.join(A);
        networks.join(A.east());
        networks.join(A.east().above());
        var network = networks.at(A).orElseThrow();
        assertSame(network, networks.at(A.east().above()).orElseThrow());
        assertEquals(3 * PER_VOIDSTONE, network.capacity());
    }

    @Test
    void voidstoneTouchingOnlyAtAnEdgeIsNotTheSameNetwork() {
        networks.join(A);
        networks.join(A.east().above());
        assertEquals(PER_VOIDSTONE, networks.at(A).orElseThrow().capacity());
    }

    @Test
    void motesPutInAtOneVoidstoneAreThereAtAnother() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, 10);
        assertEquals(Map.of(3, 10), VoidSpender.countByGrade(networks.at(A.east()).orElseThrow()));
    }

    @Test
    void aNetworkHoldsNoMoreThanItsCapacityAcrossEveryGrade() {
        networks.join(A);
        assertEquals(PER_VOIDSTONE - 1, insert(A, 3, PER_VOIDSTONE - 1));
        assertEquals(1, insert(A, 4, 5));
        assertEquals(0, insert(A, 3, 1));
        assertEquals(Map.of(3, PER_VOIDSTONE - 1, 4, 1), VoidSpender.countByGrade(networks.at(A).orElseThrow()));
    }

    @Test
    void aVoidstoneJoiningTwoNetworksMergesThemAndTheirMotes() {
        var west = A;
        var east = A.east(2);
        networks.join(west);
        networks.join(east);
        insert(west, 3, 5);
        insert(east, 3, 2);
        insert(east, 4, 1);

        networks.join(A.east());

        var merged = networks.at(west).orElseThrow();
        assertSame(merged, networks.at(east).orElseThrow());
        assertEquals(3 * PER_VOIDSTONE, merged.capacity());
        assertEquals(Map.of(3, 7, 4, 1), VoidSpender.countByGrade(merged));
    }

    @Test
    void theRoomAVoidstoneWouldHaveTouchingNothingIsItsOwn() {
        assertEquals(PER_VOIDSTONE, networks.roomIfJoined(A));
    }

    @Test
    void theRoomAVoidstoneWouldHaveCountsTheNetworksItWouldJoin() {
        networks.join(A.west());
        networks.join(A.east());
        insert(A.west(), 3, 10);
        insert(A.east(), 3, PER_VOIDSTONE);
        // Its own, and what the west network has spare; the east network is full.
        assertEquals(PER_VOIDSTONE + PER_VOIDSTONE - 10, networks.roomIfJoined(A));
    }

    @Test
    void aVoidstoneThatGoesTakesItsCapacityAndTheMotesBeyondIt() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, 2 * PER_VOIDSTONE);

        networks.leave(A.east());

        assertTrue(networks.at(A.east()).isEmpty());
        var left = networks.at(A).orElseThrow();
        assertEquals(PER_VOIDSTONE, left.capacity());
        assertEquals(Map.of(3, PER_VOIDSTONE), VoidSpender.countByGrade(left));
    }

    @Test
    void anAbortedInsertLeavesTheNetworkAsItWas() {
        networks.join(A);
        try (var transaction = Transaction.openRoot()) {
            networks.at(A).orElseThrow().insert(MoteResource.of(3), 5, transaction);
        }
        assertEquals(Map.of(), VoidSpender.countByGrade(networks.at(A).orElseThrow()));
    }

    @Test
    void networksAreSavedWithTheirVoidstoneAndMotes() {
        networks.join(A);
        networks.join(A.east());
        networks.join(A.east(5));
        insert(A, 3, 7);
        insert(A.east(5), 5, 2);

        var codec = VoidNetworks.codec(() -> PER_VOIDSTONE);
        var saved = codec.encodeStart(JsonOps.INSTANCE, networks).getOrThrow();
        var read = codec.parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertSame(read.at(A).orElseThrow(), read.at(A.east()).orElseThrow());
        assertEquals(2 * PER_VOIDSTONE, read.at(A).orElseThrow().capacity());
        assertEquals(Map.of(3, 7), VoidSpender.countByGrade(read.at(A).orElseThrow()));
        assertEquals(Map.of(5, 2), VoidSpender.countByGrade(read.at(A.east(5)).orElseThrow()));
    }

    private int insert(BlockPos pos, int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = networks.at(pos).orElseThrow().insert(MoteResource.of(grade), count, transaction);
            transaction.commit();
            return inserted;
        }
    }
}
