// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.Handling;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Passing;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Showing;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellHatches;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * A Matter-line dragon seen loading and unloading, on a real server (spec § Moving matter, Seen): at
 * the test's Well, whose chunk is loaded, it takes off from where it rested to load there, holds still
 * over the shaft while the first item of each hatch moves between the hatch and its paws, and hangs its
 * first stack only once that item has reached them; unloading is the mirror. What is seen moving
 * follows the stop's clock in its dragon's record, so a presence shown partway through joins it there.
 * The other end of each trip is a Well in the Nether, in a chunk nothing loads.
 */
final class SeenLoadingTests {

    /** The core, in the middle of its north wall's middle course, its floor on the platform. */
    private static final BlockPos WELL = new BlockPos(4, 3, 2);
    /** A hatch against the Well's east wall, and a second against its west wall. */
    private static final BlockPos HATCH = new BlockPos(7, 3, 4);
    private static final BlockPos WEST_HATCH = new BlockPos(1, 3, 4);
    /** The grade the Toll and the healing are paid in: worth something in the Overworld. */
    private static final int GRADE = 4;
    /** Longer than waking the Well, sitting, a stop and a dive between dimensions take. */
    private static final int TICKS = 1_200;
    /** The heights of each test's Nether Well, so two tests side by side never share one network. */
    private static final int LOADS_Y = 40;
    private static final int UNLOADS_Y = 48;

    private SeenLoadingTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("seen_loading_a_dragon_resting_at_its_source_takes_off_holds_still_and_hangs_its_stack_once_the_first_item_reaches_its_paws",
                TICKS, SeenLoadingTests::loads);
        tests.test("seen_loading_unloading_is_its_mirror_into_the_hatch_and_it_hangs_what_it_carried_until_the_item_has_landed", TICKS,
                SeenLoadingTests::unloads);
    }

    /**
     * Sitting at the test's Well, between two providers, the dragon is sent for what a Nether requester
     * asks: the same presence takes off, and once up holds still over the shaft while an item moves from
     * each provider to its paws, the second after the first; it hangs nothing until the first lands. Taken
     * away while the first is on its way, a presence shown anew sees it on from where it is.
     */
    private static void loads(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = owner(helper);
        PresenceTests.wakeWell(helper, owner, WELL);
        var here = Place.of(helper.getLevel(), helper.absolutePos(WELL));
        var nether = server.getLevel(Level.NETHER);
        var under = PresenceTests.under(helper, nether, LOADS_Y);
        var hatches = SavedWellHatches.of(server);
        var east = hatches.put(Place.of(helper.getLevel(), helper.absolutePos(HATCH)), Optional.of(here), Optional.of(nameOf(owner)),
                WellHatches.Mode.PROVIDER, 27);
        var west = hatches.put(Place.of(helper.getLevel(), helper.absolutePos(WEST_HATCH)), Optional.of(here), Optional.of(nameOf(owner)),
                WellHatches.Mode.PROVIDER, 27);
        MatterRoutingTests.fill(east, new ItemStack(Items.COBBLESTONE, 64), 2);
        MatterRoutingTests.fill(west, new ItemStack(Items.DIRT, 64), 1);
        var shown = new Shown();
        var requesters = new ArrayList<WellHatches.Hatch>();
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        var shaft = WellStructure.shaft(helper.absolutePos(WELL), Direction.NORTH);
        var over = Vec3.atBottomCenterOf(shaft.above(DragonPresences.OVER_SHAFT));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(PresenceTests.stored(helper, WELL).isPresent(), "the Well has not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, 1_000);
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), Line.MATTER, here).id());
                })
                .thenWaitUntil(() -> {
                    var sitting = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(sitting.isPresent() && sitting.get().perch() == VoidDragon.Perch.SITTING, "the dragon is not seen sitting at the Well");
                    presence.set(sitting.get());
                })
                .thenExecute(() -> {
                    netherWell(helper, owner, under, 0);
                    // Short of more cobblestone than dirt, it loads the cobblestone first.
                    var cobblestone = hatches.put(Place.of(nether, under.pos().east(3)), Optional.of(under), Optional.of(nameOf(owner)),
                            WellHatches.Mode.REQUESTER, 27);
                    cobblestone.ask(Items.COBBLESTONE);
                    cobblestone.ask(Items.COBBLESTONE);
                    var dirt = hatches.put(Place.of(nether, under.pos().west(3)), Optional.of(under), Optional.of(nameOf(owner)),
                            WellHatches.Mode.REQUESTER, 27);
                    dirt.ask(Items.DIRT);
                    requesters.addAll(List.of(cobblestone, dirt));
                    shown.listen(dragonId.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.LOADING)), "the dragon is not loading"))
                .thenWaitUntil(() -> helper.assertTrue(presence.get().perch() != VoidDragon.Perch.SITTING, "the dragon loading has not taken off"))
                .thenExecute(() -> {
                    helper.assertTrue(DragonPresences.presence(server, dragonId.get()).equals(Optional.of(presence.get())),
                            "the dragon loading where it rested shows another presence than the one that sat there");
                    helper.assertTrue(presence.get().hung().isEmpty(), "the dragon hangs " + presence.get().hung() + " before anything reached its paws");
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.passings.size() == 1, "nothing is seen moving"))
                .thenExecute(() -> {
                    var first = shown.passings.getFirst();
                    helper.assertTrue(first.passing().hatch().equals(east.place()) && first.passing().ware().item().equals(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE))
                            && first.loading() && first.elapsed() == 0, "the first seen moving is " + first);
                    long into = into(helper, dragonId.get());
                    helper.assertTrue(into == Showing.EMERGE_TICKS, "the first item started " + into + " ticks into the stop, not once the dragon was up");
                    helper.assertTrue(presence.get().hung().isEmpty(), "the dragon hangs " + presence.get().hung() + " as the first item sets off");
                })
                .thenWaitUntil(() -> helper.assertTrue(into(helper, dragonId.get()) >= Showing.EMERGE_TICKS + 5, "the stop has not gone on"))
                .thenExecute(() -> {
                    // Taken away while the first item is on its way, a presence shown anew sees it on from there.
                    presence.get().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    DragonPresences.show(server);
                    presence.set(DragonPresences.presence(server, dragonId.get()).orElseThrow());
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.passings.size() == 2, "the presence shown anew sees nothing moving"))
                .thenExecute(() -> {
                    var joined = shown.passings.get(1);
                    helper.assertTrue(joined.dragon() == presence.get() && joined.passing().hatch().equals(east.place()) && joined.elapsed() > 0
                            && joined.elapsed() == joined.into() - Showing.EMERGE_TICKS, "the presence shown anew sees " + joined);
                })
                .thenWaitUntil(() -> helper.assertTrue(!presence.get().hung().isEmpty(), "the dragon hangs nothing"))
                .thenExecute(() -> {
                    long into = into(helper, dragonId.get());
                    helper.assertTrue(into >= Showing.EMERGE_TICKS + Handling.PASS_TICKS && into <= Showing.EMERGE_TICKS + Handling.PASS_TICKS + 1,
                            "the dragon hung its stack " + into + " ticks into the stop, not as the first item reached its paws");
                    helper.assertTrue(presence.get().hung().is(Items.COBBLESTONE), "the dragon hangs " + presence.get().hung());
                    helper.assertTrue(presence.get().position().distanceTo(over) < 0.5, "the dragon is at " + presence.get().position() + ", not still over the shaft at " + over);
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.passings.size() == 3, "the second hatch's item is not seen moving"))
                .thenExecute(() -> {
                    var second = shown.passings.get(2);
                    helper.assertTrue(second.passing().hatch().equals(west.place()) && second.passing().ware().item().equals(BuiltInRegistries.ITEM.getKey(Items.DIRT))
                            && second.elapsed() == 0, "the second seen moving is " + second);
                    helper.assertTrue(presence.get().position().distanceTo(over) < 0.5, "the dragon is at " + presence.get().position() + " while the second item moves");
                    shown.stop();
                    SavedVoidDragons.of(server).remove(dragonId.get());
                    List.of(east, west).forEach(hatch -> hatches.remove(hatch.place()));
                    requesters.forEach(hatch -> hatches.remove(hatch.place()));
                    clearAway(helper, under);
                    helper.setBlock(WELL, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * Resting at a Nether provider, the dragon brings cobblestone to a requester on the test's Well: it
     * emerges hanging it, the lone item moves from its paws into the requester to land half a second
     * before the stop ends, and it hangs its stack until the item leaves its paws, then nothing.
     */
    private static void unloads(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = owner(helper);
        PresenceTests.wakeWell(helper, owner, WELL);
        var here = Place.of(helper.getLevel(), helper.absolutePos(WELL));
        var nether = server.getLevel(Level.NETHER);
        var under = PresenceTests.under(helper, nether, UNLOADS_Y);
        var hatches = SavedWellHatches.of(server);
        var shown = new Shown();
        var dragonId = new AtomicReference<UUID>();
        var requester = new AtomicReference<WellHatches.Hatch>();
        var provider = new AtomicReference<WellHatches.Hatch>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(PresenceTests.stored(helper, WELL).isPresent(), "the Well has not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, 1_000);
                    // Its network pays the Toll for lifting the stack to the Overworld.
                    netherWell(helper, owner, under, 1_000);
                    provider.set(hatches.put(Place.of(nether, under.pos().east(3)), Optional.of(under), Optional.of(nameOf(owner)),
                            WellHatches.Mode.PROVIDER, 27));
                    MatterRoutingTests.fill(provider.get(), new ItemStack(Items.COBBLESTONE, 64), 1);
                    requester.set(hatches.put(Place.of(helper.getLevel(), helper.absolutePos(HATCH)), Optional.of(here), Optional.of(nameOf(owner)),
                            WellHatches.Mode.REQUESTER, 27));
                    requester.get().ask(Items.COBBLESTONE);
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), Line.MATTER, under).id());
                    shown.listen(dragonId.get());
                })
                .thenWaitUntil(() -> {
                    var presence = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(presence.isPresent() && presence.get().hung().is(Items.COBBLESTONE), "the dragon unloading is not seen hanging what it carried");
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.passings.size() == 1, "nothing is seen moving"))
                .thenExecute(() -> {
                    var put = shown.passings.getFirst();
                    helper.assertTrue(put.passing().hatch().equals(requester.get().place()) && !put.loading() && put.elapsed() == 0,
                            "the item seen moving is " + put);
                    helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.UNLOADING)), "the item moved while the dragon is " + leg(helper, dragonId.get()));
                    helper.assertTrue(put.into() == loneStart(), "the lone item started " + put.into() + " ticks into the stop, not to land half a second before it ends");
                    var presence = DragonPresences.presence(server, dragonId.get()).orElseThrow();
                    helper.assertTrue(presence.hung().isEmpty(), "the dragon hangs " + presence.hung() + " as the item leaves its paws");
                })
                .thenWaitUntil(() -> {
                    var presence = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(presence.isPresent(), "the dragon unloading shows no presence, at " + into(helper, dragonId.get()) + " ticks into its stop: "
                            + PresenceTests.record(helper, dragonId.get()));
                    helper.assertTrue(presence.get().hung().isEmpty(), "the dragon still hangs its stack");
                })
                .thenExecute(() -> {
                    long into = into(helper, dragonId.get());
                    helper.assertTrue(into >= loneStart() && into <= loneStart() + 1,
                            "the dragon let its stack go " + into + " ticks into the stop, not as the item left its paws");
                    shown.stop();
                    SavedVoidDragons.of(server).remove(dragonId.get());
                    List.of(requester.get(), provider.get()).forEach(hatch -> hatches.remove(hatch.place()));
                    clearAway(helper, under);
                    helper.setBlock(WELL, Blocks.AIR);
                })
                .thenSucceed();
    }

    /** What each presence is seen to move, as the hook main sends it through hears it, sent on as ever. */
    private static final class Shown {

        final List<Seen> passings = new ArrayList<>();
        private VoidDragon.Passer sending;

        /** Hears what the presences of the dragon {@code id} are seen to move. */
        void listen(UUID id) {
            sending = VoidDragon.passer();
            VoidDragon.onPassing((dragon, passing, loading, elapsed) -> {
                sending.show(dragon, passing, loading, elapsed);
                if (!dragon.dragonId().equals(Optional.of(id))) {
                    return;
                }
                var server = dragon.level().getServer();
                var record = SavedVoidDragons.of(server).get(dragon.dragonId().orElseThrow()).orElseThrow();
                long into = server.overworld().getGameTime() - record.trip().orElseThrow().began(VoidworksConfig.dragonStopTicks());
                passings.add(new Seen(dragon, passing, loading, elapsed, into));
            });
        }

        void stop() {
            VoidDragon.onPassing(sending);
        }
    }

    /** One item seen moving, {@code into} ticks into its dragon's stop. */
    private record Seen(VoidDragon dragon, Passing passing, boolean loading, int elapsed, long into) {
    }

    /** Makes {@code under}, in a chunk nothing loads, {@code owner}'s Well, with no target, holding {@code motes} of {@link #GRADE}. */
    private static void netherWell(GameTestHelper helper, ServerPlayer owner, Place under, long motes) {
        var nether = under.level(helper.getLevel().getServer());
        RoutingTests.assertUnloaded(helper, nether, under.pos());
        VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), motes == 0 ? Map.of() : Map.of(GRADE, motes));
        SavedVoidWells.of(helper.getLevel().getServer()).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.NONE));
    }

    private static void clearAway(GameTestHelper helper, Place under) {
        VoidstoneNetworks.of(under.level(helper.getLevel().getServer())).leave(under.pos());
        SavedVoidWells.of(helper.getLevel().getServer()).remove(under);
    }

    /** @return how many ticks into its stop a lone item starts to move: to land half a second before the stop ends */
    private static long loneStart() {
        return VoidworksConfig.dragonStopTicks() - 10 - Handling.PASS_TICKS;
    }

    private static Optional<Trip.Leg> leg(GameTestHelper helper, UUID id) {
        return PresenceTests.record(helper, id).trip().map(Trip::leg);
    }

    /** @return how many ticks into its stop the dragon {@code id} is */
    private static long into(GameTestHelper helper, UUID id) {
        var trip = PresenceTests.record(helper, id).trip().orElseThrow();
        return helper.getLevel().getServer().overworld().getGameTime() - trip.began(VoidworksConfig.dragonStopTicks());
    }

    private static NameAndId nameOf(ServerPlayer owner) {
        return new NameAndId(owner.getUUID(), owner.getGameProfile().name());
    }

    /** The Wells' and the dragon's owner, on the platform north of the Well, never in the level. */
    private static ServerPlayer owner(GameTestHelper helper) {
        var owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "loading-owner"));
        var feet = helper.absoluteVec(new Vec3(4.5, 1, 0.5));
        owner.setPos(feet.x, feet.y, feet.z);
        return owner;
    }
}
