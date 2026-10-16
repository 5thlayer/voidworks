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

import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.well.HatchReport;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
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
 * one, an empty hand clearing it, each told above the hotbar. A requester gives nothing to a hand and
 * empties through its faces; switched back, it loses its request and offers what it holds. No one but
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
    }

    private static void switched(GameTestHelper helper) {
        var owner = ownedHatch(helper);
        var hatch = hatch(helper, HATCH);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.PROVIDER, "a hatch is placed a " + hatch.mode());
        helper.assertTrue(lines(hatch).equals(List.of(Component.translatable("voidworks.jade.hatch.provider"))),
                "Jade shows a provider as " + lines(hatch));
        insert(hatch.contents(), Items.ENDER_PEARL, 16);

        sneakUse(helper, owner, ItemStack.EMPTY);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.REQUESTER, "sneak-using a provider made it a " + hatch.mode());
        helper.assertTrue(owner.overlays.getLast().equals(Component.translatable("voidworks.hatch.now_requester")),
                "switching to a requester told " + owner.overlays.getLast());
        helper.assertTrue(owner.getMainHandItem().isEmpty() && held(hatch.contents(), Items.ENDER_PEARL) == 16,
                "a provider switched to a requester did not keep what it holds");
        helper.assertTrue(hatch.request().isEmpty(), "a provider switched to a requester asks for " + hatch.request());
        helper.assertTrue(lines(hatch).equals(List.of(Component.translatable("voidworks.jade.hatch.requester_nothing"))),
                "Jade shows a requester asking for nothing as " + lines(hatch));

        use(helper, owner, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(hatch.request().isPresent(), "using an item on a requester set no request");
        sneakUse(helper, owner, ItemStack.EMPTY);
        helper.assertTrue(hatch.mode() == WellHatches.Mode.PROVIDER, "sneak-using a requester made it a " + hatch.mode());
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
        helper.assertTrue(lines(hatch).equals(List.of(Component.translatable("voidworks.jade.hatch.requester", 3, new ItemStack(Items.IRON_INGOT).getItemName()))),
                "Jade shows a requester's request as " + lines(hatch));

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

    private static List<Component> lines(WellHatches.Hatch hatch) {
        return HatchReport.of(hatch).lines();
    }
}
