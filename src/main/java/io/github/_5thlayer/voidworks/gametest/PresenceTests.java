// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Showing;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.network.MoteStore;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Void Dragon seen at work: its presence follows its record through a stop and a dive at a Well
 * whose chunk is loaded, the test's own, and never drives it (ADR 0005); and an egg it lays there pops
 * out of the shaft, for whoever places it. Its other Well is in the Nether under the test's, so the
 * dive is the fixed time alone, and in a chunk nothing loads, where the dragon shows nothing. Which
 * trips a dragon makes, and when it lays, is {@code RoutingTest}'s.
 */
final class PresenceTests {

    /** The core, in the middle of its north wall's middle course, its floor on the platform. */
    private static final BlockPos WELL = new BlockPos(4, 3, 2);
    /** The grade carried: worth something in the Overworld and the Nether. */
    private static final int GRADE = 4;
    /** What the reservoir holds. */
    private static final int HELD = 1_000;
    /** How many motes the Well below its target is short of it: less than a load, so one trip meets it. */
    private static final int SHORT = 100;

    /**
     * The heights of each test's Nether Well. Two tests side by side may put theirs at neighbouring
     * places in Nether blocks, where they would join one network and conduct; a dive's time counts no
     * height.
     */
    private static final int STOPS_Y = 64;
    private static final int EMERGES_Y = 96;
    private static final int LAYS_Y = 32;

    /** Where the laid egg is placed, on the platform clear of the Well. */
    private static final BlockPos EGG = new BlockPos(0, 1, 4);
    /** Where Voidstone touches it, a network of its own. */
    private static final BlockPos EGG_NETWORK = EGG.south();

    /** Waking the Well, then a stop to load, a dive and a stop to unload, at the default numbers, with room to spare. */
    private static final int TICKS = 1_200;

    private PresenceTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_void_dragon_stopping_at_a_loaded_well_is_seen_at_work_over_its_shaft_then_diving_away_and_leaves_no_trace", TICKS,
                PresenceTests::stopsThenDives);
        tests.test("a_void_dragon_at_an_unloaded_well_shows_nothing_and_arriving_at_a_loaded_one_is_seen_emerging_then_resting_there", TICKS,
                PresenceTests::emergesThenRests);
        tests.test("a_void_dragon_that_has_carried_enough_since_its_last_egg_lays_a_drained_dragon_egg_in_the_shaft_of_the_well_it_unloads_at",
                TICKS, PresenceTests::laysInTheShaft);
    }

    /**
     * Resting at the test's Well, a reservoir, the dragon loads there for a Well in the Nether: it is
     * seen at work over the shaft, and taken away as a chunk unloading takes it, it comes back mid-stop
     * with the record unchanged; then it dives into the shaft and is gone, and the record carries on.
     */
    private static void stopsThenDives(GameTestHelper helper) {
        var owner = owner(helper);
        var well = wakeWell(helper, owner);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        var here = Place.of(helper.getLevel(), helper.absolutePos(WELL));
        var under = under(helper, nether, STOPS_Y);
        long target = FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow();
        var dragonId = new AtomicReference<UUID>();
        var shown = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stored(helper).isPresent(), "the Well has not woken"))
                .thenExecute(() -> {
                    deposit(helper, HELD);
                    RoutingTests.assertUnloaded(helper, nether, under.pos());
                    VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), Map.of(GRADE, target - SHORT));
                    SavedVoidWells.of(server).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.QUARTER));
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), here).id());
                })
                .thenWaitUntil(() -> {
                    var presence = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(presence.isPresent() && presence.get().showing() == Showing.WORKING, "the dragon is not seen at work at the Well");
                    assertOverTheShaft(helper, presence.get(), well);
                })
                .thenExecute(() -> {
                    var before = record(helper, dragonId.get());
                    helper.assertTrue(before.trip().map(Trip::leg).equals(Optional.of(Trip.Leg.LOADING)), "the dragon is " + before.trip() + ", not loading");
                    var first = DragonPresences.presence(server, dragonId.get()).orElseThrow();
                    first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    helper.assertTrue(record(helper, dragonId.get()).equals(before), "taking the presence away changed the dragon's record");
                    shown.set(first);
                })
                .thenWaitUntil(() -> {
                    var presence = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(presence.isPresent() && presence.get() != shown.get(), "the presence taken away mid-stop has not come back");
                    // Back soon after the stop began, it may be seen emerging; either way it is at the Well.
                    var showing = presence.get().showing();
                    helper.assertTrue(showing == Showing.WORKING || showing == Showing.EMERGING, "the presence back mid-stop is " + showing);
                    helper.assertTrue(presence.get().blockPosition().closerThan(WellStructure.shaft(well.getBlockPos(), Direction.NORTH), VoidDragon.WORKING_RADIUS + 6),
                            "the presence back mid-stop is at " + presence.get().blockPosition() + ", not at the Well");
                    shown.set(presence.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.get().showing() == Showing.DIVING, "the presence has not dived away"))
                .thenExecute(() -> {
                    var diving = record(helper, dragonId.get()).trip().map(Trip::leg);
                    helper.assertTrue(diving.equals(Optional.of(Trip.Leg.TO_DESTINATION)), "the presence dived away while the dragon is " + diving);
                    helper.assertTrue(DragonPresences.presence(server, dragonId.get()).equals(Optional.of(shown.get()))
                            && presences(helper, dragonId.get()).size() == 1, "the diving dragon shows another presence than the one diving away");
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.get().isRemoved(), "the diving presence is still here"))
                .thenExecute(() -> {
                    var shaft = WellStructure.shaft(helper.absolutePos(WELL), Direction.NORTH);
                    helper.assertTrue(shown.get().getY() < shaft.getY(), "the presence was gone at " + shown.get().blockPosition() + ", not down its shaft");
                    helper.assertTrue(presences(helper, dragonId.get()).isEmpty() && DragonPresences.presence(server, dragonId.get()).isEmpty(),
                            "the dive left a presence behind");
                })
                .thenWaitUntil(() -> {
                    var after = record(helper, dragonId.get());
                    helper.assertTrue(after.trip().isEmpty() && after.rest().equals(under), "the dragon has not finished its trip: " + after);
                    helper.assertTrue(RoutingTests.total(nether, under.pos()) == target, "the Nether Well holds " + RoutingTests.total(nether, under.pos()) + ", not its target " + target);
                })
                .thenExecute(() -> {
                    helper.assertTrue(presences(helper, dragonId.get()).isEmpty(), "the dragon away in the Nether shows a presence here");
                    clearAway(helper, under);
                })
                .thenSucceed();
    }

    /**
     * Resting at a reservoir in the Nether, in a chunk nothing loads, the dragon shows nothing while it
     * loads there; then it emerges from the shaft of the test's Well, below its target, rises over it
     * while it unloads, and rests there, the same presence throughout.
     */
    private static void emergesThenRests(GameTestHelper helper) {
        var owner = owner(helper);
        var well = wakeWell(helper, owner);
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        var under = under(helper, nether, EMERGES_Y);
        long target = FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow();
        var shaft = WellStructure.shaft(helper.absolutePos(WELL), Direction.NORTH);
        var dragonId = new AtomicReference<UUID>();
        var shown = new AtomicReference<VoidDragon>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stored(helper).isPresent(), "the Well has not woken"))
                .thenExecute(() -> {
                    owner.setShiftKeyDown(true);
                    helper.useBlock(WELL, owner);
                    helper.assertTrue(well.target() == FillTarget.QUARTER, "the Well's target is " + well.target());
                    deposit(helper, Math.toIntExact(target - SHORT));
                    reservoir(helper, owner, under);
                    dragonId.set(SavedVoidDragons.of(server).add(owner.getUUID(), under).id());
                })
                .thenWaitUntil(() -> helper.assertTrue(record(helper, dragonId.get()).trip().map(Trip::leg).equals(Optional.of(Trip.Leg.LOADING)),
                        "the dragon is not loading in the Nether"))
                .thenExecute(() -> helper.assertTrue(DragonPresences.presence(server, dragonId.get()).isEmpty(),
                        "the dragon at a Well nothing loads shows a presence"))
                .thenWaitUntil(() -> {
                    var presence = DragonPresences.presence(server, dragonId.get());
                    helper.assertTrue(presence.isPresent(), "the dragon arriving at the Well is not seen");
                    shown.set(presence.get());
                })
                .thenExecute(() -> {
                    var presence = shown.get();
                    helper.assertTrue(presence.showing() == Showing.EMERGING, "the arriving dragon is first seen " + presence.showing());
                    // Its body is far wider than the shaft, so it is first seen at its mouth, clear of the walls and rim.
                    var mouth = shaft.above(DragonPresences.MOUTH);
                    helper.assertTrue(presence.getY() >= mouth.getY() && presence.getY() < mouth.getY() + 1
                                    && Math.abs(presence.getX() - (mouth.getX() + 0.5)) < 0.5 && Math.abs(presence.getZ() - (mouth.getZ() + 0.5)) < 0.5,
                            "the dragon emerges at " + presence.position() + ", not at the shaft's mouth " + mouth.toShortString());
                    var arrived = record(helper, dragonId.get()).trip().map(Trip::leg);
                    helper.assertTrue(arrived.equals(Optional.of(Trip.Leg.UNLOADING)), "the dragon emerged while it is " + arrived);
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.get().showing() == Showing.WORKING, "the dragon has not risen out of the shaft"))
                .thenExecute(() -> assertOverTheShaft(helper, shown.get(), well))
                .thenWaitUntil(() -> {
                    var after = record(helper, dragonId.get());
                    helper.assertTrue(after.trip().isEmpty(), "the dragon has not finished unloading");
                    helper.assertTrue(after.rest().pos().equals(helper.absolutePos(WELL)), "the dragon rests at " + after.rest() + ", not the Well");
                    helper.assertTrue(stored(helper).map(MoteStore::total).equals(Optional.of(target)), "the Well holds " + stored(helper).map(MoteStore::total));
                })
                .thenWaitUntil(() -> helper.assertTrue(shown.get().showing() == Showing.RESTING, "the dragon at the Well is not seen resting"))
                .thenExecute(() -> {
                    helper.assertTrue(DragonPresences.presence(server, dragonId.get()).equals(Optional.of(shown.get())),
                            "the dragon came to rest with a presence other than the one that emerged");
                    helper.assertTrue(presences(helper, dragonId.get()).size() == 1, "the dragon shows " + presences(helper, dragonId.get()).size() + " presences");
                    helper.assertTrue(shown.get().getY() > shaft.getY() + 2 || !shown.get().blockPosition().closerThan(helper.absolutePos(WELL), 2.0),
                            "the resting dragon is in the Well's wall, at " + shown.get().blockPosition());
                    clearAway(helper, under);
                })
                .thenSucceed();
    }

    /**
     * A mote short of an egg, the dragon carries a load from the Nether to the test's Well and lays
     * there as it unloads; the egg it lays, placed by another player, soaks at that player's first
     * price and is theirs.
     */
    private static void laysInTheShaft(GameTestHelper helper) {
        var owner = owner(helper);
        var well = wakeWell(helper, owner);
        var server = helper.getLevel().getServer();
        var under = under(helper, server.getLevel(Level.NETHER), LAYS_Y);
        long target = FillTarget.QUARTER.motes(VoidworksConfig.wellCapacity()).orElseThrow();
        // Relative, as GameTestHelper.relativePos turns an unrotated test half round.
        var shaft = WellStructure.shaft(WELL, Direction.NORTH);
        var dragonId = new AtomicReference<UUID>();
        var laid = new AtomicReference<ItemEntity>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stored(helper).isPresent(), "the Well has not woken"))
                .thenExecute(() -> {
                    owner.setShiftKeyDown(true);
                    helper.useBlock(WELL, owner);
                    helper.assertTrue(well.target() == FillTarget.QUARTER, "the Well's target is " + well.target());
                    deposit(helper, Math.toIntExact(target - SHORT));
                    reservoir(helper, owner, under);
                    var dragons = SavedVoidDragons.of(server);
                    var grown = dragons.add(owner.getUUID(), under);
                    dragons.update(new VoidDragonRecord(grown.id(), grown.owner(), grown.health(), grown.rest(), grown.load(), grown.trip(),
                            VoidworksConfig.dragonEggMotes() - 1));
                    dragonId.set(grown.id());
                })
                .thenWaitUntil(() -> {
                    var eggs = helper.getEntities(EntityType.ITEM, shaft, 3.0);
                    helper.assertTrue(eggs.size() == 1, eggs.size() + " items in the shaft, not one egg");
                    laid.set(eggs.getFirst());
                })
                .thenExecute(() -> {
                    var egg = laid.get().getItem();
                    helper.assertTrue(egg.is(VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get()) && egg.getCount() == 1, "the dragon laid " + egg);
                    var after = record(helper, dragonId.get());
                    helper.assertTrue(after.rest().pos().equals(helper.absolutePos(WELL)), "the dragon laid at " + after.rest() + ", not the Well");
                    helper.assertTrue(after.sinceEgg() == SHORT - 1, "the dragon's count is " + after.sinceEgg() + " after its egg, not what it carried over");
                    laid.get().discard();
                    helper.setBlock(EGG_NETWORK, VoidworksBlocks.VOIDSTONE.get());
                    var other = EggTests.player(helper);
                    EggTests.placeOnTheFloor(helper, other, egg.getItem(), EGG);
                    var soaking = EggTests.soaking(helper, EGG);
                    helper.assertTrue(soaking.owner().equals(Optional.of(other.getUUID())), "the laid egg soaks for " + soaking.owner() + ", not its placer");
                    helper.assertTrue(soaking.dormancy().cost() == VoidworksConfig.firstEggPrice(),
                            "the laid egg costs its placer " + soaking.dormancy().cost() + ", not their first price " + VoidworksConfig.firstEggPrice());
                    clearAway(helper, under);
                })
                .thenSucceed();
    }

    /** Makes {@code under}, in a chunk nothing loads, {@code owner}'s reservoir holding {@link #HELD} motes of {@link #GRADE}. */
    private static void reservoir(GameTestHelper helper, ServerPlayer owner, Place under) {
        var nether = under.level(helper.getLevel().getServer());
        RoutingTests.assertUnloaded(helper, nether, under.pos());
        VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), Map.of(GRADE, (long) HELD));
        SavedVoidWells.of(helper.getLevel().getServer()).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.NONE));
    }

    /** Takes away the Nether Well {@code under} and the test's Well, leaving the dragon with none to serve. */
    private static void clearAway(GameTestHelper helper, Place under) {
        VoidstoneNetworks.of(under.level(helper.getLevel().getServer())).leave(under.pos());
        SavedVoidWells.of(helper.getLevel().getServer()).remove(under);
        helper.setBlock(WELL, Blocks.AIR);
    }

    /** At work, a presence flies close about the open air over the shaft, never in the core's wall. */
    private static void assertOverTheShaft(GameTestHelper helper, VoidDragon presence, VoidWellBlockEntity well) {
        var over = WellStructure.shaft(well.getBlockPos(), Direction.NORTH).above(DragonPresences.OVER_SHAFT);
        helper.assertTrue(presence.blockPosition().closerThan(over, VoidDragon.WORKING_RADIUS + 2),
                "the presence at work is at " + presence.blockPosition() + ", not over the shaft at " + over);
    }

    /** The test's Well, whole and owned by {@code owner}, at no price, so it wakes on its first look and joins a network of its own. */
    private static VoidWellBlockEntity wakeWell(GameTestHelper helper, ServerPlayer owner) {
        WellTests.buildWell(helper, WELL, Direction.NORTH);
        var below = helper.absolutePos(WELL.below());
        owner.setYRot(0.0F);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidworksBlocks.VOID_WELL_ITEM.get()));
        owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        var well = helper.getBlockEntity(WELL, VoidWellBlockEntity.class);
        well.dormancy().setPrice(0);
        return well;
    }

    /** Puts {@code count} motes of {@link #GRADE} into the test's Well. */
    private static void deposit(GameTestHelper helper, int count) {
        try (var transaction = Transaction.openRoot()) {
            VoidstoneNetworks.of(helper.getLevel()).deposit(helper.absolutePos(WELL), MoteResource.of(GRADE), count, transaction);
            transaction.commit();
        }
    }

    private static Optional<MoteStore> stored(GameTestHelper helper) {
        return VoidstoneNetworks.of(helper.getLevel()).store(helper.absolutePos(WELL)).filter(store -> store.kind() == StoreKind.WELL);
    }

    /** @return a place in the Nether under the test's Well, the same place in Overworld blocks, at height {@code y} */
    private static Place under(GameTestHelper helper, ServerLevel nether, int y) {
        var pos = helper.absolutePos(WELL);
        return Place.of(nether, new BlockPos(Math.floorDiv(pos.getX(), 8), y, Math.floorDiv(pos.getZ(), 8)));
    }

    private static VoidDragonRecord record(GameTestHelper helper, UUID id) {
        return SavedVoidDragons.of(helper.getLevel().getServer()).get(id).orElseThrow();
    }

    /** Every presence of the dragon {@code id} in the test's level, wherever it is, diving ones too. */
    private static List<VoidDragon> presences(GameTestHelper helper, UUID id) {
        return List.copyOf(helper.getLevel().getEntities(VoidworksEntities.VOID_DRAGON.get(), presence -> presence.dragonId().equals(Optional.of(id))));
    }

    /** The Wells' and the dragon's owner, on the platform north of the Well, never in the level. */
    private static ServerPlayer owner(GameTestHelper helper) {
        var owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dragon-owner"));
        var feet = helper.absoluteVec(new Vec3(4.5, 1, 0.5));
        owner.setPos(feet.x, feet.y, feet.z);
        return owner;
    }
}
