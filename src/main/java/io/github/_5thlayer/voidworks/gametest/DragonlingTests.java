// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.Growth;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.SiphonMotes;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * A Dragonling on a real server: it stays near its home and follows a player holding a Void Siphon;
 * it grows up while its chunk is loaded, and its owner feeds it with a Siphon, by the spend rule
 * where it is, to grow it sooner. Grown, it is a Void Dragon's record, server state, and shows a
 * presence that holds nothing of its own (ADR 0005). The tests stand in the Overworld, where a mote
 * one grade above the pressure releases {@link VoidEnergy#BASE}.
 */
final class DragonlingTests {

    /** In one corner of the platform, in the air. */
    private static final BlockPos HERE = new BlockPos(7, 2, 7);
    /** In the other corner, further from {@link #HERE} than a Dragonling strays from its home. */
    private static final BlockPos THERE = new BlockPos(1, 1, 1);

    private static final int TICKS = 400;

    private DragonlingTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        // A player in the level, holding a Siphon, would draw the Dragonlings of every test running with it.
        tests.testAlone("a_dragonling_follows_a_player_holding_a_void_siphon", TICKS, DragonlingTests::followsASiphonHolder);
        tests.test("a_dragonling_away_from_its_home_heads_back_near_it", TICKS, DragonlingTests::headsHome);
        tests.test("its_owner_feeding_a_dragonling_spends_motes_by_the_spend_rule_and_takes_time_off_its_growing", TICKS,
                DragonlingTests::feedingTakesTimeOff);
        tests.test("a_siphon_holding_nothing_worth_anything_there_or_set_to_charge_or_held_by_another_feeds_a_dragonling_nothing", TICKS,
                DragonlingTests::feedingThatDoesNothing);
        tests.test("a_dragonling_grows_up_in_its_time_into_a_void_dragon_record_resting_where_it_grew_with_a_presence", TICKS,
                DragonlingTests::growsUpInItsTime);
        tests.test("a_dragonling_fed_to_the_end_grows_up_at_once", TICKS, DragonlingTests::fedToTheEnd);
        tests.test("a_dragonling_no_player_owns_never_grows", TICKS, DragonlingTests::unownedNeverGrows);
        tests.test("a_void_dragons_presence_taken_away_comes_back_and_one_dragon_never_shows_two", TICKS, DragonlingTests::onePresence);
        tests.test("nothing_hurts_a_void_dragon", TICKS, DragonlingTests::nothingHurtsAVoidDragon);
    }

    private static void followsASiphonHolder(GameTestHelper helper) {
        var dragonling = hatch(helper, HERE, UUID.randomUUID());
        // In the level, where a mob looks for players, but never joined to the server.
        var player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidworksItems.VOID_SIPHON.get()));
        helper.getLevel().addNewPlayer(player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(dragonling.distanceTo(player) < 4.5,
                        "the Dragonling is still " + dragonling.distanceTo(player) + " from the player holding a Siphon"))
                .thenExecute(() -> helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED))
                .thenSucceed();
    }

    private static void headsHome(GameTestHelper helper) {
        var dragonling = hatch(helper, HERE, UUID.randomUUID());
        var home = helper.absolutePos(THERE);
        dragonling.setHomeTo(home, VoidDragon.HOME_RADIUS);
        helper.assertTrue(!dragonling.isWithinHome(), "the Dragonling starts near its home");
        helper.succeedWhen(() -> helper.assertTrue(dragonling.isWithinHome(),
                "the Dragonling is still " + Math.sqrt(home.distSqr(dragonling.blockPosition())) + " from its home"));
    }

    /** Ten motes, a minute's worth, take a minute off. */
    private static void feedingTakesTimeOff(GameTestHelper helper) {
        var owner = player(helper);
        var dragonling = hatch(helper, HERE, owner.getUUID());
        long before = dragonling.ticksToGrow();
        int grade = grade(helper);
        int minute = Math.toIntExact(VoidworksConfig.dragonlingFeedEnergy() / VoidEnergy.BASE);
        hold(owner, SiphonMode.EMPTY, grade, minute);
        dragonling.interact(owner, InteractionHand.MAIN_HAND, dragonling.position());
        helper.assertTrue(dragonling.ticksToGrow() == before - Growth.TICKS_PER_MINUTE,
                "fed a minute's worth, it has " + dragonling.ticksToGrow() + " ticks to grow, not " + (before - Growth.TICKS_PER_MINUTE));
        helper.assertTrue(VoidSiphonItem.heldMotes(owner.getMainHandItem()).total() == 0,
                "the Siphon still holds " + VoidSiphonItem.heldMotes(owner.getMainHandItem()));
        helper.assertTrue(dragonling.isAlive() && !dragonling.grown(), "a minute's feeding grew it up");
        helper.succeed();
    }

    private static void feedingThatDoesNothing(GameTestHelper helper) {
        var owner = player(helper);
        var dragonling = hatch(helper, HERE, owner.getUUID());
        long before = dragonling.ticksToGrow();
        int grade = grade(helper);
        hold(owner, SiphonMode.EMPTY, grade - 1, 50);
        dragonling.interact(owner, InteractionHand.MAIN_HAND, dragonling.position());
        assertUnfed(helper, dragonling, owner, before, Map.of(grade - 1, 50), "motes worth nothing there");
        hold(owner, SiphonMode.CHARGE, grade, 50);
        dragonling.interact(owner, InteractionHand.MAIN_HAND, dragonling.position());
        assertUnfed(helper, dragonling, owner, before, Map.of(grade, 50), "a Siphon set to charge");
        var stranger = player(helper);
        hold(stranger, SiphonMode.EMPTY, grade, 50);
        dragonling.interact(stranger, InteractionHand.MAIN_HAND, dragonling.position());
        assertUnfed(helper, dragonling, stranger, before, Map.of(grade, 50), "a player who does not own it");
        helper.succeed();
    }

    private static void growsUpInItsTime(GameTestHelper helper) {
        var owner = UUID.randomUUID();
        var dragonling = hatch(helper, HERE, owner);
        dragonling.setTicksToGrow(40);
        var where = dragonling.blockPosition();
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(dragonling.isAlive(), "the Dragonling grew up before its time"))
                .thenWaitUntil(() -> helper.assertTrue(dragonling.isRemoved(), "the Dragonling has not grown up"))
                .thenExecute(() -> assertGrewUp(helper, owner, where))
                .thenSucceed();
    }

    /** Forty minutes' worth at once, at the top grade's worth here. */
    private static void fedToTheEnd(GameTestHelper helper) {
        var owner = player(helper);
        var dragonling = hatch(helper, HERE, owner.getUUID());
        var where = dragonling.blockPosition();
        long needed = Growth.energyToFinish(dragonling.ticksToGrow(), VoidworksConfig.dragonlingFeedEnergy());
        int grade = grade(helper);
        hold(owner, SiphonMode.EMPTY, grade, Math.toIntExact(needed / VoidEnergy.BASE + 5));
        dragonling.interact(owner, InteractionHand.MAIN_HAND, dragonling.position());
        helper.assertTrue(dragonling.isRemoved(), "fed all it needs, the Dragonling has " + dragonling.ticksToGrow() + " ticks to grow");
        helper.assertTrue(VoidSiphonItem.heldMotes(owner.getMainHandItem()).equals(SiphonMotes.EMPTY.with(grade, 5)),
                "feeding took more than growing up needed: the Siphon holds " + VoidSiphonItem.heldMotes(owner.getMainHandItem()));
        assertGrewUp(helper, owner.getUUID(), where);
        helper.succeed();
    }

    private static void unownedNeverGrows(GameTestHelper helper) {
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
        dragonling.setTicksToGrow(5);
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(dragonling.isAlive() && !dragonling.grown(), "a Dragonling no player owns grew up"))
                .thenSucceed();
    }

    /** Taken away as a chunk unloading takes it, the presence comes back; shown again and again, there is still one. */
    private static void onePresence(GameTestHelper helper) {
        var owner = UUID.randomUUID();
        var dragonling = hatch(helper, HERE, owner);
        dragonling.setTicksToGrow(1);
        var server = helper.getLevel().getServer();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(dragonling.isRemoved(), "the Dragonling has not grown up"))
                .thenExecute(() -> {
                    var dragon = record(helper, owner);
                    var first = DragonPresences.presence(server, dragon.id()).orElseThrow();
                    first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    helper.assertTrue(SavedVoidDragons.of(server).get(dragon.id()).equals(Optional.of(dragon)), "taking the presence away lost the dragon");
                    DragonPresences.show(server);
                    DragonPresences.show(server);
                    var shown = presences(helper, dragon);
                    helper.assertTrue(shown.size() == 1, "the dragon shows " + shown.size() + " presences");
                    helper.assertTrue(shown.getFirst() != first, "the presence taken away is still shown");
                })
                .thenSucceed();
    }

    private static void nothingHurtsAVoidDragon(GameTestHelper helper) {
        var owner = UUID.randomUUID();
        var dragonling = hatch(helper, HERE, owner);
        dragonling.setTicksToGrow(1);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(dragonling.isRemoved(), "the Dragonling has not grown up"))
                .thenExecute(() -> {
                    var presence = DragonPresences.presence(helper.getLevel().getServer(), record(helper, owner).id()).orElseThrow();
                    float health = presence.getHealth();
                    var sources = helper.getLevel().damageSources();
                    for (var source : List.of(sources.generic(), sources.magic(), sources.inFire(), sources.playerAttack(player(helper)))) {
                        helper.hurt(presence, source, 1000.0F);
                        helper.assertTrue(presence.isAlive() && presence.getHealth() == health, source.getMsgId() + " hurt the Void Dragon");
                    }
                })
                .thenSucceed();
    }

    /** Grown: one record its owner owns, at full health, resting where it grew, shown by one grown presence there. */
    private static void assertGrewUp(GameTestHelper helper, UUID owner, BlockPos where) {
        var dragon = record(helper, owner);
        helper.assertTrue(dragon.health() == VoidDragonRecord.FULL_HEALTH, "the Void Dragon grew up at " + dragon.health() + " health");
        helper.assertTrue(dragon.rest().pos().equals(where) && dragon.rest().dimension().equals(helper.getLevel().dimension().identifier()),
                "the Void Dragon rests at " + dragon.rest() + ", not where it grew, " + where);
        var shown = presences(helper, dragon);
        helper.assertTrue(shown.size() == 1, "the Void Dragon shows " + shown.size() + " presences");
        var presence = shown.getFirst();
        helper.assertTrue(presence.grown() && presence.owner().equals(Optional.of(owner)), "its presence is not its owner's grown dragon");
        helper.assertTrue(presence.blockPosition().closerThan(where, 2.0), "its presence is at " + presence.blockPosition() + ", not where it grew");
        helper.assertTrue(presence.getName().equals(Component.translatable("entity.voidworks.void_dragon")), "a grown dragon is called " + presence.getName());
        helper.assertTrue(!presence.shouldBeSaved(), "a presence would be saved with its chunk");
        helper.assertTrue(!presence.canUsePortal(false) && !presence.canBeLeashed(), "a presence could be led off, showing its dragon twice");
    }

    private static VoidDragonRecord record(GameTestHelper helper, UUID owner) {
        var owned = SavedVoidDragons.of(helper.getLevel().getServer()).all().stream().filter(dragon -> dragon.owner().equals(owner)).toList();
        helper.assertTrue(owned.size() == 1, "its owner has " + owned.size() + " Void Dragons, not one");
        return owned.getFirst();
    }

    /** Every presence of {@code dragon} in the level, wherever it is. */
    private static List<VoidDragon> presences(GameTestHelper helper, VoidDragonRecord dragon) {
        return List.copyOf(helper.getLevel().getEntities(VoidworksEntities.VOID_DRAGON.get(),
                presence -> presence.dragonId().equals(Optional.of(dragon.id()))));
    }

    private static VoidDragon hatch(GameTestHelper helper, BlockPos pos, UUID owner) {
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), pos);
        dragonling.hatchFor(owner);
        return dragonling;
    }

    private static void assertUnfed(GameTestHelper helper, VoidDragon dragonling, ServerPlayer player, long before, Map<Integer, Integer> held,
            String why) {
        helper.assertTrue(dragonling.ticksToGrow() == before, why + " fed the Dragonling: " + dragonling.ticksToGrow() + " ticks to grow, not " + before);
        helper.assertTrue(VoidSiphonItem.heldMotes(player.getMainHandItem()).byGrade().equals(held),
                why + " left the Siphon holding " + VoidSiphonItem.heldMotes(player.getMainHandItem()));
    }

    /** Puts a Void Siphon in {@code player}'s main hand, set to {@code mode}, holding {@code count} motes of {@code grade}. */
    private static void hold(ServerPlayer player, SiphonMode mode, int grade, int count) {
        var siphon = new ItemStack(VoidworksItems.DRACONIC_VOID_SIPHON.get());
        siphon.set(VoidworksItems.SIPHON_MODE.get(), mode);
        siphon.set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(grade, count));
        player.setItemInHand(InteractionHand.MAIN_HAND, siphon);
    }

    /** A player on the platform, not in the level, so no Dragonling follows them. */
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dragon-owner"));
        var feet = helper.absoluteVec(Vec3.atBottomCenterOf(THERE));
        player.setPos(feet.x, feet.y, feet.z);
        return player;
    }

    /** @return the grade one above the Void Pressure at {@link #HERE}, worth {@link VoidEnergy#BASE} a mote there */
    private static int grade(GameTestHelper helper) {
        return DisplacerTests.pressure(helper, HERE) + 1;
    }
}
