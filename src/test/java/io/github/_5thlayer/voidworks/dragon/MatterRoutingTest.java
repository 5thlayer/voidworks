// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * A Matter-line dragon's routing over Well hatches, on its record alone: it carries items from its
 * owner's providers to their requesters, between Wells in any dimension, the item most short first, up
 * to its load in stacks; its trips claim items by kind, so dragons sharing a request never overshoot it;
 * what does not fit on arrival goes to another requester asking for it, else to providers with room,
 * else it holds it and rests. Lines don't mix: a Matter-line dragon loads no motes, and a Mote-line one
 * touches no hatch.
 */
class MatterRoutingTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");
    private static final Identifier END = Identifier.withDefaultNamespace("the_end");

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier DIRT = Identifier.withDefaultNamespace("dirt");
    private static final Identifier PEARL = Identifier.withDefaultNamespace("ender_pearl");
    private static final Identifier SHULKER_BOX = Identifier.withDefaultNamespace("shulker_box");

    private static final long CAPACITY = 1_000;
    private static final long MOTE_LOAD = 100;
    /** A young Matter-line dragon's load, in stacks. */
    private static final long LOAD = 27;
    private static final int STOP = 100;
    private static final long EGG = 1_000_000;
    private static final LifeStages AGELESS = LifeStages.of(1_000_000_000L, List.of(1.0, 1.0, 1.0, 1.0), List.of(1.0, 1.0, 1.0, 1.0));
    private static final Life AGELESS_LIFE = new Life(AGELESS, EGG);
    private static final Dive DIVE = new Dive(200, 64, dimension -> dimension.equals(NETHER) ? 8.0 : 1.0);
    private static final Harm HARMLESS = new Harm(dimension -> 0, 50, 16);
    /** Long enough for any trip here to end. */
    private static final int A_WHILE = 20_000;

    private static final Place END_WELL = new Place(END, new BlockPos(0, 64, 0));
    private static final Place NETHER_WELL = new Place(NETHER, new BlockPos(80, 64, 0));
    private static final Place OVERWORLD_WELL = new Place(OVERWORLD, new BlockPos(0, 64, 640));

    private static final Place END_PROVIDER = hatchOf(END_WELL, 0);
    private static final Place END_REQUESTER = hatchOf(END_WELL, 1);
    private static final Place NETHER_REQUESTER = hatchOf(NETHER_WELL, 0);
    private static final Place NETHER_OTHER_REQUESTER = hatchOf(NETHER_WELL, 1);
    private static final Place OVERWORLD_PROVIDER = hatchOf(OVERWORLD_WELL, 0);
    private static final Place OVERWORLD_REQUESTER = hatchOf(OVERWORLD_WELL, 1);

    private final FakeWells wells = new FakeWells(CAPACITY);
    private final FakeHatches hatches = new FakeHatches();
    private final FakeOwners owners = new FakeOwners();
    private final VoidDragons dragons = new VoidDragons();
    /** Free, so routing alone is tested, but where a test prices it. */
    private Toll toll = Toll.FREE;
    private Routing routing = routing(LOAD, AGELESS_LIFE);
    private long now;

    @Test
    void aRequesterOnOneWellIsFilledFromAProviderOnAnotherInAnotherDimension() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(A_WHILE);
        assertEquals(3 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
        assertEquals(2 * 64, hatches.holds(END_PROVIDER, COBBLESTONE));
    }

    @Test
    void itLoadsUpToItsLoadInStacksAndClaimsWhatItLoads() {
        routing = routing(2, AGELESS_LIFE);
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        var loaded = dragons.get(dragon.id()).orElseThrow();
        assertEquals(new MatterLoad(List.of(stacks(COBBLESTONE, 2))), loaded.load());
        assertEquals(2, loaded.trip().orElseThrow().claimed());
        assertEquals(Map.of(COBBLESTONE, 128L), loaded.trip().orElseThrow().items());
    }

    @Test
    void twoDragonsServingOneRequestNeverOvershootIt() {
        routing = routing(2, AGELESS_LIFE);
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 10));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        var first = dragons.add(ALICE, Line.MATTER, END_WELL);
        var second = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        assertEquals(2, dragons.get(first.id()).orElseThrow().carried());
        assertEquals(1, dragons.get(second.id()).orElseThrow().carried());
        runFor(A_WHILE);
        assertEquals(3 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
        assertEquals(7 * 64, hatches.holds(END_PROVIDER, COBBLESTONE));
    }

    @Test
    void claimsCountByItemSoASecondDragonCarriesWhatTheFirstLeftShort() {
        routing = routing(2, AGELESS_LIFE);
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 5), stacks(DIRT, 5));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        hatches.requester(NETHER_OTHER_REQUESTER, NETHER_WELL, DIRT, 64, 1);
        var first = dragons.add(ALICE, Line.MATTER, END_WELL);
        var second = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        // The item most short first: the first takes two stacks of cobblestone, the second the third and the dirt.
        assertEquals(Map.of(COBBLESTONE, 128L), dragons.get(first.id()).orElseThrow().load().items());
        assertEquals(Map.of(COBBLESTONE, 64L, DIRT, 64L), dragons.get(second.id()).orElseThrow().load().items());
        runFor(A_WHILE);
        assertEquals(3 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
        assertEquals(64, hatches.holds(NETHER_OTHER_REQUESTER, DIRT));
    }

    @Test
    void aRequesterIsShortOnlyOfWhatItDoesNotHold() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3, Ware.of(COBBLESTONE, 64, 100));
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        assertEquals(Map.of(COBBLESTONE, 92L), dragons.get(dragon.id()).orElseThrow().load().items());
        runFor(A_WHILE);
        assertEquals(3 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
    }

    @Test
    void itLoadsFromTheStopWhoseProvidersHoldMostOfWhatIsShort() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 1));
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL, stacks(COBBLESTONE, 3));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        assertEquals(Optional.of(OVERWORLD_WELL), dragons.get(dragon.id()).orElseThrow().trip().orElseThrow().source());
    }

    @Test
    void aProviderNeverAsksAndARequesterNeverGives() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.requester(END_REQUESTER, END_WELL, DIRT, 64, 1, stacks(COBBLESTONE, 5));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(A_WHILE);
        assertEquals(0, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
        assertTrue(dragons.get(dragon.id()).orElseThrow().trip().isEmpty(), "the dragon took a trip");
    }

    @Test
    void aLoadCountsInStacksByItsStage() {
        routing = routing(LOAD, new Life(new LifeStages(1_000, LifeStages.DEFAULTS), EGG));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        var pearls = Ware.of(PEARL, 16, 27 * 16);
        hatches.provider(END_PROVIDER, END_WELL, pearls);
        hatches.provider(hatchOf(END_WELL, 2), END_WELL, pearls);
        hatches.provider(hatchOf(END_WELL, 3), END_WELL, pearls);
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, PEARL, 16, 27);
        hatches.requester(NETHER_OTHER_REQUESTER, NETHER_WELL, PEARL, 16, 27);
        hatches.requester(hatchOf(NETHER_WELL, 2), NETHER_WELL, PEARL, 16, 27);
        var young = dragons.add(ALICE, Line.MATTER, END_WELL);
        var adult = dragons.add(ALICE, Line.MATTER, END_WELL).agedTo(1_000);
        dragons.update(adult);
        runFor(1);
        // Stacks of ender pearls, sixteen to a stack: young 27, adult twice that.
        assertEquals(27, dragons.get(young.id()).orElseThrow().carried());
        assertEquals(27 * 16, dragons.get(young.id()).orElseThrow().load().items().get(PEARL));
        assertEquals(54, dragons.get(adult.id()).orElseThrow().carried());
    }

    @Test
    void aLoadCountsEachItemsPartStackAsOneAndAFilledContainerAsOne() {
        var box = new Ware(SHULKER_BOX, Optional.of(contents("cobblestone")), 1, 2);
        var otherBox = new Ware(SHULKER_BOX, Optional.of(contents("dirt")), 1, 1);
        var load = new MatterLoad(List.of(Ware.of(COBBLESTONE, 64, 65), Ware.of(PEARL, 16, 3), box, otherBox, Ware.of(COBBLESTONE, 64, 63)));
        // 128 cobblestone in two stacks, 3 pearls in one, and three shulker boxes, one stack each.
        assertEquals(List.of(Ware.of(COBBLESTONE, 64, 128), Ware.of(PEARL, 16, 3), box, otherBox), load.wares());
        assertEquals(6, load.amount());
    }

    @Test
    void aShulkerBoxTakesOneStackOfALoad() {
        routing = routing(2, AGELESS_LIFE);
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        var filled = new Ware(SHULKER_BOX, Optional.of(contents("diamond_block")), 1, 1);
        hatches.provider(END_PROVIDER, END_WELL, filled, filled, filled);
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, SHULKER_BOX, 1, 27);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        assertEquals(new MatterLoad(List.of(filled.withCount(2))), dragons.get(dragon.id()).orElseThrow().load());
        runFor(A_WHILE);
        assertEquals(3, hatches.holds(NETHER_REQUESTER, SHULKER_BOX));
    }

    @Test
    void whatDoesNotFitOnArrivalGoesToAnotherRequesterAskingForItThenToProvidersWithRoom() {
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of());
        // The Nether requester lacks a stack of the three it asks for.
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3, stacks(COBBLESTONE, 2));
        hatches.requester(OVERWORLD_REQUESTER, OVERWORLD_WELL, COBBLESTONE, 64, 1);
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL);
        var dragon = dragons.add(ALICE, Line.MATTER, NETHER_WELL);
        var load = new MatterLoad(List.of(stacks(COBBLESTONE, 4)));
        // Arriving at the Nether Well with four stacks.
        dragons.update(dragon.carrying(load).on(Optional.of(Trip.planned(Optional.empty(), NETHER_WELL, 4, false, Trip.Leg.TO_DESTINATION)
                .claiming(load))));
        runFor(A_WHILE);
        assertEquals(3 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
        assertEquals(64, hatches.holds(OVERWORLD_REQUESTER, COBBLESTONE));
        assertEquals(2 * 64, hatches.holds(OVERWORLD_PROVIDER, COBBLESTONE));
        var after = dragons.get(dragon.id()).orElseThrow();
        assertTrue(after.load().isEmpty(), "the dragon still carries " + after.load());
    }

    @Test
    void withRoomNowhereADragonHoldsItsLoadAndRestsUntilThereIs() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(DIRT, FakeHatches.SLOTS));
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        var holding = dragon.carrying(new MatterLoad(List.of(stacks(COBBLESTONE, 2))));
        dragons.update(holding);
        runFor(A_WHILE);
        var after = dragons.get(dragon.id()).orElseThrow();
        assertEquals(holding.load(), after.load());
        assertTrue(after.trip().isEmpty(), "the dragon took a trip with nowhere to put its load");
        hatches.provider(hatchOf(NETHER_WELL, 0), NETHER_WELL);
        runFor(A_WHILE);
        assertTrue(dragons.get(dragon.id()).orElseThrow().load().isEmpty(), "the dragon kept its load though there is room");
        assertEquals(2 * 64, hatches.holds(hatchOf(NETHER_WELL, 0), COBBLESTONE));
    }

    @Test
    void aRequesterAskingForSomethingElseMakesNoRoomForALoadNoOneWants() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(DIRT, FakeHatches.SLOTS));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, DIRT, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        var holding = dragon.carrying(new MatterLoad(List.of(stacks(COBBLESTONE, 2))));
        dragons.update(holding);
        for (int tick = 0; tick < A_WHILE; tick += 100) {
            runFor(100);
            var after = dragons.get(dragon.id()).orElseThrow();
            assertEquals(holding.load(), after.load());
            assertTrue(after.trip().isEmpty(), "the dragon took a trip with nowhere to put its load");
        }
    }

    @Test
    void aDragonArrivingFirstPutsNoMoreThanWhatIsLeftOfTheRequestAfterAnothersClaim() {
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of());
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL);
        // Another dragon, far off, has claimed two of the three stacks.
        var other = dragons.add(ALICE, Line.MATTER, END_WELL);
        var otherLoad = new MatterLoad(List.of(stacks(COBBLESTONE, 2)));
        dragons.update(other.carrying(otherLoad).on(Optional.of(new Trip(Optional.empty(), NETHER_WELL, 2, false, Trip.Leg.TO_DESTINATION,
                Long.MAX_VALUE, 0, 0, Optional.empty(), otherLoad.items()))));
        var first = dragons.add(ALICE, Line.MATTER, NETHER_WELL);
        var load = new MatterLoad(List.of(stacks(COBBLESTONE, 3)));
        dragons.update(first.carrying(load).on(Optional.of(Trip.planned(Optional.empty(), NETHER_WELL, 1, false, Trip.Leg.TO_DESTINATION)
                .claiming(1, Map.of(COBBLESTONE, 64L)))));
        runFor(1);
        assertEquals(64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
    }

    @Test
    void aMatterLineDragonLoadsNoMotesAndAMoteLineOneTouchesNoHatch() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 500L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(250), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        var mote = dragons.add(ALICE, END_WELL);
        var matter = dragons.add(ALICE, Line.MATTER, END_WELL);
        for (int tick = 0; tick < A_WHILE; tick++) {
            runFor(1);
            assertTrue(dragons.get(mote.id()).orElseThrow().load().items().isEmpty(), "the Mote-line dragon loaded items");
            assertTrue(!(dragons.get(matter.id()).orElseThrow().load() instanceof MoteLoad motes) || motes.isEmpty(),
                    "the Matter-line dragon loaded motes");
        }
        // Each carried its own: the motes to the Well short of its target, the items to the requester.
        assertEquals(Map.of(4, 250L), wells.motes(NETHER_WELL));
        assertEquals(3 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
        assertEquals(2 * 64, hatches.holds(END_PROVIDER, COBBLESTONE));
    }

    @Test
    void aMoteLineDragonAloneLeavesEveryHatchAsItIs() {
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 500L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.of(250), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        dragons.add(ALICE, END_WELL);
        runFor(A_WHILE);
        assertEquals(5 * 64, hatches.holds(END_PROVIDER, COBBLESTONE));
        assertEquals(0, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
    }

    @Test
    void aLoadPaysItsTollAsItLoadsFromTheDepartureNetworkByTheSpendRulePricedToItsDestination() {
        priced();
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(2, 100L, 3, 10_000L));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(END_REQUESTER, END_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, OVERWORLD_WELL);
        runFor(1);
        var loaded = (MatterLoad) dragons.get(dragon.id()).orElseThrow().load();
        // Three stacks lifted a step, 512 base void energy each, paid in grade-3 motes worth one each in the Overworld.
        assertEquals(List.of(stacks(COBBLESTONE, 3)), loaded.wares());
        assertEquals(3 * 512, loaded.toll());
        assertEquals(Map.of(2, 100L, 3, 10_000L - 3 * 512), wells.motes(OVERWORLD_WELL));
    }

    @Test
    void aNetworkThatCanPayForPartOfALoadLoadsOnlyThat() {
        priced();
        // Worth 640 base void energy: a stack and a quarter lifted a step.
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(3, 640L));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(END_REQUESTER, END_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, OVERWORLD_WELL);
        runFor(1);
        var loading = dragons.get(dragon.id()).orElseThrow();
        assertEquals(Map.of(COBBLESTONE, 80L), loading.load().items());
        assertEquals(Map.of(COBBLESTONE, 80L), loading.trip().orElseThrow().items());
        assertEquals(Map.of(), wells.motes(OVERWORLD_WELL));
    }

    @Test
    void aStopThatCanPayForNoneIsNeverLoadedFrom() {
        priced();
        // The Overworld holds most, but its grade-2 motes are worth nothing there; the Nether can pay for a stack.
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(2, 100_000L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of(2, 1_024L));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL, stacks(COBBLESTONE, 5));
        hatches.provider(hatchOf(NETHER_WELL, 2), NETHER_WELL, stacks(COBBLESTONE, 1));
        hatches.requester(END_REQUESTER, END_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        assertEquals(Optional.of(NETHER_WELL), dragons.get(dragon.id()).orElseThrow().trip().orElseThrow().source());
        runFor(A_WHILE);
        // The Nether paid two steps for its stack, and the Overworld never could.
        assertEquals(64, hatches.holds(END_REQUESTER, COBBLESTONE));
        assertEquals(5 * 64, hatches.holds(OVERWORLD_PROVIDER, COBBLESTONE));
        assertEquals(Map.of(), wells.motes(NETHER_WELL));
        assertTrue(dragons.get(dragon.id()).orElseThrow().trip().isEmpty(), "the dragon took a trip no network can pay for");
    }

    @Test
    void theTollIsTakenFirstAndTheDragonHealsFromWhatIsLeft() {
        priced();
        // The Toll for three stacks, and 5 health's worth over.
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(3, 3 * 512L + 5 * 16));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL, stacks(COBBLESTONE, 5));
        hatches.requester(END_REQUESTER, END_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, OVERWORLD_WELL).weakened(50);
        dragons.update(dragon);
        runFor(STOP + 1);
        var diving = dragons.get(dragon.id()).orElseThrow();
        assertEquals(Trip.Leg.TO_DESTINATION, diving.trip().orElseThrow().leg());
        assertEquals(3, diving.carried());
        assertEquals(VoidDragonRecord.FULL_HEALTH - 50 + 5, diving.health());
        assertEquals(Map.of(), wells.motes(OVERWORLD_WELL));
    }

    @Test
    void aLoadSentOnPaysNothingMoreAndGetsNothingBack() {
        priced();
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of(2, 10_000L));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 10_000L));
        hatches.requester(END_REQUESTER, END_WELL, COBBLESTONE, 64, 3);
        // At the Nether Well, whose requesters ask for nothing it carries, with a load that paid its Toll.
        var dragon = dragons.add(ALICE, Line.MATTER, NETHER_WELL);
        var load = new MatterLoad(List.of(stacks(COBBLESTONE, 2)), 2 * 1_024);
        dragons.update(dragon.carrying(load));
        runFor(A_WHILE);
        assertEquals(2 * 64, hatches.holds(END_REQUESTER, COBBLESTONE));
        assertEquals(Map.of(2, 10_000L), wells.motes(NETHER_WELL));
        assertEquals(Map.of(4, 10_000L), wells.motes(END_WELL));
    }

    @Test
    void aShulkerBoxPaysForWhatItHoldsPlusOne() {
        priced();
        var filled = new Ware(SHULKER_BOX, Optional.of(contents("diamond_block")), 1, 1, new TreeMap<>(Map.of(64, 27 * 64L)));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of(4, 100_000L));
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, filled);
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, SHULKER_BOX, 1, 1);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        runFor(1);
        assertEquals(28 * 256, ((MatterLoad) dragons.get(dragon.id()).orElseThrow().load()).toll());
        assertEquals(Map.of(4, 100_000L - 28 * 256), wells.motes(END_WELL));
    }

    @Test
    void aProviderShowsHowManyOfItsStacksItsNetworkCanPayTheTollForToTheCheapestStopWantingThem() {
        priced();
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(3, 1_024L));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL, stacks(COBBLESTONE, 9));
        // Wanted only in the End, a step up: 1,024 pays for two stacks.
        hatches.requester(END_REQUESTER, END_WELL, COBBLESTONE, 64, 9);
        assertEquals(Optional.of(new Payable(9, 2)), routing.payable(ALICE, OVERWORLD_WELL, OVERWORLD_PROVIDER));
        // Wanted in the Nether too, downhill, so four.
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 1);
        assertEquals(Optional.of(new Payable(9, 4)), routing.payable(ALICE, OVERWORLD_WELL, OVERWORLD_PROVIDER));
        assertEquals(Optional.empty(), routing.payable(ALICE, END_WELL, END_REQUESTER));
    }

    @Test
    void aProviderWhoseNetworkCanPayForNoneShowsNone() {
        priced();
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of(2, 100_000L));
        hatches.provider(OVERWORLD_PROVIDER, OVERWORLD_WELL, stacks(COBBLESTONE, 9));
        assertEquals(Optional.of(new Payable(9, 0)), routing.payable(ALICE, OVERWORLD_WELL, OVERWORLD_PROVIDER));
    }

    /** Prices the Toll as the shipped data does. */
    private void priced() {
        toll = new Toll(256);
        routing = routing(LOAD, AGELESS_LIFE);
    }

    @Test
    void aMatterLineDragonAgesByTheStacksItUnloadsAtItsOwnLinesStageLength() {
        // Stages of 54 stacks for the Matter line, while a Mote-line stage lasts far longer.
        routing = routing(LOAD, new Life(new LifeStages(54, LifeStages.DEFAULTS), EGG));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        for (int index = 0; index < 3; index++) {
            hatches.provider(hatchOf(END_WELL, index), END_WELL, stacks(COBBLESTONE, 27));
            hatches.requester(hatchOf(NETHER_WELL, index), NETHER_WELL, COBBLESTONE, 64, 27);
        }
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL).agedTo(40);
        dragons.update(dragon);
        // Young, it loads 27 stacks; unloading them, 67 in its life, it is an adult, and loads twice that.
        assertEquals(27, nextLoad(dragon));
        assertEquals(67, dragons.get(dragon.id()).orElseThrow().cargo());
        assertEquals(54, nextLoad(dragon));
        assertEquals(121, dragons.get(dragon.id()).orElseThrow().cargo());
    }

    @Test
    void anAdultMatterLineDragonLaysAMatterDragonEggEachEggOfStacksOwingThemAtAnUnloadedWell() {
        routing = routing(LOAD, new Life(new LifeStages(1_000, LifeStages.DEFAULTS), 27));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        wells.add(ALICE, OVERWORLD_WELL, OptionalLong.empty(), 2, Map.of());
        wells.unseen.add(NETHER_WELL);
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 27));
        hatches.provider(hatchOf(END_WELL, 2), END_WELL, stacks(COBBLESTONE, 27));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 27);
        hatches.requester(NETHER_OTHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 27);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL).agedTo(1_000);
        dragons.update(dragon);
        runFor(A_WHILE);
        // An adult's 54 stacks unloaded at a Well whose chunk is not loaded: two eggs owed, none laid.
        assertEquals(2 * 27 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE) + hatches.holds(NETHER_OTHER_REQUESTER, COBBLESTONE));
        assertEquals(List.of(), wells.laid);
        assertEquals(54, dragons.get(dragon.id()).orElseThrow().towardEgg());
        // Its next stop is at the End's, loaded, where it lays them both, Matter Dragon Eggs.
        hatches.requester(OVERWORLD_REQUESTER, OVERWORLD_WELL, COBBLESTONE, 64, 1);
        hatches.provider(hatchOf(END_WELL, 3), END_WELL, stacks(COBBLESTONE, 1));
        runFor(A_WHILE);
        assertEquals(List.of(END_WELL, END_WELL), wells.laid);
        assertEquals(List.of(Line.MATTER, Line.MATTER), wells.laidOf);
        assertEquals(1, dragons.get(dragon.id()).orElseThrow().towardEgg());
    }

    @Test
    void aMatterLineDragonCountsTowardItsEggsOnlyTheStacksItUnloadsAsAnAdult() {
        routing = routing(LOAD, new Life(new LifeStages(54, LifeStages.DEFAULTS), 27));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.provider(END_PROVIDER, END_WELL, stacks(COBBLESTONE, 27));
        hatches.provider(hatchOf(END_WELL, 2), END_WELL, stacks(COBBLESTONE, 27));
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 27);
        hatches.requester(NETHER_OTHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 27);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL);
        dragons.update(new VoidDragonRecord(dragon.id(), ALICE, Line.MATTER, dragon.health(), dragon.rest(), dragon.load(), dragon.trip(), 20, 90));
        runFor(A_WHILE);
        // Of its 54 stacks, the 18 unloaded before it turned senior at 108 count: 38, an egg and 11 over.
        assertEquals(144, dragons.get(dragon.id()).orElseThrow().cargo());
        assertEquals(List.of(NETHER_WELL), wells.laid);
        assertEquals(11, dragons.get(dragon.id()).orElseThrow().towardEgg());
        // Senior, nothing more it unloads counts toward an egg.
        hatches.provider(hatchOf(END_WELL, 3), END_WELL, stacks(COBBLESTONE, 27));
        hatches.requester(hatchOf(NETHER_WELL, 2), NETHER_WELL, COBBLESTONE, 64, 27);
        runFor(A_WHILE);
        assertEquals(171, dragons.get(dragon.id()).orElseThrow().cargo());
        assertEquals(List.of(NETHER_WELL), wells.laid);
        assertEquals(11, dragons.get(dragon.id()).orElseThrow().towardEgg());
    }

    @Test
    void aMatterLineDragonThatHasOutlivedItsStagesDiesOnlyAtAStopWithNothingAboard() {
        routing = routing(LOAD, new Life(new LifeStages(54, LifeStages.DEFAULTS), EGG));
        wells.add(ALICE, END_WELL, OptionalLong.empty(), 3, Map.of());
        wells.add(ALICE, NETHER_WELL, OptionalLong.empty(), 1, Map.of());
        hatches.requester(NETHER_REQUESTER, NETHER_WELL, COBBLESTONE, 64, 3);
        var dragon = dragons.add(ALICE, Line.MATTER, END_WELL).agedTo(4 * 54).carrying(new MatterLoad(List.of(stacks(COBBLESTONE, 5))));
        dragons.update(dragon);
        runFor(A_WHILE);
        // It unloads the 3 stacks asked for and, with nowhere to put the other 2, holds them, alive.
        assertEquals(3 * 64, hatches.holds(NETHER_REQUESTER, COBBLESTONE));
        assertEquals(2, dragons.get(dragon.id()).orElseThrow().carried());
        hatches.provider(END_PROVIDER, END_WELL);
        runFor(A_WHILE);
        // Stowed at the End's provider, it carries nothing, and dies there.
        assertEquals(2 * 64, hatches.holds(END_PROVIDER, COBBLESTONE));
        assertEquals(Optional.empty(), dragons.get(dragon.id()));
    }

    /** @return how many stacks {@code dragon} carries as it next loads, run on until it has unloaded them all */
    private long nextLoad(VoidDragonRecord dragon) {
        while (dragons.get(dragon.id()).orElseThrow().carried() == 0) {
            runFor(1);
        }
        long loaded = dragons.get(dragon.id()).orElseThrow().carried();
        while (dragons.get(dragon.id()).orElseThrow().carried() > 0) {
            runFor(1);
        }
        return loaded;
    }

    private Routing routing(long load, Life matter) {
        return new Routing(wells, hatches, owners, DIVE, HARMLESS, MOTE_LOAD, load, toll, STOP, AGELESS_LIFE, matter);
    }

    /** @return {@code stacks} full stacks of {@code item}, 64 to a stack */
    private static Ware stacks(Identifier item, int stacks) {
        return Ware.of(item, 64, 64L * stacks);
    }

    /** @return a hatch's place against the Well at {@code well}, the {@code index}th */
    private static Place hatchOf(Place well, int index) {
        return new Place(well.dimension(), well.pos().offset(3, index, 0));
    }

    /** @return components saying what a container holds, as a stand-in for a filled shulker box's */
    private static CompoundTag contents(String held) {
        var components = new CompoundTag();
        components.putString("container", held);
        return components;
    }

    private void runFor(long ticks) {
        for (long end = now + ticks; now < end; now++) {
            routing.step(dragons, now);
        }
    }
}
