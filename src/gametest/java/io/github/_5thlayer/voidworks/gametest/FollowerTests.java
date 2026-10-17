// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
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
 * A Mote-line dragon shows its Line by the butterflies following it, of the grades it carries, in
 * their grades' colours, whatever it is doing, and none while it carries nothing; which grades they
 * are is in its presence's synced data, so a client sees them too, and their counts are not. A
 * Matter-line dragon and a Dragonling have none.
 */
final class FollowerTests {

    /** Where the dragon rests, on no Well, and the Dragonling hatches. */
    private static final BlockPos HERE = new BlockPos(4, 2, 4);

    private FollowerTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("followers_a_mote_line_dragon_carrying_grades_3_and_4_syncs_both_to_clients_resting_working_and_diving_and_an_empty_or_matter_line_one_none", 1,
                FollowerTests::followedByItsGrades);
        tests.test("followers_a_mote_line_dragonling_has_none", 1, FollowerTests::dragonlingHasNone);
    }

    private static void followedByItsGrades(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dragons = SavedVoidDragons.of(server);
        var here = Place.of(helper.getLevel(), helper.absolutePos(HERE));
        var dragon = dragons.add(UUID.randomUUID(), Line.MOTE, here);
        var id = dragon.id();

        DragonPresences.show(server);
        var presence = DragonPresences.presence(server, id).orElseThrow();
        assertFollowed(helper, presence, Showing.RESTING, Set.of(), "carrying nothing");

        var load = MoteLoad.of(Map.of(3, 1_000L, 4, 7L));
        var both = Set.of(3, 4);
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.empty());
        DragonPresences.show(server);
        assertFollowed(helper, presence, Showing.RESTING, both, "resting");

        long now = server.overworld().getGameTime();
        int stop = VoidworksConfig.dragonStopTicks();
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.of(DragonLoads.trip(here, load, Trip.Leg.LOADING, now + stop - Showing.EMERGE_TICKS - 1)));
        DragonPresences.show(server);
        assertFollowed(helper, presence, Showing.WORKING, both, "working");
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.of(DragonLoads.trip(here, load, Trip.Leg.TO_DESTINATION, now + 1_000)));
        DragonPresences.show(server);
        assertFollowed(helper, presence, Showing.DIVING, both, "diving");
        DragonLoads.carrying(dragons, dragon, Line.MOTE, load, Optional.empty());

        // A presence shown anew is followed at once.
        presence.discard();
        DragonPresences.show(server);
        DragonPresences.show(server);
        var anew = DragonPresences.presence(server, id).orElseThrow();
        helper.assertTrue(anew != presence, "the presence taken away is still shown");
        assertFollowed(helper, anew, Showing.RESTING, both, "shown anew");

        DragonLoads.carrying(dragons, dragon, Line.MOTE, MoteLoad.NONE, Optional.empty());
        DragonPresences.show(server);
        assertFollowed(helper, anew, Showing.RESTING, Set.of(), "having unloaded");

        DragonLoads.carrying(dragons, dragon, Line.MATTER, MatterLoad.NONE, Optional.empty());
        DragonPresences.show(server);
        assertFollowed(helper, anew, Showing.RESTING, Set.of(), "of the Matter line");
        dragons.remove(id);
        DragonPresences.show(server);
        helper.succeed();
    }

    private static void dragonlingHasNone(GameTestHelper helper) {
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
        dragonling.hatchFor(UUID.randomUUID(), Line.MOTE);
        helper.assertTrue(dragonling.followers().isEmpty(), "a Mote-line Dragonling is followed by grades " + dragonling.followers().grades());
        dragonling.discard();
        helper.succeed();
    }

    /** The presence shows {@code showing} and is followed by butterflies of {@code grades}, and a client given its synced data sees them so. */
    private static void assertFollowed(GameTestHelper helper, VoidDragon presence, Showing showing, Set<Integer> grades, String doing) {
        helper.assertTrue(presence.showing() == showing, "the presence " + doing + " shows " + presence.showing() + ", not " + showing);
        var followers = presence.followers().grades();
        helper.assertTrue(followers.equals(grades), "the presence " + doing + " is followed by grades " + followers + ", not " + grades);
        var seen = DragonClients.seen(helper, presence).followers().grades();
        helper.assertTrue(seen.equals(grades), "a client sees the presence " + doing + " followed by grades " + seen + ", not " + grades);
    }

}
