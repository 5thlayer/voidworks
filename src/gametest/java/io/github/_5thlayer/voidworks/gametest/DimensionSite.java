// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksCapabilities;
import io.github._5thlayer.voidworks.displacer.DisplacerBlockEntity;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A site outside the Overworld, a chunk of its own, where a Displacer of that dimension stands for
 * a test: positions are relative to {@code corner}, as a test's are to its structure. The game test
 * server ticks no block entity outside the Overworld, so a test ticks the Displacer itself, the site
 * catches what it drops, and it is cleared afterwards: every block within {@code reach} of the
 * Displacer, and every block the test set.
 */
record DimensionSite(GameTestHelper helper, ServerLevel level, BlockPos corner, BlockPos displacer, int reach,
        List<Item> dropped, Set<BlockPos> touched) {

    /**
     * Runs {@code body} at the site in {@code dimension} whose corner is {@code corner}, its
     * Displacer at {@code displacer}, and clears it afterwards; the test succeeds if the body returns.
     */
    static void run(GameTestHelper helper, ResourceKey<Level> dimension, BlockPos corner, BlockPos displacer, int reach,
            Consumer<DimensionSite> body) {
        var level = helper.getLevel().getServer().getLevel(dimension);
        var site = new DimensionSite(helper, level, corner, displacer, reach, new ArrayList<>(), new HashSet<>());
        int chunkX = corner.getX() >> 4;
        int chunkZ = corner.getZ() >> 4;
        // Such a level loads no entities here, so its drops are caught as they are added.
        Consumer<EntityJoinLevelEvent> drops = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof ItemEntity item) {
                site.dropped().add(item.getItem().getItem());
                event.setCanceled(true);
            }
        };
        level.setChunkForced(chunkX, chunkZ, true);
        NeoForge.EVENT_BUS.addListener(drops);
        try {
            body.accept(site);
        } finally {
            NeoForge.EVENT_BUS.unregister(drops);
            site.clear();
            level.setChunkForced(chunkX, chunkZ, false);
        }
        helper.succeed();
    }

    BlockPos abs(BlockPos pos) {
        return corner.offset(pos);
    }

    BlockState state(BlockPos pos) {
        return level.getBlockState(abs(pos));
    }

    void set(BlockPos pos, BlockState state) {
        touched.add(abs(pos));
        level.setBlockAndUpdate(abs(pos), state);
    }

    /** Places {@code state}, a Displacer, at the site's Displacer position, with a Zone of {@code zone}. */
    <T extends DisplacerBlockEntity> T place(BlockState state, int zone, Class<T> type) {
        set(displacer, state);
        var entity = type.cast(level.getBlockEntity(abs(displacer)));
        entity.setZone(zone);
        return entity;
    }

    /** Feeds it {@code count} copper blocks, of Density 1. */
    void feed(DisplacerBlockEntity entity, int count) {
        entity.inventory().set(DisplacerBlockEntity.INPUT, ItemResource.of(Items.COPPER_BLOCK), count);
    }

    /** Pays its whole cost, as a Siphon emptied into it does, so it wakes on its next tick. */
    void payInFull(Dormancy.Holder machine) {
        int local = pressure(displacer);
        int grade = local + 3;
        var dormancy = machine.dormancy();
        var store = new MoteStore().add(grade, (int) (dormancy.cost() / VoidEnergy.release(grade, local).orElseThrow()) + 1);
        dormancy.payFrom(level, store, abs(displacer).getCenter());
        assertTrue(dormancy.paid() == dormancy.cost(), "the Displacer was paid " + dormancy.paid());
    }

    /** Ticks the Displacer as the server would, {@code ticks} times or until it is gone. */
    void tick(DisplacerBlockEntity entity, int ticks) {
        for (int i = 0; i < ticks && !entity.isRemoved(); i++) {
            entity.serverTick(level);
        }
    }

    boolean dormantLook() {
        var state = state(displacer);
        return state.hasProperty(Dormancy.DORMANT) && state.getValue(Dormancy.DORMANT);
    }

    int pressure(BlockPos pos) {
        return VoidPressure.at(level, abs(pos));
    }

    void insert(BlockPos pos, int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = VoidstoneNetworks.of(level).deposit(abs(pos), MoteResource.of(grade), count, transaction);
            assertTrue(inserted == count, "the network at " + pos + " took " + inserted + " of " + count + " motes");
            transaction.commit();
        }
    }

    /** The motes of the network at {@code pos}, in every store of it together. */
    Map<Integer, Integer> motes(BlockPos pos) {
        return DisplacerTests.networkMotes(level, abs(pos));
    }

    void assertVoidstone(BlockPos pos) {
        assertTrue(state(pos).is(VoidworksBlocks.VOIDSTONE.get()), "there is " + state(pos) + " at " + pos + ", not Voidstone");
    }

    /** Voidstone with the look of {@code kind}, as everything placed in that kind's dimension has. */
    void assertVoidstone(BlockPos pos, HarvestKind kind) {
        assertVoidstone(pos);
        var look = state(pos).getValue(VoidstoneBlock.KIND);
        assertTrue(look == kind, "the Voidstone at " + pos + " looks like " + look + ", not " + kind);
    }

    void assertTrue(boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    /** Clears every block a test may have set, leaving the site as it found it: air. */
    void clear() {
        var around = abs(displacer);
        for (var pos : BlockPos.betweenClosed(around.offset(-reach, -reach, -reach), around.offset(reach, reach, reach))) {
            touched.add(pos.immutable());
        }
        for (var pos : touched) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }
}
