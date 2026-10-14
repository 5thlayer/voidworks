// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
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
 * its destination no longer takes goes on elsewhere, or waits. Diving it weakens, hurt it flies more
 * slowly, and it heals from a Well's motes as it leaves it. An owner's Wells on one network are one
 * stop, judged by the highest of their targets, and another owner's Wells there never bind it.
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
    /** No harm anywhere, so a trip's motes go only where it carries them. */
    private static final Harm HARMLESS = new Harm(dimension -> 0, 50, 16);
    /** The End's pressure 3: the Overworld a step below it, the Nether two; a point a step every 50 ticks. */
    private static final Harm HARM = new Harm(dimension -> dimension.equals(NETHER) ? 2 : dimension.equals(OVERWORLD) ? 1 : 0, 50, 16);

    private static final Place END_WELL = new Place(END, new BlockPos(0, 64, 0));
    private static final Place NETHER_WELL = new Place(NETHER, new BlockPos(80, 64, 0));
    private static final Place OVERWORLD_WELL = new Place(OVERWORLD, new BlockPos(0, 64, 640));
    private static final Place OTHER_WELL = new Place(OVERWORLD, new BlockPos(64, 64, 640));
    private static final Place OTHER_END_WELL = new Place(END, new BlockPos(0, 64, 64));

    private final FakeWells wells = new FakeWells();
    private final VoidDragons dragons = new VoidDragons();
    private Routing routing = new Routing(wells, DIVE, HARMLESS, LOAD, STOP);
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

    @Test
    void aDragonWeakensOnlyWhileItDivesAtTheWorseOfItsEndsRate() {
        harming();
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of(4, 300L));
        wells.add(ALICE, END_WELL, OptionalLong.of(50), 3, Map.of());
        wells.add(BOB, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(4, 300L));
        wells.add(BOB, OTHER_END_WELL, OptionalLong.of(50), 3, Map.of());
        var fromTheNether = dragons.add(ALICE, NETHER_WELL);
        var fromTheOverworld = dragons.add(BOB, OVERWORLD_WELL);
        // Each loads for 100 ticks, costing nothing, then dives to the End, weakening as it goes:
        // by the Nether's 2 or the Overworld's 1 each 50 ticks.
        runFor(301);
        var halfway = dragons.get(fromTheNether.id()).orElseThrow();
        assertEquals(Optional.of(Trip.Leg.TO_DESTINATION), halfway.trip().map(Trip::leg));
        assertEquals(VoidDragonRecord.FULL_HEALTH - 8, halfway.health());
        runFor(20_000);
        assertEquals(Map.of(4, 50L), wells.motes(END_WELL));
        assertEquals(Map.of(4, 50L), wells.motes(OTHER_END_WELL));
        // 640 Overworld blocks, 400 ticks; 576, 380 ticks; then they unload and rest, costing nothing.
        assertEquals(VoidDragonRecord.FULL_HEALTH - 16, dragons.get(fromTheNether.id()).orElseThrow().health());
        assertEquals(VoidDragonRecord.FULL_HEALTH - 7, dragons.get(fromTheOverworld.id()).orElseThrow().health());
    }

    @Test
    void aRestingDragonNeitherWeakensNorHeals() {
        harming();
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of(4, 300L));
        var dragon = hurt(dragons.add(ALICE, NETHER_WELL), 50);
        runFor(20_000);
        assertEquals(50, dragons.get(dragon.id()).orElseThrow().health());
        assertEquals(Map.of(4, 300L), wells.motes(NETHER_WELL));
    }

    @Test
    void aHurtDragonsTripsTakeLonger() {
        harming();
        wells.add(ALICE, OTHER_END_WELL, OptionalLong.of(50), 3, Map.of());
        var dragon = carrying(ALICE, END_WELL, 50, Map.of(4, 50L));
        // At full health the dive of 64 blocks takes 220 ticks; at half health, twice that. Between End
        // Wells it never weakens, so it keeps its speed.
        runFor(440);
        assertEquals(Map.of(), wells.motes(OTHER_END_WELL));
        runFor(1);
        assertEquals(Map.of(4, 50L), wells.motes(OTHER_END_WELL));
        assertEquals(50, dragons.get(dragon.id()).orElseThrow().health());
    }

    @Test
    void aDragonWeakeningDuringADiveSlowsForTheRestOfIt() {
        harming();
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        var dragon = carrying(ALICE, END_WELL, 27, Map.of(4, 50L));
        // 400 ticks at full speed: 50 at 27% of it, then at 25 health the rest at a quarter, 1546 more,
        // where at the speed it set off at it would have taken 1482.
        runFor(1_596);
        assertEquals(Map.of(), wells.motes(NETHER_WELL));
        runFor(1);
        assertEquals(Map.of(4, 50L), wells.motes(NETHER_WELL));
    }

    @Test
    void aDragonsHealthNeverDropsBelowOneAndItKeepsWorking() {
        harming();
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(100), 1, Map.of());
        var dragon = carrying(ALICE, END_WELL, 3, Map.of(4, 50L));
        // A dive of 1600 ticks at a quarter of its speed would cost it 64 of its 3 health.
        runFor(2_000);
        var weakest = dragons.get(dragon.id()).orElseThrow();
        assertEquals(Map.of(4, 50L), wells.motes(NETHER_WELL));
        assertEquals(1, weakest.health());
        // Enough to heal it fully at 16 motes a point as it leaves, with 50 over for the Nether.
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 2_000L));
        runFor(20_000);
        assertEquals(Map.of(4, 100L), wells.motes(NETHER_WELL));
    }

    @Test
    void leavingAWellADragonHealsFromItsMotesLowestGradeFirst() {
        harming();
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(2, 300L, 5, 1_000L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        var dragon = hurt(dragons.add(ALICE, END_WELL), 70);
        runFor(1);
        // It loads 50 of grade 5, highest first, and heals only as it leaves.
        var loading = dragons.get(dragon.id()).orElseThrow();
        assertEquals(70, loading.health());
        assertEquals(Map.of(5, 50L), loading.load());
        runFor(STOP);
        // 30 points at 16 motes each, 480: all 300 of grade 2 and 180 of grade 5.
        var leaving = dragons.get(dragon.id()).orElseThrow();
        assertEquals(Optional.of(Trip.Leg.TO_DESTINATION), leaving.trip().map(Trip::leg));
        assertEquals(VoidDragonRecord.FULL_HEALTH, leaving.health());
        assertEquals(Map.of(5, 770L), wells.motes(END_WELL));
    }

    @Test
    void aWellHealsADragonOnlyAsFarAsItsMotesGo() {
        harming();
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(2, 40L, 4, 100L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(50), 1, Map.of());
        var dragon = hurt(dragons.add(ALICE, END_WELL), 50);
        runFor(1 + STOP);
        // It loads 50 of grade 4; the 90 left heal 5 points, 80 of them, grade 2 first.
        var leaving = dragons.get(dragon.id()).orElseThrow();
        assertEquals(55, leaving.health());
        assertEquals(Map.of(4, 50L), leaving.load());
        assertEquals(Map.of(4, 10L), wells.motes(END_WELL));
    }

    @Test
    void anEmptyWellHealsNothing() {
        harming();
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, OTHER_END_WELL, OptionalLong.of(50), 3, Map.of());
        var dragon = carrying(ALICE, END_WELL, 50, Map.of(4, 50L));
        runFor(20_000);
        var rested = dragons.get(dragon.id()).orElseThrow();
        assertEquals(Map.of(4, 50L), wells.motes(OTHER_END_WELL));
        assertEquals(OTHER_END_WELL, rested.rest());
        assertEquals(50, rested.health());
    }

    @Test
    void aDragonNeverCarriesBetweenTwoOfItsOwnersWellsOnOneNetwork() {
        // A reservoir and a Well short of its target, holding the same 300 motes.
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(4, 300L));
        wells.join(ALICE, OTHER_WELL, OptionalLong.of(500), OVERWORLD_WELL);
        var dragon = dragons.add(ALICE, OVERWORLD_WELL);
        runFor(20_000);
        var rested = dragons.get(dragon.id()).orElseThrow();
        assertTrue(rested.trip().isEmpty(), "the dragon took a trip");
        assertEquals(OVERWORLD_WELL, rested.rest());
        assertEquals(Map.of(4, 300L), wells.motes(OTHER_WELL));
    }

    @Test
    void aWellElsewhereFillsANetworkOfTwoWellsUntilTheHigherOfTheirTargets() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 1_000L));
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.of(100), 2, Map.of());
        wells.join(ALICE, OTHER_WELL, OptionalLong.of(150), OVERWORLD_WELL);
        dragons.add(ALICE, END_WELL);
        var second = dragons.add(ALICE, END_WELL);
        runFor(1);
        // The network is 150 short, whichever Well each dragon unloads at: a load and half another.
        assertEquals(50, dragons.get(second.id()).orElseThrow().carried());
        runFor(20_000);
        assertEquals(Map.of(4, 150L), wells.motes(OVERWORLD_WELL));
        assertEquals(Map.of(4, 850L), wells.motes(END_WELL));
    }

    @Test
    void aNetworkOfTwoWellsGivesOnlyItsExcessOverTheHigherOfTheirTargets() {
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.of(100), 2, Map.of(4, 500L));
        wells.join(ALICE, OTHER_WELL, OptionalLong.of(300), OVERWORLD_WELL);
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(1_000), 1, Map.of());
        dragons.add(ALICE, NETHER_WELL);
        runFor(20_000);
        assertEquals(Map.of(4, 300L), wells.motes(OVERWORLD_WELL));
        assertEquals(Map.of(4, 200L), wells.motes(NETHER_WELL));
    }

    @Test
    void aDragonJudgesANetworkByItsOwnersTargetsAloneAndCountsEveryDragonsClaimOnIt() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 1_000L));
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.of(300), 2, Map.of());
        // Bob's Well on Alice's network asks for more, and his dragon brings 250 to it.
        wells.join(BOB, OTHER_WELL, OptionalLong.of(800), OVERWORLD_WELL);
        carrying(BOB, OTHER_END_WELL, VoidDragonRecord.FULL_HEALTH, Map.of(4, 250L));
        var alices = dragons.add(ALICE, END_WELL);
        runFor(1);
        assertEquals(50, dragons.get(alices.id()).orElseThrow().carried());
        runFor(20_000);
        assertEquals(Map.of(4, 300L), wells.motes(OVERWORLD_WELL));
        assertEquals(Map.of(4, 950L), wells.motes(END_WELL));
    }

    @Test
    void aReservoirOnANetworkWithAnotherOwnersTargetedWellGivesAllItHolds() {
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(4, 500L));
        // Bob's Well on Alice's network asks for 800, which never binds Alice's dragons.
        wells.join(BOB, OTHER_WELL, OptionalLong.of(800), OVERWORLD_WELL);
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(1_000), 1, Map.of());
        dragons.add(ALICE, NETHER_WELL);
        runFor(20_000);
        assertEquals(Map.of(), wells.motes(OVERWORLD_WELL));
        assertEquals(Map.of(4, 500L), wells.motes(NETHER_WELL));
    }

    /** Runs the rest of the test with {@link #HARM}. */
    private void harming() {
        routing = new Routing(wells, DIVE, HARM, LOAD, STOP);
    }

    /** @return {@code dragon}, now of {@code health} */
    private VoidDragonRecord hurt(VoidDragonRecord dragon, int health) {
        var hurt = dragon.weakened(dragon.health() - health);
        dragons.update(hurt);
        return hurt;
    }

    /** @return a new dragon of {@code owner}'s of {@code health}, resting at {@code rest} with {@code load} and no trip */
    private VoidDragonRecord carrying(UUID owner, Place rest, int health, Map<Integer, Long> load) {
        var dragon = hurt(dragons.add(owner, rest), health).carrying(new TreeMap<>(load));
        dragons.update(dragon);
        return dragon;
    }

    /** Runs routing a step a tick for {@code ticks} ticks. */
    private void runFor(long ticks) {
        for (long end = now + ticks; now < end; now++) {
            routing.step(dragons, now);
        }
    }

    /** Wells as plain data, each its owner's, whole and awake until broken; Wells on one network share its motes. */
    private static final class FakeWells implements Wells {

        /** One network: the places of its Wells, whoever's, and the motes they share, {@link #CAPACITY} a Well. */
        private record Network(Set<Place> wells, TreeMap<Integer, Long> motes) {

            long capacity() {
                return CAPACITY * wells.size();
            }
        }

        private record Entry(UUID owner, OptionalLong target, int pressure, Network network) {
        }

        private final Map<Place, Entry> wells = new LinkedHashMap<>();

        /** Adds {@code owner}'s Well at {@code place}, on a network of its own holding {@code motes}. */
        void add(UUID owner, Place place, OptionalLong target, int pressure, Map<Integer, Long> motes) {
            wells.put(place, new Entry(owner, target, pressure, new Network(new LinkedHashSet<>(Set.of(place)), new TreeMap<>(motes))));
        }

        /** Adds {@code owner}'s Well at {@code place} to the network of the Well at {@code onNetworkOf}, sharing its motes. */
        void join(UUID owner, Place place, OptionalLong target, Place onNetworkOf) {
            var on = wells.get(onNetworkOf);
            on.network().wells().add(place);
            wells.put(place, new Entry(owner, target, on.pressure(), on.network()));
        }

        void remove(Place place) {
            var removed = wells.remove(place);
            if (removed != null) {
                removed.network().wells().remove(place);
            }
        }

        /** @return what the network of the Well at {@code place} holds, of the grades it holds any of */
        Map<Integer, Long> motes(Place place) {
            var motes = new TreeMap<>(wells.get(place).network().motes());
            motes.values().removeIf(count -> count == 0);
            return motes;
        }

        @Override
        public List<Well> of(UUID owner) {
            var owned = new ArrayList<Well>();
            wells.forEach((place, entry) -> {
                if (entry.owner().equals(owner)) {
                    var network = entry.network();
                    owned.add(new Well(place, entry.target(), entry.pressure(), network.capacity(), network.motes(), Set.copyOf(network.wells())));
                }
            });
            return owned;
        }

        @Override
        public long take(Place place, int grade, long count) {
            if (!wells.containsKey(place)) {
                return 0;
            }
            var motes = wells.get(place).network().motes();
            long taken = Math.min(count, motes.getOrDefault(grade, 0L));
            motes.merge(grade, -taken, Long::sum);
            return taken;
        }

        @Override
        public long put(Place place, int grade, long count) {
            if (!wells.containsKey(place)) {
                return 0;
            }
            var network = wells.get(place).network();
            long total = network.motes().values().stream().mapToLong(Long::longValue).sum();
            long put = Math.min(count, network.capacity() - total);
            network.motes().merge(grade, put, Long::sum);
            return put;
        }
    }
}
