// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The Void Dragons, server state rather than entities (ADR 0005): each a record of its owner, its
 * health, where it rests, its load, its claimed trip and the motes it has carried since its last
 * egg, kept across a save and reload.
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
    void removingByPlaceTakesEveryDragonRestingThereOrOnATripToOrFromItOrToAStopAddedThere() {
        var elsewhere = new Place(Identifier.withDefaultNamespace("overworld"), new BlockPos(100, 64, 100));
        var resting = dragons.add(ALICE, HERE);
        var bound = dragons.add(ALICE, elsewhere);
        dragons.update(bound.on(Optional.of(new Trip(Optional.empty(), HERE, 70, false, Trip.Leg.TO_DESTINATION, 1_234, 1_000, 0))));
        var loading = dragons.add(UUID.randomUUID(), elsewhere);
        dragons.update(loading.on(Optional.of(new Trip(Optional.of(HERE), THERE, 70, false, Trip.Leg.TO_SOURCE, 1_234, 1_000, 0))));
        var ridden = dragons.add(ALICE, elsewhere);
        dragons.update(ridden.on(Optional.of(new Trip(Optional.empty(), THERE, 70, false, Trip.Leg.TO_PICKED, 1_234, 1_000, 0, Optional.of(HERE)))));
        var away = dragons.add(ALICE, elsewhere);

        var removed = dragons.removeAt(HERE::equals);

        assertEquals(List.of(resting.id(), bound.id(), loading.id(), ridden.id()), removed.stream().map(VoidDragonRecord::id).toList());
        assertEquals(List.of(away), List.copyOf(dragons.all()));
    }

    @Test
    void removingByPlaceWhereNoDragonIsChangesNothing() {
        var dragon = dragons.add(ALICE, HERE);
        var changes = new int[1];
        dragons.onChange(() -> changes[0]++);

        assertTrue(dragons.removeAt(THERE::equals).isEmpty());
        assertEquals(List.of(dragon), List.copyOf(dragons.all()));
        assertEquals(0, changes[0]);
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
        var trip = new Trip(Optional.of(HERE), THERE, 70, false, Trip.Leg.TO_DESTINATION, 1_234, 1_000, 23_400);
        var carrying = dragon.carrying(MoteLoad.of(Map.of(5, 40L, 3, 30L))).on(Optional.of(trip));
        dragons.update(carrying);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(carrying, read.get(dragon.id()).orElseThrow());
    }

    @Test
    void aSaveAndReloadKeepsAStopAddedToATrip() {
        var dragon = dragons.add(ALICE, HERE);
        var called = dragon.on(Optional.of(new Trip(Optional.of(HERE), THERE, 70, false, Trip.Leg.WAITING, 1_234, 1_000, 0, Optional.of(HERE))));
        dragons.update(called);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(called, read.get(dragon.id()).orElseThrow());
    }

    @Test
    void aSaveAndReloadKeepsHowAStopIsSeenUnloading() {
        var dragon = dragons.add(ALICE, HERE);
        var cobblestone = Ware.of(Identifier.withDefaultNamespace("cobblestone"), 64, 64);
        var seen = Handling.unloading(List.of(new Passing(THERE, cobblestone)), Optional.of(cobblestone));
        var unloading = dragon.on(Optional.of(new Trip(Optional.empty(), THERE, 0, false, Trip.Leg.UNLOADING, 1_234, 1_000, 0).seen(seen)));
        dragons.update(unloading);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(unloading, read.get(dragon.id()).orElseThrow());
    }

    @Test
    void aSaveAndReloadKeepsAHurtDragonsHealth() {
        var dragon = dragons.add(ALICE, HERE);
        var hurt = dragon.weakened(63);
        dragons.update(hurt);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(37, read.get(dragon.id()).orElseThrow().health());
    }

    @Test
    void aSaveAndReloadKeepsTheMotesCarriedSinceItsLastEgg() {
        var dragon = dragons.add(ALICE, HERE);
        var carried = dragon.unloaded(5_000, 3_000);
        dragons.update(carried);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(3_000, read.get(dragon.id()).orElseThrow().towardEgg());
        assertEquals(5_000, read.get(dragon.id()).orElseThrow().cargo());
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
        assertEquals(0, read.towardEgg());
        assertEquals(HERE, read.rest());
    }

    @Test
    void aLoadSavedBeforeTheCargoSeamReadsIntact() {
        var saved = JsonParser.parseString("""
                [{"id": [0, 0, 0, 1], "owner": [0, 0, 0, 2], "health": 100,
                  "rest": {"dimension": "minecraft:overworld", "pos": [1, 64, 2]}, "load": {"5": 40, "3": 30}}]
                """);
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().all().iterator().next();
        assertEquals(MoteLoad.of(Map.of(5, 40L, 3, 30L)), read.load());
        assertEquals(70, read.carried());
        assertEquals(saved, VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow()).getOrThrow());
    }

    @Test
    void aDragonSavedBeforeLifeStagesReadsYoungWithItsEggCountDropped() {
        var saved = JsonParser.parseString("""
                [{"id": [0, 0, 0, 1], "owner": [0, 0, 0, 2], "health": 100,
                  "rest": {"dimension": "minecraft:overworld", "pos": [1, 64, 2]}, "since_egg": 40000}]
                """);
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().all().iterator().next();
        assertEquals(0, read.towardEgg());
        assertEquals(0, read.cargo());
    }

    @Test
    void aDragonIsOfTheLineItHatchedOfAndASaveAndReloadKeepsIt() {
        var matter = dragons.add(ALICE, Line.MATTER, HERE);
        var mote = dragons.add(ALICE, HERE);
        assertEquals(Line.MATTER, matter.line());
        assertEquals(Line.MOTE, mote.line());
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(matter, read.get(matter.id()).orElseThrow());
        assertEquals(mote, read.get(mote.id()).orElseThrow());
    }

    @Test
    void aDragonSavedBeforeLinesReadsAsTheMoteLine() {
        var saved = JsonParser.parseString("""
                [{"id": [0, 0, 0, 1], "owner": [0, 0, 0, 2], "health": 100,
                  "rest": {"dimension": "minecraft:overworld", "pos": [1, 64, 2]}, "load": {"5": 40}}]
                """);
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().all().iterator().next();
        assertEquals(Line.MOTE, read.line());
    }

    @Test
    void aDiveSavedBeforeDragonsWeakenedArrivesWhenItWasTo() {
        var saved = JsonParser.parseString("""
                {"destination": {"dimension": "minecraft:the_end", "pos": [-5, 70, 9]}, "claimed": 70,
                 "leg": "to_destination", "until": 1234}
                """);
        var read = Trip.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(1_234, read.until());
        assertEquals(1_234, read.countedTo());
        assertEquals(0, read.toCover());
    }

    @Test
    void anAddIsReported() {
        int[] changes = {0};
        dragons.onChange(() -> changes[0]++);
        dragons.add(ALICE, HERE);
        assertTrue(changes[0] == 1);
    }
}
