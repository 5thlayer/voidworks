// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.CallPayload;
import io.github._5thlayer.voidworks.dragon.DiveColour;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.DragonRiding;
import io.github._5thlayer.voidworks.dragon.LifeStage;
import io.github._5thlayer.voidworks.dragon.MoteLoad;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedHatchery;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Showing;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
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
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/**
 * Riding and calling a Void Dragon, with a player in the level: its owner mounts one of theirs
 * at a Well and no one else can, it waits there for them, strafing picks another of their Wells and
 * jumping dives there, holding them unseen and unhurt through the dive until it sets them down on that
 * Well's rim; the Well they pick is a stop added to the dragon's trip; and standing on a Well's rim for a
 * second calls a dragon there, which waits while they stay, the dragon pushing no one off the rim.
 * The owner's other Well stands high over the test's, in its column, clear of every other test, so a
 * dive between them is the shortest a dive takes. Which trips a dragon makes is {@code RoutingTest}'s.
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
    private static final int BRIEF_Y = 128;
    private static final int OTHERS_RIM_Y = 144;
    private static final int RIDDEN_DIVE_Y = 96;
    private static final int RIDDEN_REFUSED_Y = 112;
    /** How many motes a ridden dive's Nether Well is short of its target: more than a ridden dive to it carries. */
    private static final int FAR_SHORT = 4_000;
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
        tests.testAlone("a_rider_watches_the_dragon_start_down_and_sees_the_void_only_once_their_eyes_go_below_the_wells_rim",
                TICKS, RideTests::seesTheVoidInTheShaft);
        tests.testAlone("the_well_a_rider_picks_is_a_stop_added_to_the_trip_which_gets_what_it_is_short_of_and_the_rest_goes_on",
                TICKS, RideTests::addedStop);
        tests.testAlone("rimcall_its_owner_standing_on_a_wells_rim_calls_a_void_dragon_there_which_waits_while_they_stay_and_its_shaft_calls_none",
                TICKS, RideTests::calls);
        tests.testAlone("rimcall_a_void_dragons_presence_left_where_nothing_ticks_gives_way_to_a_new_one_at_its_well", TICKS, RideTests::strandedPresence);
        tests.testAlone("rimcall_a_step_onto_a_wells_rim_shorter_than_a_second_calls_no_dragon", TICKS, RideTests::briefStep);
        tests.testAlone("rimcall_standing_on_the_rim_of_another_owners_well_calls_no_dragon_and_says_nothing", TICKS, RideTests::othersRim);
        tests.testAlone("rimcall_a_player_on_a_wells_rim_beside_a_void_dragon_sitting_astride_it_is_not_pushed", TICKS, RideTests::notPushed);
        tests.testAlone("a_rider_leaving_the_game_is_set_down_first_on_the_rim_of_the_well_the_dragon_is_at_or_diving_to", TICKS, RideTests::leaves);
        tests.testAlone("ridden_dive_to_the_nether_is_short_and_carries_its_load_cut_putting_the_rest_back_where_it_is", TICKS,
                RideTests::riddenDive);
        tests.testAlone("ridden_dive_is_refused_while_the_well_it_is_at_has_no_room_for_what_it_cannot_carry", TICKS,
                RideTests::riddenDiveRefused);
        tests.testAlone("a_void_dragon_resting_at_a_well_takes_off_to_hover_as_its_owner_mounts_it_and_lands_again_once_they_get_off", TICKS,
                RideTests::takesOffForItsRider);
        tests.testAlone("life_stage_an_old_void_dragon_that_outlives_its_stage_dies_at_the_well_it_unloads_at_and_lowers_its_owners_next_egg_price",
                TICKS, RideTests::diesOfOldAge);
        tests.testAlone("life_stage_a_void_dragon_dying_of_old_age_sets_its_rider_down_on_the_rim_of_the_well_it_dies_at", TICKS,
                RideTests::setsItsRiderDownAsItDies);
        tests.testAlone("life_stage_a_void_dragon_dying_of_old_age_sets_down_a_rider_who_mounted_it_as_it_unloaded_and_stayed_on", TICKS,
                RideTests::setsDownARiderStillMountedAsItDies);
    }

    /**
     * An old dragon a few motes short of the end of its stage loads at the test's Well for the high
     * one, unloads there, and dies: its record is gone, its presence plays its death over the high Well
     * and is gone, and its owner's next egg costs as it did before they hatched it.
     */
    private static void diesOfOldAge(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        PresenceTests.wakeWell(helper, owner, HIGH_WELL);
        var hatchery = SavedHatchery.of(server);
        var hatched = Place.of(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
        var dragonId = new AtomicReference<UUID>();
        var dying = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL) && awake(helper, HIGH_WELL), "the Wells have not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, HELD);
                    shortOfItsTarget(helper, owner, HIGH_WELL, SHORT);
                    // Its owner hatched it: it counts toward their next egg while it lives.
                    hatchery.startSoaking(hatched, owner.getUUID(), EggTests.eggPrice());
                    hatchery.hatch(hatched);
                    helper.assertTrue(hatchery.dragons(owner.getUUID()) == 1, "the hatched dragon does not count");
                    dragonId.set(old(helper, owner, place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> helper.assertTrue(SavedVoidDragons.of(server).get(dragonId.get()).isEmpty(), "the old dragon has not died"))
                .thenExecute(() -> {
                    helper.assertTrue(total(helper, HIGH_WELL) == FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow(),
                            "the dragon died before it unloaded: the high Well holds " + total(helper, HIGH_WELL));
                    helper.assertTrue(hatchery.dragons(owner.getUUID()) == 0, "the dead dragon still counts toward its owner's next egg");
                    helper.assertTrue(DragonPresences.presence(server, dragonId.get()).isEmpty(), "the dead dragon is still shown as its presence");
                    var shown = helper.getLevel().getEntitiesOfClass(VoidDragon.class, new AABB(helper.absolutePos(HIGH_WELL)).inflate(16),
                            presence -> presence.dying() && presence.dragonId().equals(Optional.of(dragonId.get())));
                    helper.assertTrue(shown.size() == 1, "the dragon's death is shown " + shown.size() + " times over the high Well");
                    dying.set(shown.getFirst());
                })
                .thenWaitUntil(() -> helper.assertTrue(dying.get() != null && dying.get().isRemoved(), "the dying presence is not gone"))
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
    }

    /**
     * Its owner rides an old dragon loading at the test's Well and picks the high Well, short of its
     * target: the dragon dives there, unloads, and dies, setting them down on that Well's rim.
     */
    private static void setsItsRiderDownAsItDies(GameTestHelper helper) {
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
                    dragonId.set(old(helper, owner, place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> presence.set(atWork(helper, dragonId.get())))
                .thenExecute(() -> {
                    presence.get().interact(owner, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(owner.getVehicle() == presence.get(), "its owner did not mount the old dragon");
                    owner.setLastClientInput(RIGHT);
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(Input.EMPTY))
                .thenExecuteAfter(1, () -> owner.setLastClientInput(JUMP))
                .thenExecuteAfter(1, () -> {
                    owner.setLastClientInput(Input.EMPTY);
                    helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.TO_PICKED)), "jumping did not dive to the high Well");
                })
                .thenWaitUntil(() -> helper.assertTrue(SavedVoidDragons.of(server).get(dragonId.get()).isEmpty(), "the old dragon has not died"))
                .thenExecute(() -> {
                    helper.assertTrue(!owner.isPassenger(), "the dead dragon's rider still rides");
                    assertOnTheRim(helper, owner, HIGH_WELL);
                    helper.assertTrue(!DragonRiding.inTheDive(owner) && !owner.hasEffect(MobEffects.INVISIBILITY), "the rider set down is still in the dive");
                })
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
    }

    /**
     * An old dragon loads at the test's Well for the high one and dives there alone; its owner mounts
     * it as it unloads there and stays on through the end of its stop: it dies with them on its back,
     * setting them down on the high Well's rim.
     */
    private static void setsDownARiderStillMountedAsItDies(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        PresenceTests.wakeWell(helper, owner, HIGH_WELL);
        var dragonId = new AtomicReference<UUID>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL) && awake(helper, HIGH_WELL), "the Wells have not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, HELD);
                    shortOfItsTarget(helper, owner, HIGH_WELL, SHORT);
                    dragonId.set(old(helper, owner, place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.UNLOADING)), "the old dragon is not unloading");
                    var presence = atWork(helper, dragonId.get());
                    helper.assertTrue(PresenceTests.record(helper, dragonId.get()).rest().equals(place(helper, HIGH_WELL)), "the old dragon is not unloading at the high Well");
                    presence.interact(owner, InteractionHand.MAIN_HAND, presence.position());
                    helper.assertTrue(owner.getVehicle() == presence, "its owner did not mount the unloading dragon");
                })
                .thenWaitUntil(() -> helper.assertTrue(SavedVoidDragons.of(server).get(dragonId.get()).isEmpty(), "the ridden old dragon has not died"))
                .thenExecute(() -> {
                    helper.assertTrue(!owner.isPassenger(), "the dead dragon's rider still rides");
                    assertOnTheRim(helper, owner, HIGH_WELL);
                })
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
    }

    /** @return a new dragon of {@code owner}'s resting at {@code rest}, old, a few motes short of the end of its stage */
    private static VoidDragonRecord old(GameTestHelper helper, Rider owner, Place rest) {
        var dragons = SavedVoidDragons.of(helper.getLevel().getServer());
        var grown = dragons.add(owner.getUUID(), rest);
        var old = new VoidDragonRecord(grown.id(), grown.owner(), grown.health(), grown.rest(), grown.load(), grown.trip(), 0,
                LifeStage.values().length * VoidworksConfig.lifeStageMotes() - 1);
        dragons.update(old);
        return old;
    }

    /**
     * Sitting at the test's Well, the dragon takes off as its owner mounts it and hovers over the shaft
     * for as long as they ride; sneaking, they are set down on the rim, and it lands again.
     */
    private static void takesOffForItsRider(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        var risen = WellStructure.shaft(helper.absolutePos(WELL), Direction.NORTH).above(DragonPresences.MOUTH + DragonPresences.RISE);
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id()))
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(shown.isPresent() && shown.get().perch() == VoidDragon.Perch.SITTING, "the dragon is not seen sitting at the Well");
                    presence.set(shown.get());
                })
                .thenExecute(() -> {
                    presence.get().interact(owner, InteractionHand.MAIN_HAND, presence.get().position());
                    helper.assertTrue(owner.getVehicle() == presence.get(), "its owner did not mount the sitting dragon");
                    helper.assertTrue(presence.get().perch() == VoidDragon.Perch.TAKING_OFF, "the mounted dragon is " + presence.get().perch() + ", not taking off");
                })
                .thenWaitUntil(() -> helper.assertTrue(presence.get().perch() == VoidDragon.Perch.FLYING, "the mounted dragon has not risen"))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(presence.get().perch() == VoidDragon.Perch.FLYING && Math.abs(presence.get().getY() - risen.getY()) < 0.5,
                            "the ridden dragon is " + presence.get().perch() + " at " + presence.get().position() + ", not hovering over the shaft at " + risen);
                    owner.setShiftKeyDown(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(!owner.isPassenger(), "sneaking did not get its rider off"))
                .thenExecute(() -> {
                    assertOnTheRim(helper, owner, WELL);
                    owner.setShiftKeyDown(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(presence.get().perch() == VoidDragon.Perch.SITTING, "the dragon its rider got off has not landed again"))
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
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
     * Jumping on a dragon hovering over the test's Well, its rider watches it start down, still on its
     * back and unseen, and sees the void only a set while after their eyes have gone below the Well's
     * rim, into the shaft, until the dragon emerging at the high Well brings them up.
     */
    private static void seesTheVoidInTheShaft(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        PresenceTests.wakeWell(helper, owner, HIGH_WELL);
        // The shaft's mouth, the first open air over the Well's rim.
        int mouth = WellStructure.shaft(helper.absolutePos(WELL), Direction.NORTH).above(DragonPresences.MOUTH).getY();
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        var over = new AtomicInteger();
        var under = new AtomicInteger();
        var unseen = new AtomicBoolean(true);
        var voided = new AtomicBoolean(true);
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
                    owner.setLastClientInput(RIGHT);
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(Input.EMPTY))
                .thenWaitUntil(() -> helper.assertTrue(presence.get().perch() == VoidDragon.Perch.FLYING, "the mounted dragon has not taken off"))
                .thenExecute(() -> owner.setLastClientInput(JUMP))
                .thenExecuteAfter(1, () -> {
                    owner.setLastClientInput(Input.EMPTY);
                    helper.assertTrue(presence.get().carrying() && DragonRiding.inTheDive(owner), "jumping did not dive");
                    helper.assertTrue(!presence.get().inTheVoid(), "the rider sees the void with their eyes at " + owner.getEyeY() + ", over the mouth at " + mouth);
                    helper.assertTrue(owner.getY() > presence.get().getY() + 0.5, "the rider at " + owner.position() + " is not on the back of the diving dragon at " + presence.get().position());
                })
                .thenWaitUntil(() -> {
                    if (!presence.get().inTheVoid()) {
                        if (owner.getEyeY() >= mouth) {
                            over.incrementAndGet();
                        } else {
                            under.incrementAndGet();
                        }
                        unseen.compareAndSet(true, owner.hasEffect(MobEffects.INVISIBILITY));
                    }
                    helper.assertTrue(presence.get().inTheVoid(), "the rider does not see the void");
                })
                .thenExecute(() -> {
                    helper.assertTrue(unseen.get(), "the rider is seen as the dragon starts down");
                    helper.assertTrue(over.get() > 0, "the rider never saw the dragon start down");
                    helper.assertTrue(under.get() == VoidDragon.VOID_DELAY_TICKS,
                            "the rider saw the shaft for " + under.get() + " ticks with their eyes below its mouth at " + mouth + ", not " + VoidDragon.VOID_DELAY_TICKS);
                })
                .thenWaitUntil(() -> {
                    if (!presence.get().isRemoved()) {
                        voided.compareAndSet(true, presence.get().inTheVoid());
                    }
                    helper.assertTrue(owner.getVehicle() instanceof VoidDragon emerging && emerging != presence.get(), "the rider is not brought up at the high Well");
                })
                .thenExecute(() -> {
                    helper.assertTrue(voided.get(), "the rider stopped seeing the void in the dive");
                    var emerging = (VoidDragon) owner.getVehicle();
                    helper.assertTrue(!emerging.inTheVoid(), "the rider brought up at the high Well still sees the void");
                })
                .thenWaitUntil(() -> helper.assertTrue(!owner.isPassenger(), "the rider has not been set down"))
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
                    helper.assertTrue(setDown.load().equals(MoteLoad.of(Map.of(PresenceTests.GRADE, (long) SHORT))), "the dragon carries " + setDown.load() + " on");
                    // Staying on the rim would call the dragon to wait there for them.
                    stepOff(helper, owner);
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
     * The dragon loads at the test's Well for a Well in the Nether far short of its target; its rider
     * picks that Well and jumps: the dive takes the ridden time, its rider is shown the Well and the
     * Nether's colour, and the dragon carries only the share of its load the time saved leaves, having
     * put the rest back into the test's Well.
     */
    private static void riddenDive(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        PresenceTests.wakeWell(helper, owner, WELL);
        var under = PresenceTests.under(helper, nether, RIDDEN_DIVE_Y);
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        var held = new AtomicLong();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, 2 * FAR_SHORT);
                    farShort(helper, owner, nether, under);
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> presence.set(atWork(helper, dragonId.get())))
                .thenExecute(() -> {
                    var loading = PresenceTests.record(helper, dragonId.get());
                    helper.assertTrue(loading.carried() == FAR_SHORT, "the dragon is not loading what the Nether Well is short of: " + loading);
                    held.set(total(helper, WELL));
                    mountAndPick(helper, owner, presence.get());
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(JUMP))
                .thenExecuteAfter(1, () -> {
                    owner.setLastClientInput(Input.EMPTY);
                    var diving = PresenceTests.record(helper, dragonId.get());
                    var trip = diving.trip().orElseThrow();
                    int ridden = VoidworksConfig.dragonRiddenDiveTicks();
                    helper.assertTrue(trip.leg() == Trip.Leg.TO_PICKED && trip.until() - trip.countedTo() == ridden,
                            "the ridden dive takes " + (trip.until() - trip.countedTo()) + " ticks, not " + ridden);
                    long most = (long) VoidworksConfig.dragonLoad() * ridden / VoidworksConfig.dragonDiveTicks();
                    helper.assertTrue(diving.carried() > 0 && diving.carried() <= most,
                            "the ridden dragon carries " + diving.carried() + ", more than the " + most + " its time saved leaves");
                    helper.assertTrue(total(helper, WELL) == held.get() + FAR_SHORT - diving.carried(),
                            "the test's Well holds " + total(helper, WELL) + ", not what the dragon put back");
                    var shown = presence.get().diveShown();
                    helper.assertTrue(presence.get().carrying() && shown.label().equals(DragonRiding.label(server, under)),
                            "the rider is shown " + shown.label() + ", not the Nether Well");
                    helper.assertTrue(shown.until() - shown.from() == ridden, "the rider is shown a dive of " + (shown.until() - shown.from()) + " ticks");
                    helper.assertTrue(shown.colour() == DiveColour.of(server, under.dimension()) && shown.colour() != DiveColour.VOID,
                            "the rider is shown the colour " + Integer.toHexString(shown.colour()) + ", not the Nether's");
                })
                .thenWaitUntil(() -> helper.assertTrue(PresenceTests.record(helper, dragonId.get()).rest().equals(under), "the dragon has not reached the Nether Well"))
                .thenExecute(() -> clearAway(helper, owner, Set.of(under)))
                .thenSucceed();
    }

    /**
     * As {@link #riddenDive}, but the test's Well is full by the time its rider jumps: the dragon does
     * not dive, keeps its load and its rider, and tells them why above the hotbar.
     */
    private static void riddenDiveRefused(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        PresenceTests.wakeWell(helper, owner, WELL);
        var under = PresenceTests.under(helper, nether, RIDDEN_REFUSED_Y);
        var dragonId = new AtomicReference<UUID>();
        var presence = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, 2 * FAR_SHORT);
                    farShort(helper, owner, nether, under);
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id());
                })
                .thenWaitUntil(() -> presence.set(atWork(helper, dragonId.get())))
                .thenExecute(() -> {
                    PresenceTests.deposit(helper, WELL, Math.toIntExact(VoidworksConfig.wellCapacity() - total(helper, WELL)));
                    mountAndPick(helper, owner, presence.get());
                })
                .thenExecuteAfter(1, () -> owner.setLastClientInput(JUMP))
                .thenExecuteAfter(1, () -> {
                    owner.setLastClientInput(Input.EMPTY);
                    var refused = Component.translatable("voidworks.dragon.ride.no_room", DragonRiding.label(server, under));
                    helper.assertTrue(owner.overlays.getLast().equals(refused), "the rider was told " + owner.overlays.getLast() + ", not that there is no room");
                    var still = PresenceTests.record(helper, dragonId.get());
                    helper.assertTrue(still.trip().map(Trip::leg).equals(Optional.of(Trip.Leg.LOADING)) && still.carried() == FAR_SHORT,
                            "the refused dragon is " + still);
                    helper.assertTrue(owner.getVehicle() == presence.get() && !presence.get().carrying(), "the refused rider is not still mounted at the Well");
                    clearAway(helper, owner, Set.of(under));
                })
                .thenSucceed();
    }

    /** Makes a Well of {@code owner}'s at {@code under} in the Nether, {@link #FAR_SHORT} short of a quarter's target. */
    private static void farShort(GameTestHelper helper, Rider owner, ServerLevel nether, Place under) {
        long target = FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow();
        RoutingTests.assertUnloaded(helper, nether, under.pos());
        VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), Map.of(PresenceTests.GRADE, target - FAR_SHORT));
        SavedVoidWells.of(helper.getLevel().getServer()).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.QUARTER));
    }

    /** Its owner mounts the dragon's {@code presence} at work and strafes to pick their only other Well. */
    private static void mountAndPick(GameTestHelper helper, Rider owner, VoidDragon presence) {
        presence.interact(owner, InteractionHand.MAIN_HAND, presence.position());
        helper.assertTrue(owner.getVehicle() == presence, "its owner did not mount the loading dragon");
        owner.setLastClientInput(RIGHT);
    }

    /**
     * The dragon rests at a Well of its owner's in the Nether, in a chunk nothing loads; its owner
     * stands in the test's Well's shaft, which calls none, then on its rim, and it comes there and waits
     * while they stay, being told it is coming, then that it waits; once they step off, its stop ends,
     * and it rests there.
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
                .thenExecute(() -> dragonId.set(restingInTheNether(helper, owner, under)))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).isEmpty(), "the dragon with nothing to do is " + leg(helper, dragonId.get()));
                    var floor = Vec3.atBottomCenterOf(WellStructure.shaft(helper.absolutePos(WELL), Direction.NORTH).below());
                    owner.setPos(floor.x, floor.y, floor.z);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).isEmpty(), "its owner standing in the shaft called the dragon: it is " + leg(helper, dragonId.get()));
                    standOnTheRim(helper, owner, WELL);
                })
                .thenWaitUntil(() -> helper.assertTrue(owner.overlays.contains(Component.translatable("voidworks.dragon.coming")),
                        "its owner was not told a dragon is coming"))
                .thenExecute(() -> {
                    helper.assertTrue(!owner.calls.isEmpty(), "its owner was sent no dive to show as a bar");
                    var call = owner.calls.getFirst();
                    helper.assertTrue(call.until() > call.from() && call.until() - call.from() <= VoidworksConfig.dragonRiddenDiveTicks(),
                            "the dive sent runs from " + call.from() + " to " + call.until() + ", not within a ridden dive's time");
                    helper.assertTrue(call.colour() == DiveColour.of(server, here.dimension()), "the bar is not in the colour of the dimension the dragon comes to");
                })
                .thenWaitUntil(() -> {
                    var called = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(called.rest().equals(here) && leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.WAITING)),
                            "the dragon has not come to the Well its owner calls it to");
                })
                .thenWaitUntil(() -> helper.assertTrue(owner.overlays.contains(Component.translatable("voidworks.dragon.waits")),
                        "its owner was not told the dragon waits for them"))
                .thenIdle(VoidworksConfig.dragonStopTicks() + 40)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).equals(Optional.of(Trip.Leg.WAITING)),
                            "the dragon left while its owner stays on the rim: it is " + leg(helper, dragonId.get()));
                    var waiting = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(waiting.isPresent(), "the dragon waiting at the Well is not seen");
                    helper.assertTrue(waiting.get().perch() == VoidDragon.Perch.FLYING, "the dragon waiting for its owner is " + waiting.get().perch() + ", not in the air");
                    stepOff(helper, owner);
                })
                .thenWaitUntil(() -> helper.assertTrue(leg(helper, dragonId.get()).isEmpty(), "the dragon still waits once its owner has left the rim"))
                .thenExecute(() -> {
                    var rested = SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow();
                    helper.assertTrue(rested.rest().equals(here), "the dragon rests at " + rested.rest() + ", not the Well it was called to");
                    clearAway(helper, owner, Set.of(under));
                })
                .thenSucceed();
    }

    /**
     * A dragon rests at the test's Well; its presence is moved far off, where no chunk is loaded, as
     * one left behind in a chunk that unloaded: it is taken away, and a new one is seen at the Well.
     */
    private static void strandedPresence(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        var dragonId = new AtomicReference<UUID>();
        var stranded = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id()))
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(shown.isPresent() && shown.get().perch() == VoidDragon.Perch.SITTING, "the dragon is not seen sitting at the Well");
                    stranded.set(shown.get());
                })
                .thenExecute(() -> {
                    var far = helper.absoluteVec(new Vec3(0, 1, 100_000));
                    stranded.get().setPos(far.x, far.y, far.z);
                })
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(shown.isPresent() && shown.get() != stranded.get() && shown.get().blockPosition().closerThan(helper.absolutePos(WELL), 16),
                            "no new presence is seen at the Well");
                    helper.assertTrue(stranded.get().isRemoved(), "the stranded presence is still there");
                })
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
    }

    /** Its owner steps onto the test's Well's rim for half a second and off again: the dragon resting in the Nether stays there. */
    private static void briefStep(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        PresenceTests.wakeWell(helper, owner, WELL);
        var under = PresenceTests.under(helper, nether, BRIEF_Y);
        var dragonId = new AtomicReference<UUID>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> dragonId.set(restingInTheNether(helper, owner, under)))
                .thenIdle(40)
                .thenExecute(() -> standOnTheRim(helper, owner, WELL))
                .thenExecuteAfter(10, () -> stepOff(helper, owner))
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).isEmpty(), "a brief step onto the rim called the dragon: it is " + leg(helper, dragonId.get()));
                    helper.assertTrue(SavedVoidDragons.of(server).get(dragonId.get()).orElseThrow().rest().equals(under), "the dragon left the Nether");
                    clearAway(helper, owner, Set.of(under));
                })
                .thenSucceed();
    }

    /**
     * Another player, whose dragon rests at their Well in the Nether, stands on the rim of the owner's
     * Well: their dragon stays there, and they are told nothing.
     */
    private static void othersRim(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var other = Rider.join(helper);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        PresenceTests.wakeWell(helper, owner, WELL);
        var under = PresenceTests.under(helper, nether, OTHERS_RIM_Y);
        var dragonId = new AtomicReference<UUID>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> {
                    dragonId.set(restingInTheNether(helper, other, under));
                    standOnTheRim(helper, other, WELL);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(leg(helper, dragonId.get()).isEmpty(), "standing on another's rim called the dragon: it is " + leg(helper, dragonId.get()));
                    helper.assertTrue(other.overlays.isEmpty(), "the player on another's rim was told " + other.overlays);
                    clearAway(helper, other, Set.of(under));
                    clearAway(helper, owner, Set.of());
                })
                .thenSucceed();
    }

    /**
     * A dragon sits astride the test's Well's rim, resting there, and its owner stands on the rim over
     * the core, inside its presence's box: for two seconds nothing pushes them.
     */
    private static void notPushed(GameTestHelper helper) {
        var owner = Rider.join(helper);
        var server = helper.getLevel().getServer();
        PresenceTests.wakeWell(helper, owner, WELL);
        var dragonId = new AtomicReference<UUID>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(awake(helper, WELL), "the Well has not woken"))
                .thenExecute(() -> dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), place(helper, WELL)).id()))
                .thenWaitUntil(() -> {
                    var shown = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(shown.isPresent() && shown.get().perch() == VoidDragon.Perch.SITTING, "the dragon is not seen sitting at the Well");
                    standOnTheRim(helper, owner, WELL);
                    owner.setDeltaMovement(Vec3.ZERO);
                    helper.assertTrue(shown.get().getBoundingBox().contains(owner.position()), "its owner on the rim is not inside the presence's box");
                })
                .thenExecuteFor(40, () -> {
                    var pushed = owner.getDeltaMovement().horizontalDistance();
                    helper.assertTrue(pushed == 0, "the sitting dragon pushes its owner on the rim, at " + pushed);
                })
                .thenExecute(() -> clearAway(helper, owner, Set.of()))
                .thenSucceed();
    }

    /** @return the id of a new dragon of {@code owner}'s resting at their Well at {@code under} in the Nether, which nothing loads */
    private static UUID restingInTheNether(GameTestHelper helper, ServerPlayer owner, Place under) {
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        RoutingTests.assertUnloaded(helper, nether, under.pos());
        VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), Map.of());
        SavedVoidWells.of(server).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.NONE));
        return SavedVoidDragons.of(server).add(owner.getUUID(), under).id();
    }

    /** Puts {@code player} back on the platform north of the test's Well, where they joined. */
    private static void stepOff(GameTestHelper helper, ServerPlayer player) {
        var outside = helper.absoluteVec(new Vec3(4.5, 1, 0.5));
        player.setPos(outside.x, outside.y, outside.z);
    }

    /** Puts {@code player} on the rim of the Well at {@code core}, over the core. */
    private static void standOnTheRim(GameTestHelper helper, ServerPlayer player, BlockPos core) {
        var rim = Vec3.atBottomCenterOf(helper.absolutePos(core).above(3));
        player.setPos(rim.x, rim.y, rim.z);
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
        /** The called dragon's dives they were sent, for the bar their client shows. */
        final List<CallPayload> calls = new ArrayList<>();

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
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof CallPayload call && player instanceof Rider rider) {
                rider.calls.add(call);
            }
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        }

        @Override
        public void disconnect(Component message) {
        }
    }
}
