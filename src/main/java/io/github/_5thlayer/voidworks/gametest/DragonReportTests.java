// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.DragonReport;
import io.github._5thlayer.voidworks.dragon.DragonlingReport;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.VoidWells;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;

/**
 * What Jade shows of a Void Dragon and a Dragonling, read on a real server as Jade's server half
 * reads it: a dragon's from the record its presence names, following the record as it changes, and
 * a Dragonling's from the creature itself. That Jade draws it is the dev client's to see.
 */
final class DragonReportTests {

    /** Where the dragon rests, away from any Well, and the Dragonling hatches: in the air over the platform. */
    private static final BlockPos HERE = new BlockPos(4, 3, 4);
    /** Where the named Well the dragon is bound for stands, by the record alone: no core is needed for its name. */
    private static final BlockPos THERE = new BlockPos(1, 1, 1);
    /** The grade carried. */
    private static final int GRADE = 4;

    private static final int TICKS = 200;

    private DragonReportTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_void_dragons_presence_shows_what_its_record_holds_its_load_health_owner_and_where_it_is_bound_as_the_record_changes", TICKS,
                DragonReportTests::presenceShowsItsRecord);
        tests.test("a_dragonling_shows_how_far_it_has_grown_and_its_owner", TICKS, DragonReportTests::dragonlingShowsItsGrowth);
    }

    private static void presenceShowsItsRecord(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = known(helper, "dragon-owner");
        var here = Place.of(helper.getLevel(), helper.absolutePos(HERE));
        var there = Place.of(helper.getLevel(), helper.absolutePos(THERE));
        var dragon = new AtomicReference<VoidDragonRecord>();
        var presence = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenExecute(() -> dragon.set(SavedVoidDragons.of(server).add(owner, here)))
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragon.get().id());
                    helper.assertTrue(shown.isPresent(), "the resting dragon shows no presence");
                    presence.set(shown.get());
                })
                .thenExecute(() -> {
                    var report = report(helper, presence.get());
                    helper.assertTrue(report.equals(new DragonReport(DragonReport.Doing.RESTING, Optional.empty(), new TreeMap<>(),
                            VoidDragonRecord.FULL_HEALTH, Optional.of("dragon-owner"))), "the resting dragon shows " + report);
                    helper.assertTrue(DragonReport.of(presence.get(), server).isPresent(), "a presence shows nothing");
                    var load = new TreeMap<Integer, Long>();
                    load.put(GRADE, 30L);
                    var d = dragon.get();
                    SavedVoidDragons.of(server).update(new VoidDragonRecord(d.id(), d.owner(), 80, d.rest(), load, d.trip(), d.sinceEgg()));
                    var changed = report(helper, presence.get());
                    helper.assertTrue(changed.carried() == 30 && changed.load().equals(load) && changed.health() == 80,
                            "loaded and weakened, the dragon shows " + changed);
                    // Bound for a named Well, read before Routing steps again, then put back to rest.
                    SavedVoidWells.of(server).put(new VoidWells.Entry(there, owner, FillTarget.QUARTER, Optional.of(Component.literal("Smeltery"))));
                    var trip = new Trip(Optional.empty(), there, 30, false, Trip.Leg.LOADING, Long.MAX_VALUE, 0, 0);
                    var now = SavedVoidDragons.of(server).get(d.id()).orElseThrow();
                    SavedVoidDragons.of(server).update(new VoidDragonRecord(d.id(), d.owner(), now.health(), d.rest(), now.load(), Optional.of(trip), 0));
                    var bound = report(helper, presence.get());
                    helper.assertTrue(bound.doing() == DragonReport.Doing.LOADING && bound.bound().equals(Optional.of(Component.literal("Smeltery"))),
                            "loading, bound for the named Well, the dragon shows " + bound);
                    SavedVoidDragons.of(server).removeAt(here::equals);
                    SavedVoidWells.of(server).remove(there);
                })
                .thenSucceed();
    }

    private static void dragonlingShowsItsGrowth(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = known(helper, "dragonling-owner");
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
        dragonling.hatchFor(owner);
        helper.assertTrue(DragonReport.of(dragonling, server).isEmpty(), "a Dragonling shows a grown dragon's report");
        var hatched = DragonlingReport.of(dragonling, server);
        helper.assertTrue(hatched.equals(new DragonlingReport(0, true, Optional.of("dragonling-owner"))), "just hatched, the Dragonling shows " + hatched);
        dragonling.setTicksToGrow(VoidworksConfig.dragonlingGrowTicks() / 2);
        var half = DragonlingReport.of(dragonling, server);
        helper.assertTrue(half.growth() == 50, "half grown, the Dragonling shows " + half);
        helper.succeed();
    }

    private static DragonReport report(GameTestHelper helper, VoidDragon presence) {
        return DragonReport.of(presence, helper.getLevel().getServer()).orElseThrow(() -> helper.assertionException(Component.literal("the presence shows no report")));
    }

    /** @return a new player's id, whom the server has seen by {@code name}, as one who once joined it */
    private static UUID known(GameTestHelper helper, String name) {
        var id = UUID.randomUUID();
        helper.getLevel().getServer().services().nameToIdCache().add(new NameAndId(id, name));
        return id;
    }
}
