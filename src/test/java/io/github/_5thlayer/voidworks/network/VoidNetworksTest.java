// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

/**
 * Voidstone networks: touching Voidstone, and Void Wells, are one network, each position holding
 * its own motes, and networks that come to touch merge. Motes conduct between touching positions,
 * each grade from high network pressure to low, a step at a time and without loss.
 */
class VoidNetworksTest {

    private static final int PER_VOIDSTONE = 16;
    private static final int PER_WELL = 1024;
    private static final int RATE = 4;
    private static final BlockPos A = new BlockPos(0, 64, 0);

    private final VoidNetworks networks = new VoidNetworks(() -> PER_VOIDSTONE, () -> PER_WELL);

    @Test
    void aLoneVoidstoneIsANetworkHoldingOneVoidstonesWorth() {
        networks.join(A);
        assertEquals(PER_VOIDSTONE, networks.at(A).orElseThrow().capacity());
        assertEquals(PER_VOIDSTONE, networks.store(A).orElseThrow().capacity());
    }

    @Test
    void aPositionWithNoVoidstoneIsInNoNetwork() {
        networks.join(A);
        assertTrue(networks.at(A.east()).isEmpty());
        assertTrue(networks.store(A.east()).isEmpty());
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
    void aVoidstoneHoldsNoMoreThanItsCapacityAcrossEveryGrade() {
        networks.join(A);
        assertEquals(PER_VOIDSTONE - 1, insert(A, 3, PER_VOIDSTONE - 1));
        assertEquals(1, insert(A, 4, 5));
        assertEquals(0, insert(A, 3, 1));
        assertEquals(Map.of(3, PER_VOIDSTONE - 1, 4, 1), held(A));
    }

    @Test
    void motesPutInAtOneVoidstoneAreOnlyThereUntilTheyConduct() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, 10);
        assertEquals(Map.of(), held(A.east()));
        assertEquals(Map.of(3, 10L), networks.at(A.east()).orElseThrow().countByGrade());
    }

    @Test
    void aDepositFillsWhereItArrivesThenTheNearestVoidstoneWithRoom() {
        networks.join(A);
        networks.join(A.east());
        networks.join(A.east(2));
        networks.join(A.west());

        assertEquals(3 * PER_VOIDSTONE, deposit(A.east(2), 3, 3 * PER_VOIDSTONE));

        assertEquals(Map.of(3, PER_VOIDSTONE), held(A.east(2)));
        assertEquals(Map.of(3, PER_VOIDSTONE), held(A.east()));
        assertEquals(Map.of(3, PER_VOIDSTONE), held(A));
        assertEquals(Map.of(), held(A.west()));
    }

    @Test
    void aDepositStopsWhenTheNetworkIsFull() {
        networks.join(A);
        networks.join(A.east());
        assertEquals(2 * PER_VOIDSTONE, deposit(A, 3, 3 * PER_VOIDSTONE));
    }

    @Test
    void motesConductFromAFullerVoidstoneToAnEmptierOneAtTheRate() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, PER_VOIDSTONE);

        var flows = networks.conduct(RATE);

        assertEquals(List.of(new VoidNetworks.Flow(A, A.east(), 3, RATE)), flows);
        assertEquals(Map.of(3, PER_VOIDSTONE - RATE), held(A));
        assertEquals(Map.of(3, RATE), held(A.east()));
    }

    @Test
    void conductionEvensTwoVoidstoneOutAndThenStops() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, PER_VOIDSTONE);

        for (int step = 0; step < 10; step++) {
            networks.conduct(RATE);
        }

        assertEquals(Map.of(3, PER_VOIDSTONE / 2), held(A));
        assertEquals(Map.of(3, PER_VOIDSTONE / 2), held(A.east()));
        assertEquals(List.of(), networks.conduct(RATE));
    }

    @Test
    void conductionNeverTurnsTheDifferenceAround() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, 3);

        networks.conduct(RATE);

        assertEquals(Map.of(3, 2), held(A));
        assertEquals(Map.of(3, 1), held(A.east()));
    }

    @Test
    void motesTooFewToEvenOutALineOfVoidstoneStayShortOfItsEnd() {
        for (int x = 0; x < 5; x++) {
            networks.join(A.east(x));
        }
        insert(A, 3, 10);

        for (int step = 0; step < 20; step++) {
            networks.conduct(RATE);
        }

        assertEquals(Map.of(), held(A.east(4)));
    }

    @Test
    void motesTooFewToEvenOutALineOfVoidstoneAllReachWhereTheyAreWantedAtItsEnd() {
        var end = A.east(4);
        for (int x = 0; x < 5; x++) {
            networks.join(A.east(x));
        }
        insert(A, 3, 10);

        int drawn = 0;
        for (int step = 0; step < 100; step++) {
            // A spender at the end draws on it, taking all it holds.
            networks.want(end);
            networks.conduct(RATE);
            drawn += held(end).getOrDefault(3, 0);
            try (var transaction = Transaction.openRoot()) {
                networks.store(end).orElseThrow().extract(MoteStore.slot(3), MoteResource.of(3), Integer.MAX_VALUE, transaction);
                transaction.commit();
            }
        }

        assertEquals(10, drawn);
    }

    @Test
    void motesStopWhereTheyAreWanted() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, 1);
        networks.want(A.east());

        networks.conduct(RATE);
        assertEquals(Map.of(3, 1), held(A.east()));
        networks.want(A.east());
        assertEquals(List.of(), networks.conduct(RATE));
    }

    @Test
    void motesOnceWantedSettleWhenNoLongerAre() {
        networks.join(A);
        networks.join(A.east());
        networks.join(A.east(2));
        insert(A, 3, 1);
        insert(A.east(), 3, 1);
        networks.want(A.east(2));

        for (int step = 0; step < VoidNetworks.WANTED_STEPS + 1; step++) {
            networks.conduct(RATE);
        }

        assertEquals(List.of(), networks.conduct(RATE));
    }

    @Test
    void aLoneMoteStaysPut() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, 1);

        assertEquals(List.of(), networks.conduct(RATE));
    }

    @Test
    void motesTooFewToEvenOutASquareOfVoidstoneSettle() {
        networks.join(A);
        networks.join(A.east());
        networks.join(A.south());
        networks.join(A.east().south());
        insert(A, 3, 1);
        insert(A.east(), 3, 1);
        insert(A.south(), 3, 1);

        assertEquals(List.of(), networks.conduct(RATE));
    }

    @Test
    void aMoteMovesOneVoidstoneAStep() {
        networks.join(A);
        networks.join(A.east());
        networks.join(A.east(2));
        insert(A, 3, PER_VOIDSTONE);

        networks.conduct(RATE);

        assertEquals(Map.of(), held(A.east(2)));
        networks.conduct(RATE);
        assertTrue(held(A.east(2)).getOrDefault(3, 0) > 0);
    }

    @Test
    void eachGradeConductsOnItsOwn() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, 8);
        insert(A.east(), 5, 8);

        networks.conduct(RATE);

        assertEquals(Map.of(3, 4, 5, 4), held(A));
        assertEquals(Map.of(3, 4, 5, 4), held(A.east()));
    }

    @Test
    void conductionLosesNoMotes() {
        for (int x = 0; x < 6; x++) {
            networks.join(A.east(x));
        }
        deposit(A, 3, 40);
        deposit(A.east(5), 6, 20);

        for (int step = 0; step < 50; step++) {
            networks.conduct(RATE);
        }

        assertEquals(Map.of(3, 40L, 6, 20L), networks.at(A).orElseThrow().countByGrade());
    }

    @Test
    void aWellSoaksUpMostOfANetworksMotes() {
        networks.join(A);
        networks.join(A.east(), StoreKind.WELL);
        insert(A, 3, PER_VOIDSTONE);

        for (int step = 0; step < 10; step++) {
            networks.conduct(RATE);
        }

        assertEquals(Map.of(3, 1), held(A));
        assertEquals(Map.of(3, PER_VOIDSTONE - 1), held(A.east()));
    }

    @Test
    void aWellGivesBackOnlyOnceNearlyFull() {
        networks.join(A);
        networks.join(A.east(), StoreKind.WELL);
        insert(A.east(), 3, PER_WELL / 2);

        networks.conduct(RATE);
        assertEquals(Map.of(), held(A));

        insert(A.east(), 3, PER_WELL / 2);
        networks.conduct(RATE);
        assertEquals(Map.of(3, RATE), held(A));
    }

    @Test
    void aVoidstoneJoiningTwoNetworksMergesThemWithEachKeepingItsMotes() {
        var west = A;
        var east = A.east(2);
        networks.join(west);
        networks.join(east);
        insert(west, 3, 5);
        insert(east, 4, 1);

        networks.join(A.east());

        var merged = networks.at(west).orElseThrow();
        assertSame(merged, networks.at(east).orElseThrow());
        assertEquals(3 * PER_VOIDSTONE, merged.capacity());
        assertEquals(Map.of(3, 5L, 4, 1L), merged.countByGrade());
        assertEquals(Map.of(3, 5), held(west));
    }

    @Test
    void aWellJoinsTheNetworkItTouches() {
        networks.join(A);
        networks.join(A.east(), StoreKind.WELL);
        assertSame(networks.at(A).orElseThrow(), networks.at(A.east()).orElseThrow());
        assertEquals(PER_VOIDSTONE + PER_WELL, networks.at(A).orElseThrow().capacity());
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
    void aStoreThatGoesLosesItsMotesAndTheRestKeepTheirs() {
        networks.join(A);
        networks.join(A.east());
        insert(A, 3, PER_VOIDSTONE);
        insert(A.east(), 3, 5);

        networks.leave(A.east());

        assertTrue(networks.at(A.east()).isEmpty());
        assertEquals(Map.of(3, PER_VOIDSTONE), held(A));
        assertEquals(PER_VOIDSTONE, networks.at(A).orElseThrow().capacity());
    }

    @Test
    void aStoreThatGoesSplitsTheNetworkItJoined() {
        networks.join(A.west());
        networks.join(A);
        networks.join(A.east());

        networks.leave(A);

        assertNotSame(networks.at(A.west()).orElseThrow(), networks.at(A.east()).orElseThrow());
        assertEquals(PER_VOIDSTONE, networks.at(A.west()).orElseThrow().capacity());
        assertEquals(PER_VOIDSTONE, networks.at(A.east()).orElseThrow().capacity());
    }

    @Test
    void anAbortedInsertLeavesTheStoreAsItWas() {
        networks.join(A);
        try (var transaction = Transaction.openRoot()) {
            networks.store(A).orElseThrow().insert(MoteResource.of(3), 5, transaction);
        }
        assertEquals(Map.of(), held(A));
    }

    @Test
    void networksAreSavedWithEachStoreAndItsMotes() {
        networks.join(A);
        networks.join(A.east(), StoreKind.WELL);
        networks.join(A.east(5));
        insert(A, 3, 7);
        insert(A.east(), 4, 9);
        insert(A.east(5), 5, 2);

        var codec = VoidNetworks.codec(() -> PER_VOIDSTONE, () -> PER_WELL);
        var saved = codec.encodeStart(JsonOps.INSTANCE, networks).getOrThrow();
        var read = codec.parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertSame(read.at(A).orElseThrow(), read.at(A.east()).orElseThrow());
        assertEquals(PER_VOIDSTONE + PER_WELL, read.at(A).orElseThrow().capacity());
        assertEquals(StoreKind.WELL, read.store(A.east()).orElseThrow().kind());
        assertEquals(Map.of(3, 7), VoidSpender.countByGrade(read.store(A).orElseThrow()));
        assertEquals(Map.of(4, 9), VoidSpender.countByGrade(read.store(A.east()).orElseThrow()));
        assertEquals(Map.of(5, 2), VoidSpender.countByGrade(read.store(A.east(5)).orElseThrow()));
    }

    @Test
    void aPoolSavedBeforeConductionFillsItsVoidstoneInTurn() {
        var saved = JsonParser.parseString("""
                [{"voidstone": [[0, 64, 0], [1, 64, 0]], "motes": [{"grade": 3, "count": 20}]}]
                """);

        var read = VoidNetworks.codec(() -> PER_VOIDSTONE, () -> PER_WELL).parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertSame(read.at(A).orElseThrow(), read.at(A.east()).orElseThrow());
        assertEquals(Map.of(3, 20L), read.at(A).orElseThrow().countByGrade());
    }

    private Map<Integer, Integer> held(BlockPos pos) {
        return VoidSpender.countByGrade(networks.store(pos).orElseThrow());
    }

    private int insert(BlockPos pos, int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = networks.store(pos).orElseThrow().insert(MoteResource.of(grade), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private int deposit(BlockPos pos, int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = networks.deposit(pos, MoteResource.of(grade), count, transaction);
            transaction.commit();
            return inserted;
        }
    }
}
