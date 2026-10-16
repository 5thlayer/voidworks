// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Optional;
import java.util.OptionalLong;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.displacer.DisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.DisplacerReport;
import io.github._5thlayer.voidworks.displacer.EndDisplacerBlock;
import io.github._5thlayer.voidworks.displacer.EndDisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.OverworldDisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.ZoneUpgrade;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A Void Displacer's Zone upgrades on a real server: the Overworld's, standing on the platform's
 * stone floor, with a store of motes touching it, and a Dormant End Displacer in the End. A first
 * sneak-use quotes the next level, the same player's second within five seconds commits to it, and
 * the Displacer soaks its price from what touches it before its Zone grows.
 */
final class ZoneUpgradeTests {

    private static final BlockPos DISPLACER = new BlockPos(4, 1, 4);
    private static final BlockPos BELOW = DISPLACER.below();
    /** A Consumer's mote store touching it, east, off the floor it harvests. */
    private static final BlockPos STORE = DISPLACER.east();
    private static final int ZONE = 4;
    /** Long enough to soak a level or two at the configured interval. */
    private static final int TICKS = 300;

    private ZoneUpgradeTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_zone_upgrade_is_quoted_by_a_sneak_use_and_bought_by_the_same_players_second", TICKS, ZoneUpgradeTests::quotedThenBought);
        tests.test("a_zone_upgrade_quote_lapses_after_five_seconds_or_for_another_player", TICKS, ZoneUpgradeTests::quoteLapses);
        tests.test("a_zone_upgrade_its_network_cannot_afford_takes_nothing", TICKS, ZoneUpgradeTests::unaffordable);
        tests.test("a_zone_upgrade_soaking_can_be_cancelled_and_loses_what_it_paid", TICKS, ZoneUpgradeTests::cancelled);
        tests.test("a_zone_upgrade_bought_by_a_spent_displacer_lets_it_harvest_on", 2 * TICKS, ZoneUpgradeTests::spentHarvestsOn);
        tests.test("a_zone_upgrade_costs_its_growth_more_each_level_and_its_levels_survive_a_reload", 2 * TICKS, ZoneUpgradeTests::secondLevel);
        tests.test("a_zone_upgrade_is_not_offered_by_a_dormant_end_displacer", 1, ZoneUpgradeTests::notWhileDormant);
    }

    private static void quotedThenBought(GameTestHelper helper) {
        var displacer = place(helper);
        var store = store(helper, 20);
        long price = upgrade(displacer).price(0);
        var player = sneaking(helper);
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(displacer.upgradePayment().isEmpty(), "the first sneak-use committed to the upgrade, not quoted it");
        helper.assertTrue(motes(helper, store) == 20, "the quote took motes: the store holds " + motes(helper, store));
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(displacer.upgradePayment().isPresent(), "the second sneak-use did not commit to the upgrade");
        helper.succeedWhen(() -> {
            helper.assertTrue(displacer.upgradeLevels() == 1, "it has bought " + displacer.upgradeLevels() + " levels, not 1");
            int step = upgrade(displacer).step();
            helper.assertTrue(displacer.harvestsLeft() == ZONE + step, "it has " + displacer.harvestsLeft() + " harvests left, not " + (ZONE + step));
            long left = 20 - price / VoidEnergy.BASE;
            helper.assertTrue(motes(helper, store) == left, "the store holds " + motes(helper, store) + ", not the " + left + " left after " + price);
            helper.assertTrue(displacer.upgradePayment().isEmpty(), "it still soaks " + displacer.upgradePayment());
        });
    }

    private static void quoteLapses(GameTestHelper helper) {
        var displacer = place(helper);
        store(helper, 20);
        var alex = sneaking(helper);
        var steve = sneaking(helper);
        helper.useBlock(DISPLACER, alex);
        helper.useBlock(DISPLACER, steve);
        helper.useBlock(DISPLACER, alex);
        helper.assertTrue(displacer.upgradePayment().isEmpty(), "Alex's quote was confirmed after Steve's sneak-use took it over");
        helper.startSequence()
                .thenIdle(101)
                .thenExecute(() -> {
                    helper.useBlock(DISPLACER, alex);
                    helper.assertTrue(displacer.upgradePayment().isEmpty(), "a quote was confirmed more than five seconds on");
                    // A plain use in between lets the quote lapse too.
                    alex.setShiftKeyDown(false);
                    helper.useBlock(DISPLACER, alex);
                    alex.setShiftKeyDown(true);
                    helper.useBlock(DISPLACER, alex);
                    helper.assertTrue(displacer.upgradePayment().isEmpty(), "a quote was confirmed after a plain use");
                })
                .thenSucceed();
    }

    private static void unaffordable(GameTestHelper helper) {
        var displacer = place(helper);
        var store = store(helper, 10);
        var player = sneaking(helper);
        helper.useBlock(DISPLACER, player);
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(displacer.upgradePayment().isEmpty(), "it committed to an upgrade its network cannot afford");
        helper.startSequence()
                .thenIdle(2 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(motes(helper, store) == 10, "the store holds " + motes(helper, store) + ", not its 10");
                    helper.assertTrue(displacer.harvestsLeft() == ZONE, "its Zone grew: " + displacer.harvestsLeft() + " harvests left");
                })
                .thenSucceed();
    }

    /** Committed and cancelled in the same tick, before its first soak: it pays nothing and buys nothing. */
    private static void cancelled(GameTestHelper helper) {
        var displacer = place(helper);
        var store = store(helper, 20);
        var player = sneaking(helper);
        helper.useBlock(DISPLACER, player);
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(displacer.upgradePayment().isPresent(), "the second sneak-use did not commit to the upgrade");
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(displacer.upgradePayment().isPresent(), "a sneak-use while it soaks cancelled it without a confirmation");
        helper.useBlock(DISPLACER, player);
        helper.assertTrue(displacer.upgradePayment().isEmpty(), "the confirmed cancel left it soaking");
        helper.startSequence()
                .thenIdle(2 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(motes(helper, store) == 20, "the store holds " + motes(helper, store) + ", not its 20");
                    helper.assertTrue(displacer.harvestsLeft() == ZONE && displacer.upgradeLevels() == 0,
                            "the cancelled upgrade was bought: " + displacer.harvestsLeft() + " harvests left, level " + displacer.upgradeLevels());
                })
                .thenSucceed();
    }

    /**
     * A Zone of one, harvested, with its stone still in its output: it waits to become Voidstone.
     * Upgraded then and its output emptied, it waits for the upgrade instead, and harvests on.
     */
    private static void spentHarvestsOn(GameTestHelper helper) {
        var displacer = place(helper);
        displacer.setZone(1);
        displacer.inventory().set(DisplacerBlockEntity.INPUT, ItemResource.of(Items.COPPER_BLOCK), 1);
        store(helper, 20);
        var player = sneaking(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(displacer.stall(helper.getLevel()).equals(Optional.of(DisplacerBlockEntity.Stall.OUTPUT_UNEMPTIED)),
                        "it is not spent and waiting on its output: " + displacer.stall(helper.getLevel())))
                .thenExecute(() -> {
                    helper.useBlock(DISPLACER, player);
                    helper.useBlock(DISPLACER, player);
                    helper.assertTrue(displacer.upgradePayment().isPresent(), "the spent Displacer did not commit to the upgrade");
                    for (int slot = 1; slot <= DisplacerBlockEntity.OUTPUT_SLOTS; slot++) {
                        displacer.inventory().set(slot, ItemResource.EMPTY, 0);
                    }
                    displacer.inventory().set(DisplacerBlockEntity.INPUT, ItemResource.of(Items.COPPER_BLOCK), 1);
                })
                .thenIdle(1)
                .thenExecute(() -> helper.assertBlockPresent(VoidworksBlocks.OVERWORLD_DISPLACER.get(), DISPLACER))
                .thenWaitUntil(() -> helper.assertTrue(displacer.grown().size() == 2, "it harvested " + displacer.grown().size() + " positions, not 2"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(VoidworksBlocks.OVERWORLD_DISPLACER.get(), DISPLACER);
                    helper.assertBlockPresent(VoidworksBlocks.VOIDSTONE.get(), BELOW);
                })
                .thenSucceed();
    }

    /** The first level bought, the second's price is the first's times its growth, and both survive a reload. */
    private static void secondLevel(GameTestHelper helper) {
        place(helper);
        store(helper, 100);
        var player = sneaking(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.useBlock(DISPLACER, player);
                    helper.useBlock(DISPLACER, player);
                })
                .thenWaitUntil(() -> helper.assertTrue(displacer(helper).upgradeLevels() == 1, "it has not bought its first level"))
                .thenExecute(() -> {
                    var upgrade = upgrade(displacer(helper));
                    long second = upgrade.firstPrice() * upgrade.growth();
                    helper.assertTrue(displacer(helper).nextUpgradePrice().equals(OptionalLong.of(second)),
                            "its next level costs " + displacer(helper).nextUpgradePrice() + ", not " + second);
                    helper.useBlock(DISPLACER, player);
                    helper.useBlock(DISPLACER, player);
                    helper.assertTrue(displacer(helper).upgradePayment().equals(Optional.of(new DisplacerBlockEntity.Payment(0, second))),
                            "it soaks " + displacer(helper).upgradePayment() + ", not the second level's " + second);
                    var reloaded = reload(helper);
                    helper.assertTrue(reloaded.upgradeLevels() == 1, "reloaded, it has bought " + reloaded.upgradeLevels() + " levels, not 1");
                    helper.assertTrue(reloaded.upgradePayment().equals(Optional.of(new DisplacerBlockEntity.Payment(0, second))),
                            "reloaded, it soaks " + reloaded.upgradePayment() + ", not the second level's " + second);
                    var report = DisplacerReport.of(reloaded, helper.getLevel());
                    helper.assertTrue(report.levels() == 1 && report.upgrade().isPresent(), "its report shows " + report);
                })
                .thenWaitUntil(() -> helper.assertTrue(displacer(helper).upgradeLevels() == 2, "it has not bought its second level"))
                .thenExecute(() -> {
                    int step = upgrade(displacer(helper)).step();
                    helper.assertTrue(displacer(helper).harvestsLeft() == ZONE + 2 * step,
                            "it has " + displacer(helper).harvestsLeft() + " harvests left, not " + (ZONE + 2 * step));
                })
                .thenSucceed();
    }

    /** Dormant, a sneak-use quotes nothing however much touches it, so a second commits to nothing. */
    private static void notWhileDormant(GameTestHelper helper) {
        DimensionSite.run(helper, Level.END, new BlockPos(16 * 8, 0, 512), new BlockPos(1, 2, 1), 2, site -> {
            site.set(new BlockPos(1, 1, 1), Blocks.END_STONE.defaultBlockState());
            var displacer = site.place(VoidworksBlocks.END_DISPLACER.get().defaultBlockState().setValue(EndDisplacerBlock.FACING, Direction.DOWN),
                    ZONE, EndDisplacerBlockEntity.class);
            site.set(new BlockPos(1, 2, 2), VoidworksBlocks.VOIDSTONE.get().defaultBlockState());
            site.insert(new BlockPos(1, 2, 2), site.pressure(new BlockPos(1, 2, 1)) + 3, VoidworksConfig.voidstoneCapacity());
            site.assertTrue(!displacer.dormancy().awake(), "the End Displacer was placed awake");
            var player = sneaking(helper);
            var pos = site.abs(new BlockPos(1, 2, 1));
            var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
            var state = site.level().getBlockState(pos);
            state.useWithoutItem(site.level(), player, hit);
            state.useWithoutItem(site.level(), player, hit);
            site.assertTrue(displacer.upgradePayment().isEmpty(), "the Dormant Displacer committed to a Zone upgrade");
        });
    }

    private static OverworldDisplacerBlockEntity place(GameTestHelper helper) {
        helper.setBlock(DISPLACER, VoidworksBlocks.OVERWORLD_DISPLACER.get());
        var displacer = displacer(helper);
        displacer.setZone(ZONE);
        return displacer;
    }

    private static OverworldDisplacerBlockEntity displacer(GameTestHelper helper) {
        return helper.getBlockEntity(DISPLACER, OverworldDisplacerBlockEntity.class);
    }

    /** A store touching it holding {@code count} motes worth one step at the Displacer, {@link VoidEnergy#BASE} each. */
    private static MoteStore store(GameTestHelper helper, int count) {
        var store = new MoteStore().add(DisplacerTests.pressure(helper, DISPLACER) + 1, count);
        SpendTests.placeStore(helper, STORE, store);
        return store;
    }

    private static long motes(GameTestHelper helper, MoteStore store) {
        return VoidSpender.countByGrade(store).values().stream().mapToLong(Integer::longValue).sum();
    }

    private static ZoneUpgrade upgrade(DisplacerBlockEntity displacer) {
        return ZoneUpgrade.of(displacer.getBlockState()).orElseThrow();
    }

    private static Player sneaking(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        return player;
    }

    private static OverworldDisplacerBlockEntity reload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(DISPLACER);
        var state = level.getBlockState(pos);
        var saved = displacer(helper).saveWithFullMetadata(level.registryAccess());
        level.removeBlock(pos, false);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var reloaded = displacer(helper);
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        return reloaded;
    }
}
