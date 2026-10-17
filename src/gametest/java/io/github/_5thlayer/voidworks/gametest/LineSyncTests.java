// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * A dragon's Line is in its synced data, so a client, which tints it by its Line, knows it: a
 * Dragonling's, which it keeps across a save, and a grown dragon's presence's, from its record.
 */
final class LineSyncTests {

    /** Where the dragons rest, on no Well, and the Dragonlings hatch. */
    private static final BlockPos HERE = new BlockPos(4, 2, 4);

    private LineSyncTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("line_sync_a_dragonling_syncs_its_line_and_keeps_it_across_a_save", 1, LineSyncTests::dragonlings);
        tests.test("line_sync_a_presence_syncs_its_dragons_line", 1, LineSyncTests::presences);
    }

    private static void dragonlings(GameTestHelper helper) {
        for (var line : Line.values()) {
            var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
            dragonling.hatchFor(UUID.randomUUID(), line);
            assertSynced(helper, dragonling, line, "Dragonling");
            var reloaded = read(helper, saved(helper, dragonling));
            helper.assertTrue(reloaded.line() == line, "a " + line + " Dragonling reloads of the " + reloaded.line() + " line");
            assertSynced(helper, reloaded, line, "reloaded Dragonling");
            dragonling.discard();
        }
        helper.succeed();
    }

    private static void presences(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dragons = SavedVoidDragons.of(server);
        var here = Place.of(helper.getLevel(), helper.absolutePos(HERE));
        for (var line : Line.values()) {
            var id = dragons.add(UUID.randomUUID(), line, here).id();
            DragonPresences.show(server);
            assertSynced(helper, DragonPresences.presence(server, id).orElseThrow(), line, "presence");
            dragons.remove(id);
            DragonPresences.show(server);
        }
        helper.succeed();
    }

    /** {@code dragon}'s Line is {@code line}, and a client given its synced data, as one joining is, sees it so. */
    private static void assertSynced(GameTestHelper helper, VoidDragon dragon, Line line, String what) {
        helper.assertTrue(dragon.line() == line, "the " + line + " " + what + " is of the " + dragon.line() + " line");
        var client = VoidworksEntities.VOID_DRAGON.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        var values = dragon.getEntityData().getNonDefaultValues();
        if (values != null) {
            client.getEntityData().assignValues(values);
        }
        helper.assertTrue(client.line() == line, "a client sees the " + line + " " + what + " of the " + client.line() + " line");
    }

    private static CompoundTag saved(GameTestHelper helper, VoidDragon dragonling) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        dragonling.saveWithoutId(output);
        return output.buildResult();
    }

    private static VoidDragon read(GameTestHelper helper, CompoundTag saved) {
        var reloaded = VoidworksEntities.VOID_DRAGON.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        return reloaded;
    }
}
