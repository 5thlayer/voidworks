// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What a Void Dragon's presence shows in Jade, read from its record: what it is doing, the Well it is
 * bound for by what it goes by, its load by grade, lowest first and tinted like each grade's butterflies, and its total,
 * and its owner; its health of 100 is Jade's hearts, not a line.
 */
class DragonReportTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Place HERE = new Place(OVERWORLD, new BlockPos(0, 64, 0));
    private static final Place THERE = new Place(OVERWORLD, new BlockPos(640, 64, 0));
    private static final Place CALLED = new Place(OVERWORLD, new BlockPos(0, 64, 640));
    private static final LifeStages STAGES = new LifeStages(1_000, LifeStages.DEFAULTS);
    private static final Component THERE_NAME = Component.literal("Smeltery");
    private static final long MATTER_LOAD = 27;

    @Test
    void restingWithNothingItSaysSoAndItsHealthAndOwner() {
        var report = report(dragon(Optional.empty(), new TreeMap<>(), VoidDragonRecord.FULL_HEALTH), Optional.of("Alex"));

        assertEquals(DragonReport.Doing.RESTING, report.doing());
        assertEquals(Optional.empty(), report.bound());
        var lines = report.lines();
        assertEquals(100, report.health());
        assertEquals(4, lines.size());
        assertLine(lines.get(0), "voidworks.jade.dragon.resting");
        assertLine(lines.get(1), "voidworks.jade.dragon.carrying_nothing");
        assertLine(lines.get(2), "voidworks.jade.dragon.young");
        assertLine(lines.get(3), "voidworks.jade.dragon.owner", "Alex");
    }

    @Test
    void aMatterLineDragonsItemsAreNoLinesButItsStacksOfItsLoadAndTheTollItPaidAre() {
        var cobblestone = Ware.of(Identifier.withDefaultNamespace("cobblestone"), 64, 5 * 64 + 1);
        var box = Ware.of(Identifier.withDefaultNamespace("shulker_box"), 1, 1);
        var adult = new VoidDragonRecord(UUID.randomUUID(), UUID.randomUUID(), Line.MATTER, VoidDragonRecord.FULL_HEALTH, HERE,
                new MatterLoad(List.of(cobblestone, box), 3_072), trip(Trip.Leg.TO_DESTINATION), 0, STAGES.start(LifeStage.ADULT));
        var report = report(adult, Optional.of("Alex"));

        assertEquals(List.of(cobblestone, box), report.items());
        assertEquals(7, report.stacks());
        var lines = report.lines();
        assertEquals(5, lines.size());
        assertLine(lines.get(0), "voidworks.jade.dragon.diving");
        assertLine(lines.get(1), "voidworks.jade.dragon.bound", THERE_NAME);
        assertLine(lines.get(2), "voidworks.jade.dragon.stacks", 7L, 54L, 3_072L);
        assertLine(lines.get(3), "voidworks.jade.dragon.adult");
        assertEquals(report.lines().subList(0, 2), report.head());
    }

    @Test
    void aMatterLineDragonCarryingNothingSaysSo() {
        var dragon = new VoidDragonRecord(UUID.randomUUID(), UUID.randomUUID(), Line.MATTER, VoidDragonRecord.FULL_HEALTH, HERE,
                MatterLoad.NONE, Optional.empty(), 0, 0);
        assertLine(report(dragon, Optional.empty()).lines().get(1), "voidworks.jade.dragon.carrying_nothing");
    }

    @Test
    void itsLoadIsAGradeEachLowestFirstTintedLikeItsButterfliesThenItsTotal() {
        var report = report(dragon(trip(Trip.Leg.LOADING), load(7, 5, 3, 20), 63), Optional.of("Alex"));

        assertEquals(25, report.carried());
        var lines = report.lines();
        assertEquals(63, report.health());
        assertEquals(7, lines.size());
        assertLine(lines.get(0), "voidworks.jade.dragon.loading");
        assertLine(lines.get(1), "voidworks.jade.dragon.bound", THERE_NAME);
        assertLine(lines.get(2), "voidworks.jade.dragon.grade", 3, 20L);
        assertEquals(TextColor.fromRgb(GradeColor.of(3)), lines.get(2).getStyle().getColor());
        assertLine(lines.get(3), "voidworks.jade.dragon.grade", 7, 5L);
        assertEquals(TextColor.fromRgb(GradeColor.of(7)), lines.get(3).getStyle().getColor());
        assertLine(lines.get(4), "voidworks.jade.dragon.carrying", 25L);
        assertLine(lines.get(5), "voidworks.jade.dragon.young");
        assertLine(lines.get(6), "voidworks.jade.dragon.owner", "Alex");
    }

    @Test
    void itShowsItsLifeStage() {
        var dragon = dragon(Optional.empty(), new TreeMap<>(), VoidDragonRecord.FULL_HEALTH);
        var old = new VoidDragonRecord(dragon.id(), dragon.owner(), dragon.line(), dragon.health(), dragon.rest(), dragon.load(), dragon.trip(), 0, 3_500);
        var report = report(old, Optional.empty());

        assertEquals(LifeStage.OLD, report.stage());
        assertLine(report.lines().get(2), "voidworks.jade.dragon.old");
    }

    @Test
    void anOwnerTheServerHasNoNameForIsUnknown() {
        var lines = report(dragon(Optional.empty(), new TreeMap<>(), VoidDragonRecord.FULL_HEALTH), Optional.empty()).lines();

        assertLine(lines.getLast(), "voidworks.jade.dragon.unknown_owner");
    }

    @ParameterizedTest
    @EnumSource(value = Trip.Leg.class, names = {"TO_SOURCE", "LOADING", "TO_DESTINATION", "TO_CALL", "TO_PICKED"})
    void onATripItIsBoundForItsDestinationByName(Trip.Leg leg) {
        assertEquals(Optional.of(THERE_NAME), report(dragon(trip(leg), new TreeMap<>(), VoidDragonRecord.FULL_HEALTH), Optional.empty()).bound());
    }

    @ParameterizedTest
    @EnumSource(value = Trip.Leg.class, names = {"WAITING", "SETTING_DOWN"})
    void waitingForItsOwnerOrSettingItsRiderDownItShowsNoWellItIsBoundFor(Trip.Leg leg) {
        assertEquals(Optional.empty(), report(dragon(trip(leg), new TreeMap<>(), VoidDragonRecord.FULL_HEALTH), Optional.empty()).bound());
    }

    @Test
    void unloadingAtItsDestinationItIsBoundNowhereElse() {
        var report = report(dragon(trip(Trip.Leg.UNLOADING), load(4, 10), VoidDragonRecord.FULL_HEALTH), Optional.empty());

        assertEquals(DragonReport.Doing.UNLOADING, report.doing());
        assertEquals(Optional.empty(), report.bound());
        assertLine(report.lines().getFirst(), "voidworks.jade.dragon.unloading");
    }

    @ParameterizedTest
    @EnumSource(Trip.Leg.class)
    void whatItIsDoingFollowsItsLeg(Trip.Leg leg) {
        var expected = switch (leg) {
            case TO_SOURCE, TO_DESTINATION, TO_CALL, TO_PICKED -> DragonReport.Doing.DIVING;
            case LOADING -> DragonReport.Doing.LOADING;
            case UNLOADING -> DragonReport.Doing.UNLOADING;
            case WAITING -> DragonReport.Doing.WAITING;
            case SETTING_DOWN -> DragonReport.Doing.SETTING_DOWN;
        };
        assertEquals(expected, DragonReport.Doing.of(trip(leg)));
    }

    private static DragonReport report(VoidDragonRecord dragon, Optional<String> owner) {
        return DragonReport.of(dragon, place -> place.equals(THERE) ? THERE_NAME : Component.literal("elsewhere"), owner, STAGES, MATTER_LOAD);
    }

    private static VoidDragonRecord dragon(Optional<Trip> trip, SortedMap<Integer, Long> load, int health) {
        return new VoidDragonRecord(UUID.randomUUID(), UUID.randomUUID(), Line.MOTE, health, HERE, new MoteLoad(load), trip, 0, 0);
    }

    private static Optional<Trip> trip(Trip.Leg leg) {
        var added = leg == Trip.Leg.TO_CALL || leg == Trip.Leg.WAITING || leg == Trip.Leg.TO_PICKED || leg == Trip.Leg.SETTING_DOWN
                ? Optional.of(CALLED)
                : Optional.<Place>empty();
        return Optional.of(new Trip(Optional.of(HERE), THERE, 100, false, leg, 0, 0, 0, added));
    }

    private static SortedMap<Integer, Long> load(int... gradesAndCounts) {
        var load = new TreeMap<Integer, Long>();
        for (int i = 0; i < gradesAndCounts.length; i += 2) {
            load.put(gradesAndCounts[i], (long) gradesAndCounts[i + 1]);
        }
        return load;
    }

    static void assertLine(Component line, String key, Object... args) {
        var contents = (TranslatableContents) line.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(List.of(args), List.of(contents.getArgs()));
    }
}
