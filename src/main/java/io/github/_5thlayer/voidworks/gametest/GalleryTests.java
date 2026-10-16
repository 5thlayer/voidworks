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
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.gallery.GalleryPack;
import io.github._5thlayer.voidworks.network.MoteNetwork;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
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
    private static final Identifier DENSITY = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "gallery/density");
    private static final Identifier DORMANT_MACHINES = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "gallery/dormant_machines");
    private static final Identifier VOID_CRUCIBLE = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "gallery/void_crucible");

    /** How far east each scene's plot is of the one before it in its row, and how far north each row is of the last. */
    private static final int PLOT_WIDTH = 24;
    private static final int ROW_DEPTH = 16;
    /** How many scenes a row holds. */
    private static final int ROW = 3;
    /** How many scenes {@code build} lays, the Void Dragon's first. */
    private static final int SCENES = 12;

    /** Where the gallery is run from: the platform's south-west corner, its plots spilling north and east of the platform. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** The Void Dragon scene's two Well cores, from where it is run: the reservoir and the Well short of its target. */
    private static final BlockPos RESERVOIR = new BlockPos(8, 2, -4);
    private static final BlockPos SHORT = new BlockPos(20, 2, -4);
    /** Where a scene's sign stands, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(14, 0, 0);
    /** The Density scene's four Displacers, from where it is run, coal's to diamond's, each on its pad of Voidstone. */
    private static final int[] DENSITY_XS = {5, 10, 15, 20};
    /** A scene's plot, from where it is run, its floor included. */
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
        tests.testOnItsOwn("the_gallery_density_scenes_displacers_each_yield_their_densitys_motes", TICKS,
                GalleryTests::densityYields);
        tests.testOnItsOwn("the_gallery_dormant_machines_scenes_chamber_soaks_its_cost_and_wakes", TICKS,
                GalleryTests::dormantWakes);
        tests.testOnItsOwn("the_gallery_scenes_sign_clicked_builds_the_scene_again", TICKS,
                GalleryTests::signReplays);
    }

    private static void builds(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = operator(helper);
        GalleryCheck.assertOfferedAndOff(helper, GalleryPack.ID);
        forceLoad(helper, true);
        clearAwayIfItFails(helper, source);
        var on = GalleryCheck.select(server, GalleryPack.ID, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), "the Gallery pack is not on yet"))
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, BUILD);
                    GalleryCheck.assertKeyBlocks(helper, helper.absolutePos(ORIGIN), Map.ofEntries(
                            Map.entry(RESERVOIR, VoidworksBlocks.VOID_WELL.get()),
                            Map.entry(SHORT, VoidworksBlocks.VOID_WELL.get()),
                            Map.entry(scene(1, 14, 2, -6), VoidworksBlocks.OVERWORLD_DISPLACER.get()),
                            // The game tests run in the Overworld, so the End and Nether scenes show
                            // their Displacers as item displays, over a pedestal behind their signs.
                            Map.entry(scene(2, SIGN), Blocks.OAK_SIGN),
                            Map.entry(scene(3, SIGN), Blocks.OAK_SIGN),
                            Map.entry(scene(4, 20, 1, -6), VoidworksBlocks.OVERWORLD_DISPLACER.get()),
                            Map.entry(scene(5, 5, 0, -6), VoidworksBlocks.VOIDSTONE.get()),
                            Map.entry(scene(6, 14, 0, -2), Blocks.CHEST),
                            Map.entry(scene(7, 14, 0, -2), Blocks.CHEST),
                            Map.entry(scene(8, 14, 0, -6), VoidworksBlocks.MUTATION_CHAMBER.get()),
                            Map.entry(scene(9, 14, 1, -6), VoidworksBlocks.MUTATION_CHAMBER.get()),
                            Map.entry(scene(10, 14, 1, -6), VoidworksBlocks.UNMAKING_CHAMBER.get()),
                            Map.entry(scene(11, 14, 1, -6), VoidworksBlocks.VOID_CRUCIBLE.get())));
                    var emptying = helper.getLevel().getBlockEntity(helper.absolutePos(ORIGIN.offset(scene(7, 14, 0, -2))));
                    var siphon = emptying instanceof ChestBlockEntity chest ? chest.getItem(13) : ItemStack.EMPTY;
                    helper.assertTrue(siphon.is(VoidworksItems.VOID_SIPHON.get()) && VoidSiphonItem.mode(siphon) == SiphonMode.EMPTY
                                    && VoidSiphonItem.heldMotes(siphon).total() > 0,
                            "the Emptying scene's chest holds " + siphon + ", not a charged Siphon set to empty");
                    for (int index : new int[] {2, 3}) {
                        var over = helper.absolutePos(ORIGIN.offset(scene(index, 14, 1, -2)));
                        var shown = helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, new AABB(over));
                        helper.assertTrue(shown.size() == 1, "scene " + index + " shows " + shown.size() + " Displacers over its pedestal, not one");
                    }
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

    /**
     * The Density scene: each Displacer, fed one block, harvests the stone beside it, so its network
     * holds the motes one harvest of that Density yields.
     */
    private static void densityYields(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = operator(helper);
        clearAwayIfItFails(helper, source);
        var on = GalleryCheck.select(server, GalleryPack.ID, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), "the Gallery pack is not on yet"))
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, DENSITY))
                .thenWaitUntil(() -> {
                    for (int density = 1; density <= DENSITY_XS.length; density++) {
                        var face = helper.absolutePos(ORIGIN).offset(DENSITY_XS[density - 1], 1, -5);
                        var framed = helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(face));
                        helper.assertTrue(framed.size() == 1 && !framed.getFirst().getItem().isEmpty(),
                                "Density " + density + "'s Displacer shows " + framed + " on its face, not one framed block");
                        var stone = helper.absolutePos(ORIGIN).offset(DENSITY_XS[density - 1], 0, -6);
                        long motes = VoidstoneNetworks.of(helper.getLevel()).at(stone).map(MoteNetwork::total).orElse(0L);
                        long yield = VoidworksConfig.motes(density);
                        helper.assertTrue(motes == yield, "Density " + density + "'s network holds " + motes + " motes, not " + yield);
                    }
                })
                .thenExecute(() -> off.set(clearAway(helper, source)))
                .thenWaitUntil(() -> assertOff(helper, off))
                .thenSucceed();
    }

    /** The Dormant machines scene: its Chamber soaks its cost from the motes along the line, and wakes. */
    private static void dormantWakes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = operator(helper);
        var chamber = helper.absolutePos(ORIGIN).offset(14, 0, -6);
        clearAwayIfItFails(helper, source);
        var on = GalleryCheck.select(server, GalleryPack.ID, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), "the Gallery pack is not on yet"))
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, DORMANT_MACHINES))
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBlockEntity(chamber) instanceof Dormancy.Holder machine
                        && machine.dormancy().awake(), "the scene's Chamber is not awake yet"))
                .thenExecute(() -> off.set(clearAway(helper, source)))
                .thenWaitUntil(() -> assertOff(helper, off))
                .thenSucceed();
    }

    /** A scene's sign, clicked, builds the scene again where it stands: here the Crucible, taken away. */
    private static void signReplays(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = operator(helper);
        var crucible = helper.absolutePos(ORIGIN).offset(14, 1, -6);
        var sign = helper.absolutePos(ORIGIN).offset(SIGN);
        clearAwayIfItFails(helper, source);
        var on = GalleryCheck.select(server, GalleryPack.ID, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), "the Gallery pack is not on yet"))
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, VOID_CRUCIBLE);
                    helper.getLevel().removeBlock(crucible, false);
                    helper.assertTrue(helper.getLevel().getBlockEntity(sign) instanceof SignBlockEntity entity
                                    && entity.executeClickCommandsIfPresent(helper.getLevel(), source.getPlayer(), sign, true),
                            "the scene's sign runs no command when clicked");
                    helper.assertTrue(helper.getLevel().getBlockState(crucible).is(VoidworksBlocks.VOID_CRUCIBLE.get()),
                            "clicking the sign did not build the Crucible again where it stood");
                    var plot = AABB.encapsulatingFullBlocks(helper.absolutePos(ORIGIN.offset(PLOT_FROM)), helper.absolutePos(ORIGIN.offset(PLOT_TO)));
                    var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, plot);
                    helper.assertTrue(loose.isEmpty(), "building the scene again left items loose: " + loose.stream().map(ItemEntity::getItem).toList());
                    off.set(clearAway(helper, source));
                })
                .thenWaitUntil(() -> assertOff(helper, off))
                .thenSucceed();
    }

    /** @return the position, from where the gallery is run, of {@code pos} in scene {@code index}, the Void Dragon's 0 */
    private static BlockPos scene(int index, BlockPos pos) {
        return pos.east(PLOT_WIDTH * (index % ROW)).north(ROW_DEPTH * (index / ROW));
    }

    /**
     * Keeps the chunks the whole gallery stands in loaded, or not, as a player standing where it is
     * run would: the game test server loads only those near the test.
     */
    private static void forceLoad(GameTestHelper helper, boolean load) {
        var from = ChunkPos.containing(helper.absolutePos(ORIGIN.offset(PLOT_FROM).north(ROW_DEPTH * ((SCENES - 1) / ROW))));
        var to = ChunkPos.containing(helper.absolutePos(ORIGIN.offset(scene(ROW - 1, PLOT_TO))));
        for (int x = Math.min(from.x(), to.x()); x <= Math.max(from.x(), to.x()); x++) {
            for (int z = Math.min(from.z(), to.z()); z <= Math.max(from.z(), to.z()); z++) {
                helper.getLevel().setChunkForced(x, z, load);
            }
        }
    }

    private static BlockPos scene(int index, int x, int y, int z) {
        return scene(index, new BlockPos(x, y, z));
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
        var commands = helper.getLevel().getServer().getCommands();
        for (int index = 0; index < SCENES; index++) {
            // One fill a plot, each within the fill limit.
            var box = CommandTests.coordinates(helper, ORIGIN.offset(scene(index, PLOT_FROM)))
                    + " " + CommandTests.coordinates(helper, ORIGIN.offset(scene(index, PLOT_TO)));
            commands.performPrefixedCommand(source, "voidworks dragon remove " + box);
            commands.performPrefixedCommand(source, "fill " + box + " minecraft:air");
        }
        commands.performPrefixedCommand(source, "kill @e[type=minecraft:item_display,distance=..200]");
        commands.performPrefixedCommand(source, "kill @e[type=minecraft:item_frame,distance=..200]");
        forceLoad(helper, false);
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
