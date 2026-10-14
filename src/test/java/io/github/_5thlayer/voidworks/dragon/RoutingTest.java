// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * A Void Dragon's routing on its record alone: it flies only to a Well below its target, taking from
 * the owner's Well of highest network pressure that has something worth delivering there, a reservoir
 * or a Well above its target; it carries only grades worth something at the destination, highest
 * first, up to its load; its trip is claimed, so dragons sharing a Well never overshoot it; and a load
 * its destination no longer takes goes on elsewhere, or waits.
 */
class RoutingTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");
    private static final Identifier END = Identifier.withDefaultNamespace("the_end");

    private static final long CAPACITY = 1_000;
    private static final long LOAD = 100;
    private static final int STOP = 100;
    /** A dive of 200 ticks and a second per 64 blocks, the Nether's counting eight. */
    private static final Dive DIVE = new Dive(200, 64, dimension -> dimension.equals(NETHER) ? 8.0 : 1.0);

    private static final Place END_WELL = new Place(END, new BlockPos(0, 64, 0));
    private static final Place NETHER_WELL = new Place(NETHER, new BlockPos(80, 64, 0));
    private static final Place OVERWORLD_WELL = new Place(OVERWORLD, new BlockPos(0, 64, 640));
    private static final Place OTHER_WELL = new Place(OVERWORLD, new BlockPos(64, 64, 640));

    private final FakeWells wells = new FakeWells();
    private final VoidDragons dragons = new VoidDragons();
    private final Routing routing = new Routing(wells, DIVE, LOAD, STOP);
    private long now;

    @Test
    void aDragonCarriesFromAReservoirToAWellBelowItsTargetUntilItIsMet() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 500L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(250), 1, Map.of());
        dragons.add(ALICE, END_WELL);
        runFor(20_000);
        assertEquals(Map.of(4, 250L), wells.motes(NETHER_WELL));
        assertEquals(Map.of(4, 250L), wells.motes(END_WELL));
    }

    @Test
    void itCarriesOnlyGradesWorthSomethingAtTheDestinationHighestFirstUpToItsLoad() {
        // The Overworld's pressure is 2: grade 2 is worth nothing there.
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(2, 300L, 3, 60L, 5, 70L));
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.of(1_000), 2, Map.of());
        var dragon = dragons.add(ALICE, END_WELL);
        runFor(1);
        // The load is 100: all 70 of grade 5, then 30 of grade 3, and none of grade 2.
        assertEquals(Map.of(5, 70L, 3, 30L), dragons.get(dragon.id()).orElseThrow().load());
        assertEquals(Map.of(2, 300L, 3, 30L), wells.motes(END_WELL));
    }

    @Test
    void aWellOverItsTargetGivesOnlyItsExcess() {
        wells.add(ALICE, END_WELL, OptionalLong.of(250), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(1_000), 1, Map.of());
        dragons.add(ALICE, END_WELL);
        runFor(20_000);
        assertEquals(Map.of(4, 250L), wells.motes(END_WELL));
        assertEquals(Map.of(4, 50L), wells.motes(NETHER_WELL));
    }

    @Test
    void aWellBelowItsTargetNeverGives() {
        wells.add(ALICE, END_WELL, OptionalLong.of(500), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(1_000), 1, Map.of());
        var dragon = dragons.add(ALICE, END_WELL);
        runFor(20_000);
        assertEquals(Map.of(4, 300L), wells.motes(END_WELL));
        assertTrue(dragons.get(dragon.id()).orElseThrow().trip().isEmpty(), "the dragon took a trip");
    }

    @Test
    void aDragonServesOnlyItsOwnersWells() {
        wells.add(BOB, END_WELL, OptionalLong.empty(), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(1_000), 1, Map.of());
        dragons.add(ALICE, NETHER_WELL);
        runFor(20_000);
        assertEquals(Map.of(4, 300L), wells.motes(END_WELL));
        assertEquals(Map.of(), wells.motes(NETHER_WELL));
    }

    @Test
    void twoDragonsServingOneShortWellDoNotOvershootItsTarget() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 1_000L));
        // 150 short: one full load and half another.
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(150), 1, Map.of());
        var first = dragons.add(ALICE, END_WELL);
        var second = dragons.add(ALICE, END_WELL);
        runFor(1);
        assertEquals(100, dragons.get(first.id()).orElseThrow().carried());
        assertEquals(50, dragons.get(second.id()).orElseThrow().carried());
        runFor(20_000);
        assertEquals(Map.of(4, 150L), wells.motes(NETHER_WELL));
    }

    @Test
    void aSecondDragonDoesNotSetOffForMotesAnotherIsAlreadyOnItsWayToLoad() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 50L));
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(4, 60L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(1_000), 1, Map.of());
        wells.add(ALICE, OTHER_WELL, OptionalLong.of(1_000), 2, Map.of());
        // Both rest far from the Overworld reservoir, the fuller, so both would dive there.
        var first = dragons.add(ALICE, NETHER_WELL);
        var second = dragons.add(ALICE, NETHER_WELL);
        runFor(1);
        assertEquals(Optional.of(OVERWORLD_WELL), dragons.get(first.id()).orElseThrow().trip().orElseThrow().source());
        // The 60 there are spoken for: the second goes to the End's.
        assertEquals(Optional.of(END_WELL), dragons.get(second.id()).orElseThrow().trip().orElseThrow().source());
    }

    @Test
    void aDiveTakesItsTravelTimeWithNetherDistancesCountedEightfold() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        dragons.add(ALICE, END_WELL);
        // It loads at once, stops 100 ticks, then dives 640 Overworld blocks: 400 ticks.
        runFor(1 + STOP + 399);
        assertEquals(Map.of(), wells.motes(NETHER_WELL));
        runFor(1);
        assertEquals(Map.of(4, 50L), wells.motes(NETHER_WELL));
    }

    @Test
    void aDragonFirstDivesToTheSourceFromWhereItRests() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        var dragon = dragons.add(ALICE, NETHER_WELL);
        runFor(400);
        assertTrue(dragons.get(dragon.id()).orElseThrow().load().isEmpty(), "the dragon loaded before its dive ended");
        runFor(1);
        assertEquals(Map.of(4, 50L), dragons.get(dragon.id()).orElseThrow().load());
    }

    @Test
    void aLoadWhoseDestinationIsBrokenGoesToAnotherWellThatNeedsIt() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        dragons.add(ALICE, END_WELL);
        runFor(1);
        wells.remove(NETHER_WELL);
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.of(50), 2, Map.of());
        runFor(20_000);
        assertEquals(Map.of(4, 50L), wells.motes(OVERWORLD_WELL));
        assertEquals(Map.of(4, 250L), wells.motes(END_WELL));
    }

    @Test
    void aLoadNoWellNeedsGoesToTheWellWithMostRoom() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(950), 1, Map.of(4, 900L));
        // Two Wells at their targets, which neither need nor give.
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.of(900), 2, Map.of(4, 900L));
        wells.add(ALICE, OTHER_WELL, OptionalLong.of(400), 2, Map.of(4, 400L));
        var dragon = dragons.add(ALICE, END_WELL);
        runFor(1);
        // No longer short when it arrives.
        wells.put(NETHER_WELL, 4, 50);
        runFor(20_000);
        // The End's Well, where the load came from, has room for 750; the others less.
        assertEquals(Map.of(4, 300L), wells.motes(END_WELL));
        assertEquals(Map.of(4, 950L), wells.motes(NETHER_WELL));
        assertEquals(Map.of(4, 400L), wells.motes(OTHER_WELL));
        assertEquals(END_WELL, dragons.get(dragon.id()).orElseThrow().rest());
    }

    @Test
    void withRoomNowhereADragonHoldsItsLoadUntilThereIs() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        var dragon = dragons.add(ALICE, END_WELL);
        runFor(1);
        wells.put(END_WELL, 4, CAPACITY);
        wells.put(NETHER_WELL, 4, CAPACITY);
        runFor(20_000);
        var holding = dragons.get(dragon.id()).orElseThrow();
        assertEquals(Map.of(4, 50L), holding.load());
        assertTrue(holding.trip().isEmpty(), "the dragon is still on a trip");
        assertEquals(NETHER_WELL, holding.rest());
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of());
        runFor(20_000);
        assertEquals(Map.of(4, 50L), wells.motes(OVERWORLD_WELL));
        assertTrue(dragons.get(dragon.id()).orElseThrow().load().isEmpty(), "the dragon still holds its load");
    }

    @Test
    void withNothingToDoADragonRestsAtTheWellItLastStoppedAt() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 300L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        var dragon = dragons.add(ALICE, new Place(OVERWORLD, new BlockPos(9, 70, 9)));
        runFor(20_000);
        var rested = dragons.get(dragon.id()).orElseThrow();
        assertEquals(NETHER_WELL, rested.rest());
        assertTrue(rested.trip().isEmpty(), "the dragon is still on a trip");
        assertTrue(rested.load().isEmpty(), "the dragon still carries a load");
    }

    /** Runs routing a step a tick for {@code ticks} ticks. */
    private void runFor(long ticks) {
        for (long end = now + ticks; now < end; now++) {
            routing.step(dragons, now);
        }
    }

    /** Wells as plain data, each its owner's, whole and awake until broken. */
    private static final class FakeWells implements Wells {

        private record Entry(UUID owner, OptionalLong target, int pressure, TreeMap<Integer, Long> motes) {
        }

        private final Map<Place, Entry> wells = new LinkedHashMap<>();

        void add(UUID owner, Place place, OptionalLong target, int pressure, Map<Integer, Long> motes) {
            wells.put(place, new Entry(owner, target, pressure, new TreeMap<>(motes)));
        }

        void remove(Place place) {
            wells.remove(place);
        }

        Map<Integer, Long> motes(Place place) {
            var motes = new TreeMap<>(wells.get(place).motes());
            motes.values().removeIf(count -> count == 0);
            return motes;
        }

        @Override
        public List<Well> of(UUID owner) {
            var owned = new ArrayList<Well>();
            wells.forEach((place, entry) -> {
                if (entry.owner().equals(owner)) {
                    owned.add(new Well(place, entry.target(), entry.pressure(), CAPACITY, entry.motes()));
                }
            });
            return owned;
        }

        @Override
        public long take(Place place, int grade, long count) {
            if (!wells.containsKey(place)) {
                return 0;
            }
            var motes = wells.get(place).motes();
            long taken = Math.min(count, motes.getOrDefault(grade, 0L));
            motes.merge(grade, -taken, Long::sum);
            return taken;
        }

        @Override
        public long put(Place place, int grade, long count) {
            if (!wells.containsKey(place)) {
                return 0;
            }
            var motes = wells.get(place).motes();
            long total = motes.values().stream().mapToLong(Long::longValue).sum();
            long put = Math.min(count, CAPACITY - total);
            motes.merge(grade, put, Long::sum);
            return put;
        }
    }
}
