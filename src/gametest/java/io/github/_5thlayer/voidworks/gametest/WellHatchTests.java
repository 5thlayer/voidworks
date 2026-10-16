// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.WellHatchBlock;
import io.github._5thlayer.voidworks.well.WellHatchBlockEntity;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Well hatch, on its own, with no dragon: it is placed only against the outer face of a Void Well's
 * wall casing, on any course, and only by the Well's owner, joining that Well; it holds 27 slots in
 * the server's saved data, not in its block, so they survive a reload; a hopper under it empties it
 * and one above fills it, and its owner's hand puts a stack in and takes one out. Its Well no longer
 * whole, it keeps what it holds; its core broken, it is a plain container still; broken itself, it
 * drops what it held.
 */
final class WellHatchTests {

    /** Beside the well's east wall, on its middle course, against the casing west of it. */
    private static final BlockPos HATCH = new BlockPos(7, 3, 4);
    private static final BlockPos HATCH_AGAINST = HATCH.west();

    private static final int TICKS = 200;

    private WellHatchTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_well_hatch_goes_only_against_the_outer_face_of_a_void_well_s_wall_casing_and_joins_its_well", TICKS,
                WellHatchTests::placedOnlyAgainstWallCasing);
        tests.test("a_well_hatch_is_refused_against_another_player_s_void_well", TICKS, WellHatchTests::refusedAgainstAnothersWell);
        tests.test("a_well_hatch_is_emptied_by_a_hopper_under_it_and_filled_by_one_above_it", TICKS, WellHatchTests::hoppers);
        tests.test("a_well_hatch_s_owner_puts_a_stack_in_and_takes_one_out_by_hand_and_anyone_else_is_told_whose_it_is", TICKS,
                WellHatchTests::byHand);
        tests.test("a_well_hatch_keeps_its_contents_in_saved_data_across_a_save_and_reload", TICKS, WellHatchTests::savedContents);
        tests.test("a_well_hatch_keeps_its_contents_while_its_well_is_not_whole_and_is_a_plain_container_once_its_core_is_broken", TICKS,
                WellHatchTests::outlivesItsWell);
        tests.test("a_well_hatch_broken_drops_what_it_held", TICKS, WellHatchTests::brokenDrops);
        tests.test("a_well_hatch_seen_by_a_watcher_has_an_item_face_so_jade_asks_the_server_what_it_holds", 1, WellHatchTests::watcherHasAFace);
        tests.test("a_well_hatch_placed_where_one_was_removed_unbroken_drops_what_that_one_held", TICKS, WellHatchTests::staleContentsDropped);
    }

    private static void placedOnlyAgainstWallCasing(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        var core = WellTests.placeCore(helper, owner);
        var notOnWall = Component.translatable("voidworks.hatch.not_on_wall");
        helper.setBlock(new BlockPos(0, 1, 7), Blocks.STONE);
        var refused = List.of(
                new Against(WellTests.WELL, Direction.NORTH, "the core's face"),
                new Against(new BlockPos(6, 5, 4), Direction.UP, "the top of the rim"),
                new Against(new BlockPos(6, 5, 4), Direction.EAST, "the rim's outer face"),
                new Against(HATCH_AGAINST, Direction.WEST, "a wall's face into the shaft"),
                new Against(new BlockPos(0, 1, 7), Direction.UP, "a block of stone"));
        for (var against : refused) {
            // Above the platform, where the rim stands, nothing says the spot is clear.
            helper.setBlock(against.pos().relative(against.face()), Blocks.AIR);
            owner.overlays.clear();
            helper.assertFalse(placeHatch(helper, owner, against.pos(), against.face()), "a hatch was placed against " + against.what());
            helper.assertTrue(owner.overlays.equals(List.of(notOnWall)), "placing against " + against.what() + " told " + owner.overlays);
        }
        var wellPlace = Place.of(helper.getLevel(), helper.absolutePos(WellTests.WELL));
        var placed = List.of(
                new Against(HATCH_AGAINST, Direction.EAST, "the east wall's middle course"),
                new Against(new BlockPos(6, 2, 3), Direction.EAST, "the east wall's lower course"),
                new Against(new BlockPos(2, 4, 4), Direction.WEST, "the west wall's upper course"),
                new Against(new BlockPos(6, 3, 2), Direction.NORTH, "the north-east corner's north face"),
                new Against(WellTests.WELL.east(), Direction.NORTH, "the casing beside the core"));
        for (var against : placed) {
            helper.assertTrue(placeHatch(helper, owner, against.pos(), against.face()), "no hatch was placed against " + against.what());
            var pos = against.pos().relative(against.face());
            helper.assertTrue(helper.getBlockState(pos).getValue(WellHatchBlock.FACING) == against.face(),
                    "the hatch against " + against.what() + " does not face away from it");
            var hatch = hatch(helper, pos);
            helper.assertTrue(hatch.well().equals(Optional.of(wellPlace)), "the hatch against " + against.what() + " joined " + hatch.well());
            helper.assertTrue(hatch.owner().equals(core.owner()), "the hatch against " + against.what() + " is " + hatch.owner() + "'s");
            helper.assertTrue(hatch.contents().size() == VoidworksConfig.wellHatchSlots() && VoidworksConfig.wellHatchSlots() == 27,
                    "the hatch holds " + hatch.contents().size() + " slots");
        }
        helper.assertTrue(core.whole(helper.getLevel()), "the Well with its hatches is not whole: they are not part of its structure");
        helper.succeed();
    }

    private static void refusedAgainstAnothersWell(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        var core = WellTests.placeCore(helper, owner);
        var other = new WellTests.Listener(helper, "someone-else");
        helper.assertFalse(placeHatch(helper, other, HATCH_AGAINST, Direction.EAST), "another player placed a hatch on the owner's Well");
        var whose = Component.translatable("voidworks.well.owned_by", owner.getGameProfile().name());
        helper.assertTrue(other.overlays.equals(List.of(whose)), "another player was told " + other.overlays + ", not whose Well it is");
        helper.assertTrue(core.owner().isPresent() && core.owner().get().id().equals(owner.getUUID()), "refusing the hatch changed the Well's owner");
        helper.succeed();
    }

    private static void hoppers(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        WellTests.placeCore(helper, owner);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed");
        var above = HATCH.above();
        var below = HATCH.below();
        helper.setBlock(above, Blocks.HOPPER);
        helper.getBlockEntity(above, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        var contents = hatch(helper, HATCH).contents();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(held(contents, Items.COBBLESTONE) == 5,
                        "the hopper above has put " + held(contents, Items.COBBLESTONE) + " cobblestone in, not 5"))
                .thenExecute(() -> helper.setBlock(below, Blocks.HOPPER))
                .thenWaitUntil(() -> {
                    var hopper = helper.getBlockEntity(below, HopperBlockEntity.class);
                    int pulled = hopper.countItem(Items.COBBLESTONE);
                    helper.assertTrue(pulled == 5 && held(contents, Items.COBBLESTONE) == 0, "the hopper under it has pulled " + pulled + ", not 5");
                })
                .thenSucceed();
    }

    private static void byHand(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        WellTests.placeCore(helper, owner);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed");
        var contents = hatch(helper, HATCH).contents();
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL, 16));
        helper.useBlock(HATCH, owner);
        helper.assertTrue(owner.getMainHandItem().isEmpty(), "the owner still holds " + owner.getMainHandItem());
        helper.assertTrue(held(contents, Items.ENDER_PEARL) == 16, "the hatch holds " + held(contents, Items.ENDER_PEARL) + " ender pearls, not 16");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 40));
        helper.useBlock(HATCH, owner);
        helper.assertTrue(held(contents, Items.COBBLESTONE) == 40, "the hatch holds " + held(contents, Items.COBBLESTONE) + " cobblestone, not 40");

        var other = new WellTests.Listener(helper, "someone-else");
        var whose = Component.translatable("voidworks.hatch.owned_by", owner.getGameProfile().name());
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 3));
        helper.useBlock(HATCH, other);
        other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(HATCH, other);
        helper.assertTrue(other.overlays.equals(List.of(whose, whose)), "another player was told " + other.overlays + ", not whose hatch it is");
        helper.assertTrue(held(contents, Items.DIRT) == 0 && held(contents, Items.ENDER_PEARL) == 16, "another player's hand changed what the hatch holds");

        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(HATCH, owner);
        var taken = owner.getMainHandItem();
        helper.assertTrue(taken.is(Items.ENDER_PEARL) && taken.getCount() == 16, "the owner's empty hand took " + taken + ", not the first stack");
        helper.assertTrue(held(contents, Items.ENDER_PEARL) == 0 && held(contents, Items.COBBLESTONE) == 40,
                "taking a stack out left the hatch holding the wrong things");
        helper.succeed();
    }

    private static void savedContents(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        WellTests.placeCore(helper, owner);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed");
        var level = helper.getLevel();
        var pos = helper.absolutePos(HATCH);
        var place = Place.of(level, pos);
        insert(itemFace(helper), Items.DIAMOND, 70);
        var hatches = SavedWellHatches.of(level.getServer());
        helper.assertTrue(held(hatches.at(place).orElseThrow().contents(), Items.DIAMOND) == 70, "the server's saved hatches do not hold its diamonds");

        var saved = helper.getBlockEntity(HATCH, WellHatchBlockEntity.class).saveWithFullMetadata(level.registryAccess());
        helper.assertFalse(saved.toString().contains("diamond"), "the hatch's block saves its contents: " + saved);

        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var reread = WellHatches.CODEC.parse(ops, WellHatches.CODEC.encodeStart(ops, hatches).getOrThrow()).getOrThrow();
        var reloaded = reread.at(place).orElseThrow();
        helper.assertTrue(held(reloaded.contents(), Items.DIAMOND) == 70, "the saved hatches reread hold " + held(reloaded.contents(), Items.DIAMOND));
        helper.assertTrue(reloaded.contents().size() == 27, "the saved hatch reread holds " + reloaded.contents().size() + " slots");
        helper.assertTrue(reloaded.well().equals(hatches.at(place).orElseThrow().well()) && reloaded.owner().equals(hatches.at(place).orElseThrow().owner()),
                "the saved hatch reread lost its Well or its owner");

        var state = level.getBlockState(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        helper.assertTrue(held(itemFace(helper), Items.DIAMOND) == 70, "the hatch's block, loaded again, holds " + held(itemFace(helper), Items.DIAMOND));
        helper.succeed();
    }

    private static void outlivesItsWell(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        var core = WellTests.placeCore(helper, owner);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed");
        insert(itemFace(helper), Items.IRON_INGOT, 30);
        helper.setBlock(WellTests.WELL.west(), Blocks.AIR);
        var wellPlace = Place.of(helper.getLevel(), helper.absolutePos(WellTests.WELL));
        helper.startSequence()
                .thenIdle(2 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertFalse(core.whole(helper.getLevel()), "the Well with a wall taken out is whole");
                    helper.assertTrue(held(itemFace(helper), Items.IRON_INGOT) == 30, "the hatch of a Well no longer whole lost what it held");
                    helper.destroyBlock(WellTests.WELL);
                })
                .thenIdle(2 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(SavedVoidWells.of(helper.getLevel().getServer()).at(wellPlace).isEmpty(), "the broken core's Well is still listed");
                    var hatch = hatch(helper, HATCH);
                    helper.assertTrue(held(hatch.contents(), Items.IRON_INGOT) == 30, "the hatch of a broken core lost what it held");
                    helper.assertTrue(hatch.well().equals(Optional.of(wellPlace)), "the hatch of a broken core no longer remembers its Well");
                    insert(itemFace(helper), Items.GOLD_INGOT, 4);
                    helper.setBlock(HATCH.below(), Blocks.HOPPER);
                    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 2));
                    helper.useBlock(HATCH, owner);
                    helper.assertTrue(held(hatch.contents(), Items.COPPER_INGOT) == 2, "the hatch of a broken core refused its owner's hand");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(HATCH.below(), HopperBlockEntity.class).countItem(Items.IRON_INGOT) > 0,
                        "a hopper under the hatch of a broken core pulls nothing"))
                .thenSucceed();
    }

    private static void brokenDrops(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        WellTests.placeCore(helper, owner);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed");
        insert(itemFace(helper), Items.EMERALD, 12);
        var place = Place.of(helper.getLevel(), helper.absolutePos(HATCH));
        helper.destroyBlock(HATCH);
        helper.assertTrue(SavedWellHatches.of(helper.getLevel().getServer()).at(place).isEmpty(), "the broken hatch is still in the saved hatches");
        helper.assertItemEntityCountIs(Items.EMERALD, HATCH, 2, 12);
        helper.succeed();
    }

    /** A hatch taken away with no side effects, as by a command, leaves its contents saved; one placed there drops them, holding nothing. */
    private static void staleContentsDropped(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        WellTests.placeCore(helper, owner);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed");
        insert(itemFace(helper), Items.LAPIS_LAZULI, 9);
        var level = helper.getLevel();
        level.setBlock(helper.absolutePos(HATCH), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed again");
        helper.assertTrue(held(itemFace(helper), Items.LAPIS_LAZULI) == 0, "the new hatch holds what the old one left");
        helper.assertItemEntityCountIs(Items.LAPIS_LAZULI, HATCH, 2, 9);
        helper.succeed();
    }

    /**
     * A watcher's hatch, in no server level, still has an item face, holding nothing: Jade asks a block's
     * item face on the client before it asks the server for what it holds.
     */
    private static void watcherHasAFace(GameTestHelper helper) {
        var watched = new WellHatchBlockEntity(helper.absolutePos(HATCH), VoidworksBlocks.WELL_HATCH.get().defaultBlockState());
        helper.assertTrue(watched.hatch().isEmpty(), "a watcher's hatch has the server's entry");
        var face = watched.itemFace();
        helper.assertTrue(face != null && face.size() == 0, "a watcher's hatch has no item face, or one that claims to hold something");
        helper.succeed();
    }

    /** Where a hatch is placed: the face of the block at {@code pos}, and what to call it in a failure. */
    private record Against(BlockPos pos, Direction face, String what) {
    }

    /** {@code player} places a hatch against {@code face} of the block at {@code against}; @return whether it stands there now */
    private static boolean placeHatch(GameTestHelper helper, ServerPlayer player, BlockPos against, Direction face) {
        var absolute = helper.absolutePos(against);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidworksBlocks.WELL_HATCH_ITEM.get()));
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5)), face, absolute, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return helper.getBlockState(against.relative(face)).is(VoidworksBlocks.WELL_HATCH.get());
    }

    private static WellHatches.Hatch hatch(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, WellHatchBlockEntity.class).hatch().orElseThrow();
    }

    private static ResourceHandler<ItemResource> itemFace(GameTestHelper helper) {
        var face = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(HATCH), Direction.UP);
        helper.assertTrue(face != null, "the hatch has no item face");
        return face;
    }

    private static void insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            if (inserted != count) {
                throw new IllegalStateException("put " + inserted + " " + item + " in, not " + count);
            }
        }
    }

    private static int held(ResourceHandler<ItemResource> handler, Item item) {
        int count = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            if (handler.getResource(slot).is(item)) {
                count += handler.getAmountAsInt(slot);
            }
        }
        return count;
    }
}
