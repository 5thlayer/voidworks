// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Load;
import io.github._5thlayer.voidworks.dragon.MatterLoad;
import io.github._5thlayer.voidworks.dragon.MoteLoad;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Showing;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.dragon.VoidDragons;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A Mote-line dragon shows its Line by trailing sparks in the colours of the grades it carries, the
 * butterflies' colours, whatever it is doing, and none while it carries nothing; which grades they are is in its presence's
 * synced data, which reaches every client, and their counts are not. A Matter-line dragon and a
 * Dragonling trail none.
 */
final class SparkTests {

    /** Where the dragon rests, on no Well, and the Dragonling hatches. */
    private static final BlockPos HERE = new BlockPos(4, 2, 4);

    private SparkTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("sparks_a_mote_line_dragon_carrying_grades_3_and_4_trails_both_colours_resting_working_and_diving_and_an_empty_or_matter_line_one_none", 1, SparkTests::trailsItsGrades);
        tests.test("sparks_a_mote_line_dragonling_trails_none", 1, SparkTests::dragonlingTrailsNone);
    }

    private static void trailsItsGrades(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dragons = SavedVoidDragons.of(server);
        var here = Place.of(helper.getLevel(), helper.absolutePos(HERE));
        var dragon = dragons.add(UUID.randomUUID(), Line.MOTE, here);
        var id = dragon.id();

        DragonPresences.show(server);
        var presence = DragonPresences.presence(server, id).orElseThrow();
        assertTrails(helper, presence, Showing.RESTING, List.of(), "carrying nothing");

        var load = MoteLoad.of(Map.of(3, 1_000L, 4, 7L));
        var both = List.of(GradeColor.of(3), GradeColor.of(4));
        carrying(dragons, dragon, Line.MOTE, load, Optional.empty());
        DragonPresences.show(server);
        assertTrails(helper, presence, Showing.RESTING, both, "resting");
        int loaded = presence.sparks().bits();
        helper.assertTrue(synced(presence).contains(loaded), "the grades it trails are not in its synced data");

        long now = server.overworld().getGameTime();
        int stop = VoidworksConfig.dragonStopTicks();
        carrying(dragons, dragon, Line.MOTE, load, Optional.of(trip(here, load, Trip.Leg.LOADING, now + stop - Showing.EMERGE_TICKS - 1)));
        DragonPresences.show(server);
        assertTrails(helper, presence, Showing.WORKING, both, "working");
        carrying(dragons, dragon, Line.MOTE, load, Optional.of(trip(here, load, Trip.Leg.TO_DESTINATION, now + 1_000)));
        DragonPresences.show(server);
        assertTrails(helper, presence, Showing.DIVING, both, "diving");
        carrying(dragons, dragon, Line.MOTE, load, Optional.empty());

        // A presence shown anew trails its load at once.
        presence.discard();
        DragonPresences.show(server);
        DragonPresences.show(server);
        var anew = DragonPresences.presence(server, id).orElseThrow();
        helper.assertTrue(anew != presence, "the presence taken away is still shown");
        assertTrails(helper, anew, Showing.RESTING, both, "shown anew");

        carrying(dragons, dragon, Line.MOTE, MoteLoad.NONE, Optional.empty());
        DragonPresences.show(server);
        assertTrails(helper, anew, Showing.RESTING, List.of(), "having unloaded");
        helper.assertTrue(!synced(anew).contains(loaded), "the empty dragon's synced data still holds the grades it carried");

        carrying(dragons, dragon, Line.MATTER, MatterLoad.NONE, Optional.empty());
        DragonPresences.show(server);
        assertTrails(helper, anew, Showing.RESTING, List.of(), "of the Matter line");
        dragons.remove(id);
        DragonPresences.show(server);
        helper.succeed();
    }

    private static void dragonlingTrailsNone(GameTestHelper helper) {
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
        dragonling.hatchFor(UUID.randomUUID(), Line.MOTE);
        helper.assertTrue(dragonling.sparks().isEmpty(), "a Mote-line Dragonling trails grades " + dragonling.sparks().grades());
        dragonling.discard();
        helper.succeed();
    }

    private static void carrying(VoidDragons dragons, VoidDragonRecord dragon, Line line, Load load, Optional<Trip> trip) {
        dragons.update(new VoidDragonRecord(dragon.id(), dragon.owner(), line, dragon.health(), dragon.rest(), load, trip, 0, 0));
    }

    private static Trip trip(Place here, MoteLoad load, Trip.Leg leg, long until) {
        return new Trip(Optional.empty(), here, load.amount(), false, leg, until, 0, 0, Optional.empty(), load.items());
    }

    /** The presence shows {@code showing} and trails sparks of {@code colours}. */
    private static void assertTrails(GameTestHelper helper, VoidDragon presence, Showing showing, List<Integer> colours, String doing) {
        helper.assertTrue(presence.showing() == showing, "the presence " + doing + " shows " + presence.showing() + ", not " + showing);
        var sparks = presence.sparks();
        helper.assertTrue(sparks.colours().equals(colours), "the presence " + doing + " trails " + sparks.colours() + ", not " + colours);
    }

    /** @return the whole numbers in {@code dragon}'s synced data that differ from their defaults, as a client joining sees them */
    private static List<Integer> synced(VoidDragon dragon) {
        var values = dragon.getEntityData().getNonDefaultValues();
        return values == null ? List.of()
                : values.stream().map(value -> value.value()).filter(Integer.class::isInstance).map(Integer.class::cast).toList();
    }
}
