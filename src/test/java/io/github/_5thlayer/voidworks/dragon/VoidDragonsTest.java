// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The Void Dragons, server state rather than entities (ADR 0005): each a record of its owner, its
 * health, where it rests, its load and its claimed trip, kept across a save and reload.
 */
class VoidDragonsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final Place HERE = new Place(Identifier.withDefaultNamespace("overworld"), new BlockPos(1, 64, 2));
    private static final Place THERE = new Place(Identifier.withDefaultNamespace("the_end"), new BlockPos(-5, 70, 9));

    private final VoidDragons dragons = new VoidDragons();

    @Test
    void aDragonGrownUpRestsWhereItGrewAtFullHealth() {
        var dragon = dragons.add(ALICE, HERE);
        assertEquals(ALICE, dragon.owner());
        assertEquals(HERE, dragon.rest());
        assertEquals(VoidDragonRecord.FULL_HEALTH, dragon.health());
        assertEquals(dragon, dragons.get(dragon.id()).orElseThrow());
    }

    @Test
    void eachDragonIsItsOwn() {
        var first = dragons.add(ALICE, HERE);
        var second = dragons.add(ALICE, HERE);
        assertNotEquals(first.id(), second.id());
        assertEquals(List.of(first, second).size(), dragons.all().size());
    }

    @Test
    void aSaveAndReloadKeepsEveryDragon() {
        var first = dragons.add(ALICE, HERE);
        var second = dragons.add(UUID.randomUUID(), THERE);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(first, read.get(first.id()).orElseThrow());
        assertEquals(second, read.get(second.id()).orElseThrow());
        assertEquals(2, read.all().size());
    }

    @Test
    void aSaveAndReloadKeepsALoadAndAClaimedTrip() {
        var dragon = dragons.add(ALICE, HERE);
        var trip = new Trip(Optional.of(HERE), THERE, 70, false, Trip.Leg.TO_DESTINATION, 1_234);
        var carrying = dragon.carrying(new TreeMap<>(Map.of(5, 40L, 3, 30L))).on(Optional.of(trip));
        dragons.update(carrying);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(carrying, read.get(dragon.id()).orElseThrow());
    }

    @Test
    void aDragonSavedBeforeRoutingReadsWithNoLoadAndNoTrip() {
        var saved = JsonParser.parseString("""
                [{"id": [0, 0, 0, 1], "owner": [0, 0, 0, 2], "health": 100,
                  "rest": {"dimension": "minecraft:overworld", "pos": [1, 64, 2]}}]
                """);
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().all().iterator().next();
        assertTrue(read.load().isEmpty());
        assertTrue(read.trip().isEmpty());
        assertEquals(HERE, read.rest());
    }

    @Test
    void anAddIsReported() {
        int[] changes = {0};
        dragons.onChange(() -> changes[0]++);
        dragons.add(ALICE, HERE);
        assertTrue(changes[0] == 1);
    }
}
