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
import io.github._5thlayer.voidworks.dragon.DragonRiding;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Showing;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.network.MoteStore;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellStructure;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/**
 * Riding and calling a Void Dragon, with a player in the level: its owner mounts one of theirs
 * at a Well and no one else can, it waits there for them, strafing picks another of their Wells and
 * jumping dives there, holding them unseen and unhurt through the dive until it sets them down on that
 * Well's rim; the Well they pick is a stop added to the dragon's trip; and standing in a Well's shaft
 * calls a dragon there, which waits while they stay. The owner's other Well stands high over the
 * test's, in its column, clear of every other test, so a dive between them is the shortest a dive
 * takes. Which trips a dragon makes is {@code RoutingTest}'s.
 */
final class RideTests {

    /** The core of the test's Well, in the middle of its north wall's middle course, its floor on the platform. */
    private static final BlockPos WELL = new BlockPos(4, 3, 2);
    /** The core of the owner's other Well, high over the test's. */
    private static final BlockPos HIGH_WELL = WELL.above(24);
    /** What the reservoir holds. */
    private static final int HELD = 1_000;
    /** How many motes the high Well is short of its target. */
    private static final int SHORT = 100;
    /** The heights of the tests' Nether Wells, so two tests side by side never put theirs on one network. */
    private static final int ADDED_STOP_Y = 48;
    private static final int CALLS_Y = 80;
    private static final String HIGH_NAME = "Eyrie";

    private static final Input RIGHT = new Input(false, false, false, true, false, false, false);
    private static final Input LEFT = new Input(false, false, true, false, false, false, false);
    private static final Input JUMP = new Input(false, false, false, false, true, false, false);

    private static final int TICKS = 1_200;

    private RideTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        // A player in the level is seen by every mob near the test, as a Dragonling sees one holding a Siphon.
        tests.testAlone("its_owner_mounts_a_void_dragon_stopped_at_their_well_where_it_waits_for_them_and_sneaking_sets_them_down_on_its_rim",
                TICKS, RideTests::mountsAndGetsOff);
        tests.testAlone("a_rider_strafes_to_pick_a_well_and_jumps_and_the_dragon_holds_them_through_the_dive_and_sets_them_down_on_its_rim",
                TICKS, RideTests::picksAndDives);
        tests.testAlone("the_well_a_rider_picks_is_a_stop_added_to_the_trip_which_gets_what_it_is_short_of_and_the_rest_goes_on",
                TICKS, RideTests::addedStop);
        tests.testAlone("its_owner_standing_in_a_wells_shaft_calls_a_void_dragon_there_which_waits_while_they_stay", TICKS, RideTests::calls);
        tests.testAlone("a_rider_leaving_the_game_is_set_down_first_on_the_rim_of_the_well_the_dragon_is_at_or_diving_to", TICKS, RideTests::leaves);
    }

    /**
     * Loading at the test's Well for the high one, the dragon is mounted by its owner, not by anyone
     * else, and waits there past its stop while they ride; sneaking, they are set down on the Well's rim,
     * and it goes on.
     */
    private static void mountsAndGetsOff(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        PresenceTests.wakeWell(helper, owner, HIGH_WELL);
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL) && awake(helper, HIGH_WELL), "the Wells have not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, HELD);
                    shortOfItsTarget(helper, owner, HIGH_WELL, SHORT);
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> presence.set(atWork(helper, dragonId.get())))
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.LOADING)), "the dragon is not loading at the Well");
                    var stranger = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "stranger"));
                    presence.get().interact(stranger, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(!stranger.isPassenger(), "another player mounted the owner's dragon");
                    presence.get().interact(owner, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(owner.getVehicle() == presence.get(), "its owner did not mount the dragon");
                    helper.assertTrue(owner.overlays.contains(Component.translatable("voidworks.dragon.ride.mounted")),
                            "its owner was told " + owner.overlays + ", not how to ride");
                })
                .thenIdle(VoidworksConfig.dragonStopTicks() + 40)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.LOADING)),
                            "the ridden dragon left its stop: it is " + leg(helper, dragonId.get()));
                    owner.setShiftKeyDown(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(!owner.isPassenger(), "sneaking did not get its rider off"))
                .thenExecute(() -> {
                    assertOnTheRim(helper, owner, WELL);
                    owner.setShiftKeyDown(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(!leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.LOADING)),
                        "the dragon its rider got off still waits at its stop"))
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
    }

    /**
     * Resting at the test's Well, the dragon is mounted; strafing picks the high Well, by its name,
     * either way round, and jumping dives there. Through the dive its rider cannot get off or be hurt
     * and sees only the void; then the dragon emerging at the high Well brings them up out of its shaft
     * on its back, seeing again, and sets them down on its rim.
     */
    private static void picksAndDives(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        PresenceTests.wakeWell(helper, owner, HIGH_WELL);
        var high = place(helper, HIGH_WELL);
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL) && awake(helper, HIGH_WELL), "the Wells have not woken"))
                .thenExecute(() -> {
                    var tag = new ItemStack(Items.NAME_TAG);
                    tag.set(DataComponents.CUSTOM_NAME, Component.literal(HIGH_NAME));
                    owner.setItemInHand(InteractionHand.MAIN_HAND, tag);
                    helper.useBlock(HIGH_WELL, owner);
                    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(shown.isPresent() && shown.get().showing() == Showing.RESTING, "the dragon is not seen resting at the Well");
                    presence.set(shown.get());
                })
                .thenExecute(() -> {
                    presence.get().interact(owner, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(owner.getVehicle() == presence.get(), "its owner did not mount the resting dragon");
                    owner.setLastClientInput(RIGHT);
                })
                .thenExecuteAfter(1, () -> {
                    var picked = Component.translatable("voidworks.dragon.ride.picked", Component.literal(HIGH_NAME));
                    helper.assertTrue(owner.overlays.getLast().equals(picked), "strafing right showed " + owner.overlays.getLast() + ", not the high Well by its name");
                    owner.setLastClientInput(Input.EMPTY);
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(LEFT))
                .thenExecuteAfter(1, () -> {
                    // The high Well is the rider's only other: strafing either way comes back to it.
                    var picked = Component.translatable("voidworks.dragon.ride.picked", Component.literal(HIGH_NAME));
                    helper.assertTrue(owner.overlays.getLast().equals(picked), "strafing left showed " + owner.overlays.getLast());
                    owner.setLastClientInput(Input.EMPTY);
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(JUMP))
                .thenExecuteAfter(1, () -> {
                    owner.setLastClientInput(Input.EMPTY);
                    var dived = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow().trip();
                    helper.assertTrue(dived.map(Trip::leg).equals(Optional.of(Trip.Leg.TO_PICKED)) && dived.get().added().equals(Optional.of(high)),
                            "jumping did not dive to the high Well: the dragon's trip is " + dived);
                    helper.assertTrue(presence.get().carrying(), "the diving dragon does not carry its rider");
                    owner.setShiftKeyDown(true);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(owner.getVehicle() == presence.get(), "the rider got off in the dive");
                    helper.assertTrue(owner.hasEffect(MobEffects.INVISIBILITY), "the rider is seen in the dive");
                    helper.assertTrue(DragonRiding.inTheDive(owner), "the rider is not in the dive");
                    float health = owner.getHealth();
                    owner.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0F);
                    helper.assertTrue(owner.getHealth() == health, "the rider was hurt in the dive");
                    owner.setShiftKeyDown(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(owner.getVehicle() instanceof VoidDragon emerging && emerging != presence.get(),
                        "the rider is not brought up out of the high Well's shaft"))
                .thenExecute(() -> {
                    var emerging = (VoidDragon) owner.getVehicle();
                    helper.assertTrue(emerging.showing() == Showing.EMERGING && !DragonRiding.inTheDive(owner),
                            "the rider is brought up on a dragon that is " + emerging.showing());
                    helper.assertTrue(!owner.hasEffect(MobEffects.INVISIBILITY), "the rider coming up out of the dive is still unseen");
                    helper.assertTrue(presence.get().isRemoved(), "the presence that carried the rider is still here");
                    owner.setShiftKeyDown(true);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(owner.isPassenger(), "the rider got off the dragon coming up out of the shaft");
                    owner.setShiftKeyDown(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(!owner.isPassenger(), "the rider has not been set down"))
                .thenExecute(() -> {
                    assertOnTheRim(helper, owner, HIGH_WELL);
                    var after = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(after.rest().equals(high), "the dragon stopped at " + after.rest() + ", not the high Well");
                })
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(shown.isPresent() && shown.get().blockPosition().closerThan(helper.absolutePos(HIGH_WELL), VoidDragon.RESTING_RADIUS + 8),
                            "the dragon is not seen at the high Well");
                })
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
    }

    /**
     * The dragon loads at the test's Well for a Well in the Nether, more short than the high one; its
     * rider picks the high Well, which it stops at to set them down and fill, and the rest of its load
     * goes on to the Nether.
     */
    private static void addedStop(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        PresenceTests.wakeWell(helper, owner, WELL);
        PresenceTests.wakeWell(helper, owner, HIGH_WELL);
        var under = PresenceTests.under(helper, nether, ADDED_STOP_Y);
        long target = FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow();
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL) && awake(helper, HIGH_WELL), "the Wells have not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, HELD);
                    shortOfItsTarget(helper, owner, HIGH_WELL, SHORT);
                    RoutingTests.assertUnloaded(helper, nether, under.pos());
                    VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), Map.of(PresenceTests.GRADE, target - 2 * SHORT));
                    SavedVoidWells.of(server).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.QUARTER));
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> presence.set(atWork(helper, dragonId.get())))
                .thenExecute(() -> {
                    var loading = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(loading.trip().map(Trip::destination).equals(Optional.of(under)) && loading.carried() == 2 * SHORT,
                            "the dragon is not loading what the Nether Well is short of: " + loading);
                    presence.get().interact(owner, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(owner.getVehicle() == presence.get(), "its owner did not mount the loading dragon");
                    owner.setLastClientInput(RIGHT);
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(Input.EMPTY))
                .thenExecuteAfter(1, () -> {
                    // Picking runs through the owner's Wells in the order they were listed: the high Well before the Nether's.
                    var picked = Component.translatable("voidworks.dragon.ride.picked", DragonRiding.label(server, place(helper, HIGH_WELL)));
                    helper.assertTrue(owner.overlays.getLast().equals(picked), "strafing showed " + owner.overlays.getLast() + ", not the high Well");
                    owner.setLastClientInput(JUMP);
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(Input.EMPTY))
                .thenWaitUntil(() -> helper.assertTrue(!owner.isPassenger(), "the rider has not been set down"))
                .thenExecute(() -> {
                    assertOnTheRim(helper, owner, HIGH_WELL);
                    var setDown = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(total(helper, HIGH_WELL) == target, "the high Well holds " + total(helper, HIGH_WELL) + ", not its target " + target);
                    helper.assertTrue(setDown.load().equals(Map.of(PresenceTests.GRADE, (long) SHORT)), "the dragon carries " + setDown.load() + " on");
                })
                .thenWaitUntil(() -> {
                    var on = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(on.rest().equals(under) && on.trip().map(Trip::leg).equals(Optional.of(Trip.Leg.UNLOADING)),
                            "the dragon has not unloaded the rest in the Nether");
                })
                .thenExecute(() -> {
                    long there = RoutingTests.total(nether, under.pos());
                    helper.assertTrue(there == target - SHORT, "the Nether Well holds " + there + ", not " + (target - SHORT));
                    clearAway(helper, owner, Set.of(under));
                })
                .thenSucceed();
    }

    /**
     * The dragon rests at a Well of its owner's in the Nether, in a chunk nothing loads; its owner
     * stands in the test's Well's shaft, and it comes there and waits while they stay, being told it is
     * coming; once they step out, its stop ends, and it rests there.
     */
    private static void calls(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        PresenceTests.wakeWell(helper, owner, WELL);
        var under = PresenceTests.under(helper, nether, CALLS_Y);
        var here = place(helper, WELL);
        var dragonId = new AtomicReference<UUID>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> {
                    RoutingTests.assertUnloaded(helper, nether, under.pos());
                    VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), Map.of());
                    SavedVoidWells.of(server).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.NONE));
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), under).id());
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).isEmpty(), "the dragon with nothing to do is " + leg(helper, dragonId.get()));
                    var floor = Vec3.atBottomCenterOf(WellStructure.shaft(helper.absolutePos(WELL), Direction.NORTH).below());
                    owner.setPos(floor.x, floor.y, floor.z);
                })
                .thenWaitUntil(() -> helper.assertTrue(owner.overlays.contains(Component.translatable("voidworks.dragon.coming")),
                        "its owner was not told a dragon is coming"))
                .thenWaitUntil(() -> {
                    var called = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(called.rest().equals(here) && leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.WAITING)),
                            "the dragon has not come to the Well its owner calls it to");
                })
                .thenIdle(VoidworksConfig.dragonStopTicks() + 40)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.WAITING)),
                            "the dragon left while its owner stays in the shaft: it is " + leg(helper, dragonId.get()));
                    helper.assertTrue(DragonPresences.presence(server, dragonId.get()).isPresent(), "the dragon waiting at the Well is not seen");
                    var outside = helper.absoluteVec(new Vec3(4.5, 1, 0.5));
                    owner.setPos(outside.x, outside.y, outside.z);
                })
                .thenWaitUntil(() -> helper.assertTrue(leg(helper, dragonId.get()).isEmpty(), "the dragon still waits once its owner has left the shaft"))
                .thenExecute(() -> {
                    var rested = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(rested.rest().equals(here), "the dragon rests at " + rested.rest() + ", not the Well it was called to");
                    clearAway(helper, owner, Set.of(under));
                })
                .thenSucceed();
    }

    /**
     * Its rider leaves the game while the dragon hovers at the test's Well, and is set down on its rim,
     * the dragon still there; mounted again, they leave in the dive to the high Well, and are set down
     * on its rim, seen again, with the presence that carried them gone. Either way, no presence
     * is left for the server to save with them.
     */
    private static void leaves(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        PresenceTests.wakeWell(helper, owner, HIGH_WELL);
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL) && awake(helper, HIGH_WELL), "the Wells have not woken"))
                .thenExecute(() -> dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id()))
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(shown.isPresent() && shown.get().showing() == Showing.RESTING, "the dragon is not seen resting at the Well");
                    presence.set(shown.get());
                })
                .thenExecute(() -> {
                    presence.get().interact(owner, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(owner.getVehicle() == presence.get(), "its owner did not mount the dragon");
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(owner));
                    helper.assertTrue(!owner.isPassenger(), "the rider leaving the game still rides");
                    assertOnTheRim(helper, owner, WELL);
                    helper.assertTrue(!presence.get().isRemoved(), "the presence its rider left is gone");
                })
                // A player who got off waits a moment to mount again, as from any vehicle.
                .thenIdle(61)
                .thenExecute(() -> {
                    presence.get().interact(owner, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(owner.getVehicle() == presence.get(), "its owner did not mount the dragon again");
                    owner.setLastClientInput(RIGHT);
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(Input.EMPTY))
                .thenExecuteAfter(1, () -> owner.setLastClientInput(JUMP))
                .thenExecuteAfter(1, () -> owner.setLastClientInput(Input.EMPTY))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(DragonRiding.inTheDive(owner), "the rider is not in the dive");
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(owner));
                    helper.assertTrue(!owner.isPassenger(), "the rider leaving the game in the dive still rides");
                    assertOnTheRim(helper, owner, HIGH_WELL);
                    helper.assertTrue(!owner.hasEffect(MobEffects.INVISIBILITY), "the rider who left in the dive is still unseen");
                    helper.assertTrue(presence.get().isRemoved(), "the presence that carried the rider is still here");
                    clearAway(helper, owner, Set.of());
                })
                .thenSucceed();
    }

    /** Sets the target of the Well at {@code core} to a quarter, by its owner's sneak-use, and fills it to {@code shortBy} short of it. */
    private static void shortOfItsTarget(GameTestHelper helper, Rider owner, BlockPos core, int shortBy) {
        owner.setShiftKeyDown(true);
        helper.useBlock(core, owner);
        owner.setShiftKeyDown(false);
        var well = helper.getBlockEntity(core, VoidWellBlockEntity.class);
        helper.assertTrue(well.target() == FillTarget.QUARTER, "the Well's target is " + well.target());
        long target = FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow();
        PresenceTests.deposit(helper, core, Math.toIntExact(target - shortBy));
    }

    /** @return the presence of the dragon {@code id}, once it is seen at work at the Well it stopped at */
    private static VoidDragon atWork(GameTestHelper helper, UUID id) {
        var shown = DragonPresences.presence(helper.getLevel().getServer(), id);
        helper.assertTrue(shown.isPresent() && shown.get().showing() == Showing.WORKING, "the dragon is not seen at work at the Well");
        return shown.get();
    }

    /** A rider set down at the Well at {@code core} stands on its rim over the core. */
    private static void assertOnTheRim(GameTestHelper helper, ServerPlayer rider, BlockPos core) {
        var rim = Vec3.atBottomCenterOf(helper.absolutePos(core).above(3));
        helper.assertTrue(rider.position().distanceTo(rim) < 0.01 && rider.level() == helper.getLevel(),
                "the rider was set down at " + rider.position() + ", not on the rim at " + rim);
    }

    private static boolean awake(GameTestHelper helper, BlockPos core) {
        return PresenceTests.stored(helper, core).isPresent();
    }

    private static long total(GameTestHelper helper, BlockPos core) {
        return PresenceTests.stored(helper, core).map(MoteStore::total).orElse(0L);
    }

    private static Optional<Trip.Leg> leg(GameTestHelper helper, UUID id) {
        return PresenceTests.record(helper, id).trip().map(Trip::leg);
    }

    private static Place place(GameTestHelper helper, BlockPos core) {
        return Place.of(helper.getLevel(), helper.absolutePos(core));
    }

    /** Takes away the owner, their dragons, and the Nether Wells {@code away}, so nothing of the test is left to route. */
    private static void clearAway(GameTestHelper helper, Rider owner, Set<Place> away) {
        var server = helper.getLevel().getServer();
        for (var place : away) {
            VoidstoneNetworks.of(place.level(server)).leave(place.pos());
            SavedVoidWells.of(server).remove(place);
        }
        var dragons = SavedVoidDragons.of(server);
        dragons.all().stream().filter(dragon -> dragon.owner().equals(owner.getUUID())).map(dragon -> dragon.rest()).toList()
                .forEach(rest -> dragons.removeAt(rest::equals));
        owner.leave();
    }

    /**
     * The test's player, in its level, where the server finds them as it does a client's, standing on
     * the platform north of the Well, and keeping what they are shown above the hotbar; never joined to
     * the server, so nothing is sent them.
     */
    private static final class Rider extends ServerPlayer {

        final List<Component> overlays = new ArrayList<>();

        private Rider(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "dragon-rider"), ClientInformation.createDefault());
            connection = new Quiet(level.getServer(), this);
        }

        static Rider join(GameTestHelper helper) {
            var rider = new Rider(helper.getLevel());
            var feet = helper.absoluteVec(new Vec3(4.5, 1, 0.5));
            rider.snapTo(feet.x, feet.y, feet.z, 180.0F, 0.0F);
            helper.getLevel().addNewPlayer(rider);
            return rider;
        }

        void leave() {
            level().removePlayerImmediately(this, RemovalReason.DISCARDED);
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (overlay) {
                overlays.add(message);
            }
        }
    }

    /** A client's connection that sends nothing, but whose player goes where the server teleports them, as a client's does. */
    private static final class Quiet extends ServerGamePacketListenerImpl {

        Quiet(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND) {
                @Override
                public void setListenerForServerboundHandshake(PacketListener listener) {
                }
            }, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void tick() {
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        }

        @Override
        public void disconnect(Component message) {
        }
    }
}
