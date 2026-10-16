// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Layout;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The Gallery pack (libworks ADR 0010), through libworks' {@link GalleryCheck}: offered and off, its
 * build running every command without failing, and each scene's key block where it stands. Beyond
 * the build, the Void Dragon scene's dragon sets off from its reservoir for the other Well, and
 * building the scene again leaves one dragon; and a scene's sign, clicked, builds it again. What a
 * scene's machines do is the mechanics' own game tests' to check, never the Gallery's. Each test turns the pack on, which reloads the server's
 * data, and off again, passing or failing, so each runs in a batch of its own.
 */
final class GalleryTests {

    private static final Identifier VOID_DRAGON = GalleryPack.scene(Voidworks.MOD_ID, "void_dragon");
    private static final Identifier VOID_CRUCIBLE = GalleryPack.scene(Voidworks.MOD_ID, "void_crucible");

    /** Three scenes a row, each row 16 blocks north of the last, as {@code gallery/build} lays them. */
    private static final Layout ROWS = new Layout(3, 16);

    /** Where the gallery is run from: the platform's south-west corner, its plots spilling north and east of the platform. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** The Void Dragon scene's two Well cores, from where it is run: the reservoir and the Well short of its target. */
    private static final BlockPos RESERVOIR = new BlockPos(8, 2, -4);
    private static final BlockPos SHORT = new BlockPos(20, 2, -4);
    /** The Matter Link scene's provider hatch, from where it is run, on its west Well's south wall. */
    private static final BlockPos PROVIDER = new BlockPos(9, 2, -3);
    /** Where a scene's sign stands, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(14, 0, 0);

    /** A test of one scene, run on its own from where the gallery is run, loads only the first plot. */
    private static final int ONE_SCENE = 1;

    /** Two reloads, the build, and the dragon's first stop, with room to spare. */
    private static final int TICKS = 1_200;

    private GalleryTests() {
    }

    /** The scenes in the order {@code build} lays them, each with a key block and where, from where the scene is run, its function places it. */
    private static List<Scene> scenes() {
        return List.of(
                new Scene("void_dragon", RESERVOIR, VoidworksBlocks.VOID_WELL.get()),
                new Scene("overworld_displacer", new BlockPos(14, 2, -4), VoidworksBlocks.OVERWORLD_DISPLACER.get()),
                // The game tests run in the Overworld, so the End and Nether scenes show their
                // Displacers as item displays, over a pedestal behind their signs.
                new Scene("end_displacer", SIGN, Blocks.OAK_SIGN),
                new Scene("nether_displacer", SIGN, Blocks.OAK_SIGN),
                new Scene("density", new BlockPos(20, 1, -6), VoidworksBlocks.OVERWORLD_DISPLACER.get()),
                new Scene("voidstone_networks", new BlockPos(5, 0, -6), VoidworksBlocks.VOIDSTONE.get()),
                new Scene("void_siphon_charging", new BlockPos(14, 0, -2), Blocks.CHEST),
                new Scene("void_siphon_emptying", new BlockPos(14, 0, -2), Blocks.CHEST),
                new Scene("dormant_machines", new BlockPos(14, 0, -6), VoidworksBlocks.MUTATION_CHAMBER.get()),
                new Scene("mutation_chamber", new BlockPos(14, 1, -6), VoidworksBlocks.MUTATION_CHAMBER.get()),
                new Scene("unmaking_chamber", new BlockPos(14, 1, -6), VoidworksBlocks.UNMAKING_CHAMBER.get()),
                new Scene("void_crucible", new BlockPos(14, 1, -6), VoidworksBlocks.VOID_CRUCIBLE.get()),
                new Scene("matter_link", PROVIDER, VoidworksBlocks.WELL_HATCH.get()));
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.testOnItsOwn("the_gallery_pack_is_offered_and_off_and_its_build_lays_every_scene_with_no_failing_command", TICKS,
                GalleryTests::builds);
        tests.testOnItsOwn("the_void_dragon_scenes_dragon_sets_off_from_its_reservoir_for_the_other_well_and_building_it_again_leaves_one", TICKS,
                GalleryTests::dragonCarries);
        tests.testOnItsOwn("the_gallery_scenes_sign_clicked_builds_the_scene_again", TICKS,
                GalleryTests::signReplays);
    }

    /**
     * libworks' build check, over every plot, written out to add what one key block a scene can't show: the Void
     * Dragon scene's second Well, the Emptying scene's charged Siphon, and the End and Nether scenes'
     * Displacers shown over their pedestals.
     */
    private static void builds(GameTestHelper helper) {
        var scenes = scenes();
        var at = helper.absolutePos(ORIGIN);
        var functions = helper.getLevel().getServer().getFunctions();
        GalleryCheck.withPack(helper, Voidworks.MOD_ID, ORIGIN, ROWS, scenes.size(), (source, steps) -> steps.thenExecute(() -> {
            GalleryCheck.assertLoads(helper, source, Voidworks.MOD_ID);
            for (var scene : scenes) {
                helper.assertTrue(functions.get(GalleryPack.scene(Voidworks.MOD_ID, scene.name())).isPresent(),
                        "there is no scene function " + scene.name());
            }
            GalleryCheck.assertRuns(helper, source, GalleryPack.build(Voidworks.MOD_ID));
            GalleryCheck.assertKeyBlocks(helper, at, ROWS, scenes);
            var shortWell = sceneOrigin(at, scenes, "void_dragon").offset(SHORT);
            helper.assertTrue(helper.getLevel().getBlockState(shortWell).is(VoidworksBlocks.VOID_WELL.get()),
                    "the Void Dragon scene's second Well is not at " + shortWell.toShortString());
            var emptying = helper.getLevel().getBlockEntity(sceneOrigin(at, scenes, "void_siphon_emptying").offset(14, 0, -2));
            var siphon = emptying instanceof ChestBlockEntity chest ? chest.getItem(13) : ItemStack.EMPTY;
            helper.assertTrue(siphon.is(VoidworksItems.VOID_SIPHON.get()) && VoidSiphonItem.mode(siphon) == SiphonMode.EMPTY
                            && VoidSiphonItem.heldMotes(siphon).total() > 0,
                    "the Emptying scene's chest holds " + siphon + ", not a charged Siphon set to empty");
            for (var name : List.of("end_displacer", "nether_displacer")) {
                var over = sceneOrigin(at, scenes, name).offset(14, 1, -2);
                var shown = helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, new AABB(over));
                helper.assertTrue(shown.size() == 1, "scene " + name + " shows " + shown.size() + " Displacers over its pedestal, not one");
            }
        })).thenSucceed();
    }

    private static void dragonCarries(GameTestHelper helper) {
        var reservoir = Place.of(helper.getLevel(), helper.absolutePos(ORIGIN).offset(RESERVOIR));
        var shortWell = Place.of(helper.getLevel(), helper.absolutePos(ORIGIN).offset(SHORT));
        oneScene(helper, (source, steps) -> {
            var owner = source.getPlayer().getUUID();
            steps.thenExecute(() -> GalleryCheck.assertRuns(helper, source, VOID_DRAGON))
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
                    });
        });
    }

    /** A scene's sign, clicked, builds the scene again where it stands: here the Crucible, taken away. */
    private static void signReplays(GameTestHelper helper) {
        var crucible = helper.absolutePos(ORIGIN).offset(14, 1, -6);
        var sign = helper.absolutePos(ORIGIN).offset(SIGN);
        oneScene(helper, (source, steps) -> steps.thenExecute(() -> {
            GalleryCheck.assertRuns(helper, source, VOID_CRUCIBLE);
            helper.getLevel().removeBlock(crucible, false);
            helper.assertTrue(helper.getLevel().getBlockEntity(sign) instanceof SignBlockEntity entity
                            && entity.executeClickCommandsIfPresent(helper.getLevel(), source.getPlayer(), sign, true),
                    "the scene's sign runs no command when clicked");
            helper.assertTrue(helper.getLevel().getBlockState(crucible).is(VoidworksBlocks.VOID_CRUCIBLE.get()),
                    "clicking the sign did not build the Crucible again where it stood");
            var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.of(GalleryCheck.plot(helper.absolutePos(ORIGIN))));
            helper.assertTrue(loose.isEmpty(), "building the scene again left items loose: " + loose.stream().map(ItemEntity::getItem).toList());
        }));
    }

    /** Where the scene named {@code name} is run from, as {@code build} lays {@code scenes} from {@code at}, absolute. */
    private static BlockPos sceneOrigin(BlockPos at, List<Scene> scenes, String name) {
        for (int index = 0; index < scenes.size(); index++) {
            if (scenes.get(index).name().equals(name)) {
                return ROWS.sceneOrigin(at, index);
            }
        }
        throw new IllegalArgumentException("there is no scene " + name);
    }

    /** A test of a scene run on its own from where the gallery is run, on the first plot, in libworks' frame: passing once the pack is off again. */
    private static void oneScene(GameTestHelper helper, BiConsumer<CommandSourceStack, GameTestSequence> steps) {
        GalleryCheck.withPack(helper, Voidworks.MOD_ID, ORIGIN, ROWS, ONE_SCENE, steps).thenSucceed();
    }

    /** @return {@code owner}'s one dragon; fails the test unless they have exactly one */
    private static VoidDragonRecord only(GameTestHelper helper, UUID owner) {
        var dragons = SavedVoidDragons.of(helper.getLevel().getServer()).all().stream().filter(dragon -> dragon.owner().equals(owner)).toList();
        helper.assertTrue(dragons.size() == 1, "the runner has " + dragons.size() + " dragons, not one: " + dragons);
        return dragons.getFirst();
    }
}
