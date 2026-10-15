// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.gallery.GalleryPack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The Gallery pack (ADR 0008), through {@link GalleryCheck}, as FactoryWorks #691's shared check will
 * run it: offered and off, its build running every command without failing, and each scene's key
 * block where it stands. Beyond the build, the Void Dragon scene's dragon sets off from its
 * reservoir for the other Well, and building the scene again leaves one dragon. Each test turns the
 * pack on, which reloads the server's data, and off again, passing or failing, so each runs in a
 * batch of its own.
 */
final class GalleryTests {

    private static final Identifier BUILD = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "gallery/build");
    private static final Identifier VOID_DRAGON = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "gallery/void_dragon");

    /** Where the gallery is run from: the platform's south-west corner, its plots spilling north and east of the platform. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** The Void Dragon scene's two Well cores, from where it is run: the reservoir and the Well short of its target. */
    private static final BlockPos RESERVOIR = new BlockPos(8, 2, -4);
    private static final BlockPos SHORT = new BlockPos(20, 2, -4);
    /** The Void Dragon scene's plot, from where it is run, its floor included. */
    private static final BlockPos PLOT_FROM = new BlockPos(2, -1, -12);
    private static final BlockPos PLOT_TO = new BlockPos(25, 15, 0);

    /** Two reloads, the build, and the dragon's first stop, with room to spare. */
    private static final int TICKS = 1_200;

    private GalleryTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.testOnItsOwn("the_gallery_pack_is_offered_and_off_and_its_build_lays_every_scene_with_no_failing_command", TICKS,
                GalleryTests::builds);
        tests.testOnItsOwn("the_void_dragon_scenes_dragon_sets_off_from_its_reservoir_for_the_other_well_and_building_it_again_leaves_one", TICKS,
                GalleryTests::dragonCarries);
    }

    private static void builds(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = operator(helper);
        GalleryCheck.assertOfferedAndOff(helper, GalleryPack.ID);
        clearAwayIfItFails(helper, source);
        var on = GalleryCheck.select(server, GalleryPack.ID, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), "the Gallery pack is not on yet"))
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, BUILD);
                    GalleryCheck.assertKeyBlocks(helper, helper.absolutePos(ORIGIN),
                            Map.of(RESERVOIR, VoidworksBlocks.VOID_WELL.get(), SHORT, VoidworksBlocks.VOID_WELL.get()));
                    off.set(clearAway(helper, source));
                })
                .thenWaitUntil(() -> assertOff(helper, off))
                .thenSucceed();
    }

    private static void dragonCarries(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = operator(helper);
        var owner = source.getPlayer().getUUID();
        var reservoir = Place.of(helper.getLevel(), helper.absolutePos(ORIGIN).offset(RESERVOIR));
        var shortWell = Place.of(helper.getLevel(), helper.absolutePos(ORIGIN).offset(SHORT));
        clearAwayIfItFails(helper, source);
        var on = GalleryCheck.select(server, GalleryPack.ID, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), "the Gallery pack is not on yet"))
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, VOID_DRAGON))
                .thenWaitUntil(() -> {
                    var dragon = only(helper, owner);
                    helper.assertTrue(dragon.rest().equals(reservoir) && dragon.trip().map(Trip::destination).equals(Optional.of(shortWell)),
                            "the scene's dragon is " + dragon + ", not setting off from the reservoir for the other Well");
                })
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, VOID_DRAGON);
                    var dragon = only(helper, owner);
                    helper.assertTrue(dragon.rest().equals(reservoir) && dragon.trip().isEmpty(),
                            "building the scene again left " + dragon + ", not a dragon resting at the reservoir");
                    off.set(clearAway(helper, source));
                })
                .thenWaitUntil(() -> assertOff(helper, off))
                .thenSucceed();
    }

    /** Waits for the reload that turns the pack off, which a step failing before it never starts. */
    private static void assertOff(GameTestHelper helper, AtomicReference<CompletableFuture<Void>> off) {
        helper.assertTrue(off.get() != null && off.get().isDone(), "the Gallery pack is not off yet");
    }

    /** @return {@code owner}'s one dragon; fails the test unless they have exactly one */
    private static VoidDragonRecord only(GameTestHelper helper, UUID owner) {
        var dragons = SavedVoidDragons.of(helper.getLevel().getServer()).all().stream().filter(dragon -> dragon.owner().equals(owner)).toList();
        helper.assertTrue(dragons.size() == 1, "the runner has " + dragons.size() + " dragons, not one: " + dragons);
        return dragons.getFirst();
    }

    /**
     * Takes away what the scene built, its dragons and its blocks, the Wells' records going with their
     * cores, and turns the Gallery pack off.
     *
     * @return the reload, done once the pack is off
     */
    private static CompletableFuture<Void> clearAway(GameTestHelper helper, CommandSourceStack source) {
        var box = CommandTests.coordinates(helper, ORIGIN.offset(PLOT_FROM)) + " " + CommandTests.coordinates(helper, ORIGIN.offset(PLOT_TO));
        var commands = helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(source, "voidworks dragon remove " + box);
        commands.performPrefixedCommand(source, "fill " + box + " minecraft:air");
        return GalleryCheck.select(helper.getLevel().getServer(), GalleryPack.ID, false);
    }

    /**
     * Clears away if the test fails, which skips its own last steps, so a dragon it built carries on
     * and the pack stays on under the batches after it.
     */
    private static void clearAwayIfItFails(GameTestHelper helper, CommandSourceStack source) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
            }

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                clearAway(helper, source);
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
            }
        });
    }

    /**
     * An operator player of its own, standing where the gallery is run from, whose source runs it there.
     * It is NeoForge's fake player, as {@link CommandTests}' is.
     */
    private static CommandSourceStack operator(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "gallery-visitor"));
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(ORIGIN));
        player.setPos(feet.x, feet.y, feet.z);
        return player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS).withPosition(feet);
    }
}
