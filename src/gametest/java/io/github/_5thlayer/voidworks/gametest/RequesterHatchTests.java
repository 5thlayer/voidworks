// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import static io.github._5thlayer.voidworks.gametest.WellHatchTests.HATCH;
import static io.github._5thlayer.voidworks.gametest.WellHatchTests.HATCH_AGAINST;
import static io.github._5thlayer.voidworks.gametest.WellHatchTests.hatch;
import static io.github._5thlayer.voidworks.gametest.WellHatchTests.held;
import static io.github._5thlayer.voidworks.gametest.WellHatchTests.insert;
import static io.github._5thlayer.voidworks.gametest.WellHatchTests.itemFace;
import static io.github._5thlayer.voidworks.gametest.WellHatchTests.placeHatch;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.well.HatchReport;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.WellHatchBlock;
import io.github._5thlayer.voidworks.well.WellHatchBlockEntity;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Well hatch made a requester by its owner, by hand, with no dragon: sneak-using it with an empty
 * hand switches it between provider and requester; on a requester an item sets the request to its
 * kind, components ignored, and adds a stack per use up to its slots, another item starting again at
 * one, an empty hand clearing it, each told above the hotbar, its front showing which it is and the
 * item it asks for, or a provider's first stack. A requester gives nothing to a hand and
 * empties through its faces; a requester's own item places one and is what one drops, and sneak-used
 * in the air turns into a provider's and back; switched back, it loses its request and offers what it holds. No one but
 * its owner switches it or sets its request. Its mode and request survive a reload, and Jade shows them.
 */
final class RequesterHatchTests {

    private static final int TICKS = 200;

    private RequesterHatchTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_requester_hatch_is_switched_to_and_from_by_its_owner_sneak_using_it_with_an_empty_hand", TICKS,
                RequesterHatchTests::switched);
        tests.test("a_requester_hatch_s_request_is_set_added_to_replaced_and_cleared_by_its_owner_s_hand", TICKS,
                RequesterHatchTests::requestByHand);
        tests.test("a_requester_hatch_gives_nothing_to_a_hand_and_empties_through_its_item_faces", TICKS,
                RequesterHatchTests::emptiesThroughFaces);
        tests.test("a_requester_hatch_is_switched_and_set_by_no_one_but_its_owner", TICKS, RequesterHatchTests::ownerOnly);
        tests.test("a_requester_hatch_keeps_its_mode_and_request_across_a_save_and_reload", TICKS, RequesterHatchTests::saved);
        tests.test("a_requester_hatch_is_placed_from_its_own_item_and_drops_as_what_it_is", TICKS, RequesterHatchTests::itsOwnItem);
        tests.test("a_requester_hatch_item_turns_into_a_provider_s_and_back_sneak_used_in_the_air", TICKS, RequesterHatchTests::itemSwitched);
        tests.test("a_requester_hatch_shows_its_request_on_its_front_and_a_provider_its_first_stack", TICKS, RequesterHatchTests::shown);
    }

    private static void switched(GameTestHelper helper) {
        var owner = ownedHatch(helper);
        var hatch = hatch(helper, HATCH);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.PROVIDER, "a hatch is placed a " + hatch.mode());
        helper.assertBlockProperty(HATCH, WellHatchBlock.MODE, WellHatches.Mode.PROVIDER);
        helper.assertTrue(lines(helper, hatch).isEmpty(), "Jade shows a provider, which its name already says it is, as " + lines(helper, hatch));
        insert(hatch.contents(), Items.ENDER_PEARL, 16);

        sneakUse(helper, owner, ItemStack.EMPTY);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.REQUESTER, "sneak-using a provider made it a " + hatch.mode());
        helper.assertBlockProperty(HATCH, WellHatchBlock.MODE, WellHatches.Mode.REQUESTER);
        helper.assertTrue(owner.overlays.getLast().equals(Component.translatable("voidworks.hatch.now_requester")),
                "switching to a requester told " + owner.overlays.getLast());
        helper.assertTrue(owner.getMainHandItem().isEmpty() && held(hatch.contents(), Items.ENDER_PEARL) == 16,
                "a provider switched to a requester did not keep what it holds");
        helper.assertTrue(hatch.request().isEmpty(), "a provider switched to a requester asks for " + hatch.request());
        helper.assertTrue(lines(helper, hatch).equals(List.of(Component.translatable("voidworks.jade.hatch.requesting_nothing"))),
                "Jade shows a requester asking for nothing as " + lines(helper, hatch));

        use(helper, owner, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(hatch.request().isPresent(), "using an item on a requester set no request");
        sneakUse(helper, owner, ItemStack.EMPTY);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.PROVIDER, "sneak-using a requester made it a " + hatch.mode());
        helper.assertBlockProperty(HATCH, WellHatchBlock.MODE, WellHatches.Mode.PROVIDER);
        helper.assertTrue(owner.overlays.getLast().equals(Component.translatable("voidworks.hatch.now_provider")),
                "switching to a provider told " + owner.overlays.getLast());
        helper.assertTrue(hatch.request().isEmpty(), "a requester switched to a provider kept its request " + hatch.request());
        use(helper, owner, ItemStack.EMPTY);
        var taken = owner.getMainHandItem();
        helper.assertTrue(taken.is(Items.ENDER_PEARL) && taken.getCount() == 16,
                "a requester switched to a provider does not offer what it holds: the owner's hand took " + taken);

        // Sneaking with anything in either hand uses no block, as against a chest.
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        sneakUse(helper, owner, ItemStack.EMPTY);
        owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.PROVIDER, "sneak-using it with a torch in the off hand made it a " + hatch.mode());
        helper.succeed();
    }

    private static void requestByHand(GameTestHelper helper) {
        var owner = ownedHatch(helper);
        var hatch = hatch(helper, HATCH);
        sneakUse(helper, owner, ItemStack.EMPTY);

        var named = new ItemStack(Items.IRON_INGOT, 5);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Shiny"));
        use(helper, owner, named);
        assertRequest(helper, owner, hatch, Items.IRON_INGOT, 1);
        helper.assertTrue(owner.getMainHandItem().getCount() == 5, "setting a request took " + (5 - owner.getMainHandItem().getCount()) + " from the hand");
        helper.assertTrue(held(hatch.contents(), Items.IRON_INGOT) == 0, "setting a request put the item in the hatch");
        use(helper, owner, new ItemStack(Items.IRON_INGOT));
        use(helper, owner, new ItemStack(Items.IRON_INGOT));
        assertRequest(helper, owner, hatch, Items.IRON_INGOT, 3);
        helper.assertTrue(lines(helper, hatch).equals(List.of(Component.translatable("voidworks.jade.hatch.requesting")))
                && HatchReport.of(hatch, helper.getLevel().getServer()).request().equals(hatch.request()), "Jade shows a requester's request as " + lines(helper, hatch));

        for (int use = 0; use < 30; use++) {
            use(helper, owner, new ItemStack(Items.IRON_INGOT));
        }
        assertRequest(helper, owner, hatch, Items.IRON_INGOT, 27);

        use(helper, owner, new ItemStack(Items.COBBLESTONE));
        assertRequest(helper, owner, hatch, Items.COBBLESTONE, 1);
        helper.assertTrue(helper.getBlockState(HATCH.above()).isAir() && helper.getBlockState(HATCH.relative(Direction.NORTH)).isAir(),
                "a block's own use placed it against a requester");

        use(helper, owner, ItemStack.EMPTY);
        helper.assertTrue(hatch.request().isEmpty(), "an empty hand left the request " + hatch.request());
        helper.assertTrue(hatch.mode() == WellHatches.Mode.REQUESTER, "an empty hand made the requester a " + hatch.mode());
        helper.assertTrue(owner.overlays.getLast().equals(Component.translatable("voidworks.hatch.requesting_nothing")),
                "clearing the request told " + owner.overlays.getLast());
        helper.succeed();
    }

    private static void emptiesThroughFaces(GameTestHelper helper) {
        var owner = ownedHatch(helper);
        var hatch = hatch(helper, HATCH);
        sneakUse(helper, owner, ItemStack.EMPTY);
        insert(itemFace(helper), Items.GOLD_INGOT, 20);
        use(helper, owner, ItemStack.EMPTY);
        helper.assertTrue(owner.getMainHandItem().isEmpty() && held(hatch.contents(), Items.GOLD_INGOT) == 20,
                "a requester gave its owner's empty hand " + owner.getMainHandItem());
        helper.setBlock(HATCH.below(), Blocks.HOPPER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(HATCH.below(), HopperBlockEntity.class).countItem(Items.GOLD_INGOT) == 20,
                        "the hopper under a requester has pulled " + (20 - held(hatch.contents(), Items.GOLD_INGOT)) + " gold, not 20"))
                .thenSucceed();
    }

    private static void ownerOnly(GameTestHelper helper) {
        var owner = ownedHatch(helper);
        var hatch = hatch(helper, HATCH);
        var other = new WellTests.Listener(helper, "someone-else");
        var whose = Component.translatable("voidworks.hatch.owned_by", owner.getGameProfile().name());

        sneakUse(helper, other, ItemStack.EMPTY);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.PROVIDER, "another player switched the hatch to a " + hatch.mode());
        helper.assertBlockProperty(HATCH, WellHatchBlock.MODE, WellHatches.Mode.PROVIDER);
        sneakUse(helper, owner, ItemStack.EMPTY);
        use(helper, owner, new ItemStack(Items.IRON_INGOT));
        use(helper, other, new ItemStack(Items.COBBLESTONE));
        use(helper, other, new ItemStack(Items.IRON_INGOT));
        use(helper, other, ItemStack.EMPTY);
        sneakUse(helper, other, ItemStack.EMPTY);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.REQUESTER, "another player switched the requester to a " + hatch.mode());
        helper.assertTrue(hatch.request().equals(Optional.of(new WellHatches.Request(Items.IRON_INGOT, 1))),
                "another player changed the request to " + hatch.request());
        helper.assertTrue(other.overlays.equals(List.of(whose, whose, whose, whose, whose)),
                "another player was told " + other.overlays + ", not whose hatch it is");
        helper.succeed();
    }

    private static void saved(GameTestHelper helper) {
        var owner = ownedHatch(helper);
        var place = Place.of(helper.getLevel(), helper.absolutePos(HATCH));
        sneakUse(helper, owner, ItemStack.EMPTY);
        use(helper, owner, new ItemStack(Items.REDSTONE));
        use(helper, owner, new ItemStack(Items.REDSTONE));

        var reread = reread(helper, place);
        helper.assertTrue(reread.mode() == WellHatches.Mode.REQUESTER, "the saved requester reread is a " + reread.mode());
        helper.assertTrue(reread.request().equals(Optional.of(new WellHatches.Request(Items.REDSTONE, 2))),
                "the saved requester reread asks for " + reread.request());

        sneakUse(helper, owner, ItemStack.EMPTY);
        var provider = reread(helper, place);
        helper.assertTrue(provider.mode() == WellHatches.Mode.PROVIDER && provider.request().isEmpty(),
                "the saved provider reread is a " + provider.mode() + " asking for " + provider.request());
        helper.succeed();
    }

    private static void itsOwnItem(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        WellTests.placeCore(helper, owner);
        var requesterItem = VoidworksBlocks.WELL_HATCH_REQUESTER_ITEM.get();
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST, requesterItem), "no hatch was placed from a requester's item");
        helper.assertTrue(hatch(helper, HATCH).mode() == WellHatches.Mode.REQUESTER, "a requester's item placed a " + hatch(helper, HATCH).mode());
        helper.assertBlockProperty(HATCH, WellHatchBlock.MODE, WellHatches.Mode.REQUESTER);
        var picked = helper.getBlockState(HATCH).getCloneItemStack(helper.getLevel(), helper.absolutePos(HATCH), false);
        helper.assertTrue(picked.is(requesterItem), "a requester is picked as " + picked);
        helper.getLevel().destroyBlock(helper.absolutePos(HATCH), true);
        helper.assertItemEntityCountIs(requesterItem, HATCH, 2, 1);
        helper.assertItemEntityCountIs(VoidworksBlocks.WELL_HATCH_ITEM.get(), HATCH, 2, 0);
        helper.killAllEntitiesOfClass(ItemEntity.class);

        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST, VoidworksBlocks.WELL_HATCH_ITEM.get()), "no hatch was placed from a provider's item");
        helper.assertTrue(hatch(helper, HATCH).mode() == WellHatches.Mode.PROVIDER, "a provider's item placed a " + hatch(helper, HATCH).mode());
        sneakUse(helper, owner, ItemStack.EMPTY);
        helper.getLevel().destroyBlock(helper.absolutePos(HATCH), true);
        helper.assertItemEntityCountIs(requesterItem, HATCH, 2, 1);
        helper.assertItemEntityCountIs(VoidworksBlocks.WELL_HATCH_ITEM.get(), HATCH, 2, 0);
        helper.succeed();
    }

    private static void itemSwitched(GameTestHelper helper) {
        var player = new WellTests.Listener(helper, "holder");
        var provider = VoidworksBlocks.WELL_HATCH_ITEM.get();
        var requester = VoidworksBlocks.WELL_HATCH_REQUESTER_ITEM.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(provider, 5));
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(provider) && player.getMainHandItem().getCount() == 5,
                "using a provider's item in the air, not sneaking, left " + player.getMainHandItem());
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(requester) && player.getMainHandItem().getCount() == 5,
                "sneak-using 5 providers' items in the air left " + player.getMainHandItem() + ", not 5 requesters'");
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(provider) && player.getMainHandItem().getCount() == 5,
                "sneak-using 5 requesters' items in the air left " + player.getMainHandItem() + ", not 5 providers'");
        helper.succeed();
    }

    private static void shown(GameTestHelper helper) {
        var owner = ownedHatch(helper);
        var hatch = hatch(helper, HATCH);
        var entity = helper.getBlockEntity(HATCH, WellHatchBlockEntity.class);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(entity.shown().isEmpty(), "an empty provider shows " + entity.shown());
                    insert(hatch.contents(), Items.ENDER_PEARL, 16);
                    insert(hatch.contents(), Items.COBBLESTONE, 40);
                })
                .thenWaitUntil(() -> assertShown(helper, entity, Items.ENDER_PEARL, "a provider holding pearls first"))
                .thenExecute(() -> sneakUse(helper, owner, ItemStack.EMPTY))
                .thenWaitUntil(() -> helper.assertTrue(entity.shown().isEmpty(), "a requester asking for nothing shows " + entity.shown()))
                .thenExecute(() -> use(helper, owner, new ItemStack(Items.IRON_INGOT, 7)))
                .thenWaitUntil(() -> assertShown(helper, entity, Items.IRON_INGOT, "a requester asking for iron"))
                .thenExecute(() -> {
                    var update = entity.getUpdateTag(helper.getLevel().registryAccess());
                    helper.assertTrue(update.toString().contains("iron_ingot") && !update.toString().contains("ender_pearl"),
                            "a watcher is sent " + update + ", not the requested iron alone");
                })
                .thenSucceed();
    }

    private static void assertShown(GameTestHelper helper, WellHatchBlockEntity entity, Item item, String what) {
        helper.assertTrue(entity.shown().is(item) && entity.shown().getCount() == 1, what + " shows " + entity.shown() + ", not one " + item);
    }

    /** @return the hatch at {@code place} as the server's saved hatches, written and read back, hold it */
    private static WellHatches.Hatch reread(GameTestHelper helper, Place place) {
        var level = helper.getLevel();
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var hatches = SavedWellHatches.of(level.getServer());
        return WellHatches.CODEC.parse(ops, WellHatches.CODEC.encodeStart(ops, hatches).getOrThrow()).getOrThrow().at(place).orElseThrow();
    }

    /** @return the owner of a Well with a hatch placed on its east wall */
    private static WellTests.Listener ownedHatch(GameTestHelper helper) {
        WellTests.buildWell(helper);
        var owner = new WellTests.Listener(helper, "well-owner");
        WellTests.placeCore(helper, owner);
        helper.assertTrue(placeHatch(helper, owner, HATCH_AGAINST, Direction.EAST), "no hatch was placed");
        return owner;
    }

    private static void use(GameTestHelper helper, WellTests.Listener player, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        helper.useBlock(HATCH, player);
    }

    /** Sneak-uses the hatch as a player's click does, through their game mode, which uses no block for one sneaking with anything in hand. */
    private static void sneakUse(GameTestHelper helper, WellTests.Listener player, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setShiftKeyDown(true);
        var pos = helper.absolutePos(HATCH);
        player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false));
        player.setShiftKeyDown(false);
    }

    private static void assertRequest(GameTestHelper helper, WellTests.Listener owner, WellHatches.Hatch hatch, Item item, int stacks) {
        helper.assertTrue(hatch.request().equals(Optional.of(new WellHatches.Request(item, stacks))),
                "the requester asks for " + hatch.request() + ", not " + stacks + " stacks of " + item);
        var told = Component.translatable("voidworks.hatch.requesting", stacks, new ItemStack(item).getItemName());
        helper.assertTrue(owner.overlays.getLast().equals(told), "setting the request told " + owner.overlays.getLast() + ", not " + told);
    }

    private static List<Component> lines(GameTestHelper helper, WellHatches.Hatch hatch) {
        return HatchReport.of(hatch, helper.getLevel().getServer()).lines();
    }
}
