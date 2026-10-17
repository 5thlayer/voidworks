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
import io.github._5thlayer.voidworks.dragon.MatterLoad;
import io.github._5thlayer.voidworks.dragon.MoteLoad;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Showing;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A Mote-line dragon shows its Line by trailing butterflies of the grades it carries, in their
 * grades' colours, whatever it is doing, and none while it carries nothing; which grades they are is in its presence's
 * synced data, which reaches every client, and their counts are not. A Matter-line dragon and a
 * Dragonling trail none.
 */
final class TrailTests {

    /** Where the dragon rests, on no Well, and the Dragonling hatches. */
    private static final BlockPos HERE = new BlockPos(4, 2, 4);

    private TrailTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("trail_a_mote_line_dragon_carrying_grades_3_and_4_trails_both_colours_resting_working_and_diving_and_an_empty_or_matter_line_one_none", 1, TrailTests::trailsItsGrades);
        tests.test("trail_a_mote_line_dragonling_trails_none", 1, TrailTests::dragonlingTrailsNone);
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
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.empty());
        DragonPresences.show(server);
        assertTrails(helper, presence, Showing.RESTING, both, "resting");

        long now = server.overworld().getGameTime();
        int stop = VoidworksConfig.dragonStopTicks();
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.of(DragonLoads.trip(here, load, Trip.Leg.LOADING, now + stop - Showing.EMERGE_TICKS - 1)));
        DragonPresences.show(server);
        assertTrails(helper, presence, Showing.WORKING, both, "working");
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.of(DragonLoads.trip(here, load, Trip.Leg.TO_DESTINATION, now + 1_000)));
        DragonPresences.show(server);
        assertTrails(helper, presence, Showing.DIVING, both, "diving");
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.empty());

        // A presence shown anew trails its load at once.
        presence.discard();
        DragonPresences.show(server);
        DragonPresences.show(server);
        var anew = DragonPresences.presence(server, id).orElseThrow();
        helper.assertTrue(anew != presence, "the presence taken away is still shown");
        assertTrails(helper, anew, Showing.RESTING, both, "shown anew");

        DragonLoads.carrying(dragons, dragon, Line.MOTE, MoteLoad.NONE, Optional.empty());
        DragonPresences.show(server);
        assertTrails(helper, anew, Showing.RESTING, List.of(), "having unloaded");

        DragonLoads.carrying(dragons, dragon, Line.MATTER, MatterLoad.NONE, Optional.empty());
        DragonPresences.show(server);
        assertTrails(helper, anew, Showing.RESTING, List.of(), "of the Matter line");
        dragons.remove(id);
        DragonPresences.show(server);
        helper.succeed();
    }

    private static void dragonlingTrailsNone(GameTestHelper helper) {
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
        dragonling.hatchFor(UUID.randomUUID(), Line.MOTE);
        helper.assertTrue(dragonling.trail().isEmpty(), "a Mote-line Dragonling trails grades " + dragonling.trail().grades());
        dragonling.discard();
        helper.succeed();
    }

    /** The presence shows {@code showing} and trails butterflies of {@code colours}, and a client given its synced data sees them so. */
    private static void assertTrails(GameTestHelper helper, VoidDragon presence, Showing showing, List<Integer> colours, String doing) {
        helper.assertTrue(presence.showing() == showing, "the presence " + doing + " shows " + presence.showing() + ", not " + showing);
        var trail = presence.trail();
        helper.assertTrue(trail.colours().equals(colours), "the presence " + doing + " trails " + trail.colours() + ", not " + colours);
        var seen = DragonClients.seen(helper, presence).trail();
        helper.assertTrue(seen.colours().equals(colours), "a client sees the presence " + doing + " trail " + seen.colours() + ", not " + colours);
    }

}
