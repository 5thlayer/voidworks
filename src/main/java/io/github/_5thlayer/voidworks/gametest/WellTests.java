// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.SiphonMotes;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.ShaftFill;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellReport;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * A Void Well's owner, fill target and name, each set in the world: the player who places its core
 * owns it; its owner cycles its target by sneak-using the core with an empty hand, and anyone else is
 * told whose it is; plain use with an empty hand shows its status and target, and Jade its status,
 * target and name; a name tag names it and is used up; and a Well with no owner is claimed by the
 * first player to name it or set its target. All of it is kept while the Well is not whole and across
 * a reload. A Void Siphon used on its core pays it once whole, and Voidstone touching any block of
 * the well, casing, rim or core, reaches it, so its core's face stays free. Its shaft shows its
 * network's fill to a watcher, but only while it is awake and whole.
 */
final class WellTests {

    /** The core, in the middle of its north wall's middle course, its floor on the platform. */
    private static final BlockPos WELL = new BlockPos(4, 3, 2);
    /** A second core, alone, apart from the first's well. */
    private static final BlockPos OTHER = new BlockPos(1, 1, 7);
    /** Beside the east wall's middle course, outside the well. */
    private static final BlockPos BESIDE_CASING = new BlockPos(7, 3, 4);
    /** Beside the east side of the rim, outside the well. */
    private static final BlockPos BESIDE_RIM = new BlockPos(7, 5, 4);
    /** Grades above the pressure that pay the Well's cost in as many motes as one Voidstone holds. */
    private static final int COST_STEPS = 5;

    private static final int TICKS = 200;

    private WellTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("the_player_who_places_a_void_well_core_owns_the_well", TICKS, WellTests::placerOwnsIt);
        tests.test("its_owner_sneak_using_a_void_well_core_with_an_empty_hand_cycles_its_fill_target", TICKS, WellTests::ownerCyclesTarget);
        tests.test("anyone_else_sneak_using_or_naming_a_void_well_changes_nothing_and_is_told_whose_it_is", TICKS, WellTests::othersChangeNothing);
        tests.test("plain_use_of_a_void_well_core_shows_its_status_and_target", TICKS, WellTests::plainUseShowsStatusAndTarget);
        tests.test("jade_shows_a_void_well_s_status_target_and_name", TICKS, WellTests::jadeShowsStatusTargetAndName);
        tests.test("jade_shows_a_void_well_s_target_as_a_share_of_its_network_s_capacity_which_voidstone_joining_raises", TICKS,
                WellTests::jadeTargetIsANetworkShare);
        tests.test("a_name_tag_names_a_void_well_and_is_used_up", TICKS, WellTests::nameTagNamesIt);
        tests.test("a_void_well_no_one_owns_is_claimed_by_the_first_player_to_name_it_or_set_its_target", TICKS, WellTests::unownedIsClaimed);
        tests.test("an_emptying_siphon_used_on_a_dormant_void_well_core_pays_its_cost", TICKS, WellTests::siphonPaysIt);
        tests.test("a_void_well_soaks_its_cost_and_joins_the_network_through_voidstone_touching_its_casing_leaving_its_core_free", TICKS,
                WellTests::joinsThroughItsCasing);
        tests.test("a_void_well_saved_before_its_casing_touched_for_it_rejoins_through_its_casing_once_loaded", TICKS,
                WellTests::savedWellRejoinsThroughItsCasing);
        tests.test("a_void_well_keeps_its_owner_target_and_name_while_not_whole_and_across_a_save_and_reload", TICKS, WellTests::keptWhileNotWholeAndReloaded);
        tests.test("a_void_well_is_listed_for_its_owner_s_dragons_with_its_target_until_its_core_is_broken", TICKS, WellTests::listedForDragons);
        tests.test("a_void_well_s_shaft_shows_its_network_s_fill_to_a_watcher_only_while_it_is_awake_and_whole", TICKS * 2,
                WellTests::shaftShowsFill);
    }

    private static void placerOwnsIt(GameTestHelper helper) {
        buildWell(helper);
        var player = new Listener(helper, "well-placer");
        var well = placeCore(helper, player);
        helper.assertTrue(helper.getBlockState(WELL).getValue(VoidWellBlock.FACING) == Direction.NORTH, "the placed core does not face out of its wall");
        helper.assertTrue(ownedBy(well, player), "the Well's owner is " + well.owner() + ", not its placer");
        helper.assertTrue(well.target() == FillTarget.NONE, "a new Well's fill target is " + well.target());
        helper.assertTrue(well.name().isEmpty(), "a new Well is named " + well.name());
        var pos = helper.absolutePos(WELL);
        var unnamed = Component.translatable("voidworks.well.unnamed", helper.getLevel().dimension().identifier().toString(), pos.getX(), pos.getY(),
                pos.getZ());
        helper.assertTrue(well.label().equals(unnamed), "an unnamed Well goes by " + well.label() + ", not its dimension and position");
        helper.succeed();
    }

    /** None, a quarter, a half, three quarters, full, and none again, each told to its owner. */
    private static void ownerCyclesTarget(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        owner.setShiftKeyDown(true);
        for (var expected : List.of(FillTarget.QUARTER, FillTarget.HALF, FillTarget.THREE_QUARTERS, FillTarget.FULL, FillTarget.NONE)) {
            helper.useBlock(WELL, owner);
            helper.assertTrue(well.target() == expected, "the owner's sneak-use set the target to " + well.target() + ", not " + expected);
            var told = Component.translatable("voidworks.well.target_set", expected.label());
            helper.assertTrue(owner.overlays.getLast().equals(told), "the owner was told " + owner.overlays.getLast() + ", not " + told);
        }
        helper.succeed();
    }

    private static void othersChangeNothing(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        var other = new Listener(helper, "someone-else");
        var whose = Component.translatable("voidworks.well.owned_by", owner.getGameProfile().name());
        other.setShiftKeyDown(true);
        helper.useBlock(WELL, other);
        helper.assertTrue(well.target() == FillTarget.NONE, "another player's sneak-use set the target to " + well.target());
        helper.assertTrue(other.overlays.equals(List.of(whose)), "another player was told " + other.overlays + ", not whose Well it is");
        other.setShiftKeyDown(false);
        var tag = nameTag("Not Theirs");
        other.setItemInHand(InteractionHand.MAIN_HAND, tag);
        helper.useBlock(WELL, other);
        helper.assertTrue(well.name().isEmpty(), "another player's name tag named the Well " + well.name());
        helper.assertTrue(tag.getCount() == 1, "another player's name tag was used up");
        helper.assertTrue(other.overlays.equals(List.of(whose, whose)), "another player naming it was told " + other.overlays.getLast());
        helper.assertTrue(ownedBy(well, owner), "the Well changed owner to " + well.owner());
        helper.succeed();
    }

    /** Plain use, from its owner or anyone, says how the Well stands and its target, and changes nothing. */
    private static void plainUseShowsStatusAndTarget(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        owner.setShiftKeyDown(true);
        helper.useBlock(WELL, owner);
        owner.setShiftKeyDown(false);
        helper.useBlock(WELL, owner);
        helper.assertTrue(well.target() == FillTarget.QUARTER, "plain use changed the target to " + well.target());
        var shown = Component.translatable("voidworks.well.use", well.dormancy().status(), FillTarget.QUARTER.label());
        helper.assertTrue(owner.overlays.getLast().equals(shown), "plain use showed " + owner.overlays.getLast() + ", not " + shown);
        var other = new Listener(helper, "someone-else");
        helper.setBlock(WELL.east(), Blocks.AIR);
        helper.useBlock(WELL, other);
        var notWhole = Component.translatable("voidworks.well.use", Component.translatable("voidworks.well.not_whole"), FillTarget.QUARTER.label());
        helper.assertTrue(other.overlays.equals(List.of(notWhole)), "plain use of a Well no longer whole showed " + other.overlays);
        helper.succeed();
    }

    /** Its name, that it is not whole when so, its target in motes, and its owner; its payment is the Dormant line's. */
    private static void jadeShowsStatusTargetAndName(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        owner.setShiftKeyDown(true);
        helper.useBlock(WELL, owner);
        helper.useBlock(WELL, owner);
        owner.setShiftKeyDown(false);
        owner.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Home"));
        helper.useBlock(WELL, owner);
        long half = VoidworksConfig.wellCapacity() / 2;
        var name = Component.literal("Home");
        var target = Component.translatable("voidworks.jade.well.target", FillTarget.HALF.label(), half);
        var whose = Component.translatable("voidworks.jade.well.owner", owner.getGameProfile().name());
        var whole = WellReport.of(well, helper.getLevel()).lines();
        helper.assertTrue(whole.equals(List.of(name, target, whose)), "Jade shows the whole Well as " + whole);
        helper.setBlock(WELL.east(), Blocks.AIR);
        var broken = WellReport.of(well, helper.getLevel()).lines();
        var notWhole = Component.translatable("voidworks.jade.well.not_whole").withStyle(ChatFormatting.RED);
        helper.assertTrue(broken.equals(List.of(name, notWhole, target, whose)), "Jade shows the Well no longer whole as " + broken);
        // Three quarters, full, and back to none.
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        owner.setShiftKeyDown(true);
        for (int use = 0; use < 3; use++) {
            helper.useBlock(WELL, owner);
        }
        var reservoir = WellReport.of(well, helper.getLevel()).lines().get(2);
        helper.assertTrue(reservoir.equals(Component.translatable("voidworks.jade.well.reservoir")), "Jade shows no target as " + reservoir);
        helper.succeed();
    }

    /**
     * Awake in a network, a Well's target is a share of its core and every Voidstone of the network
     * together, so Voidstone joining it raises the target Jade shows (ADR 0006).
     */
    private static void jadeTargetIsANetworkShare(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        owner.setShiftKeyDown(true);
        helper.useBlock(WELL, owner);
        helper.setBlock(BESIDE_CASING, VoidworksBlocks.VOIDSTONE.get());
        VoidstoneTests.insert(helper, BESIDE_CASING, DisplacerTests.pressure(helper, WELL) + COST_STEPS, costInMotes(helper, well));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(well.dormancy().awake(), "the Well is still Dormant, paid " + well.dormancy().paid()))
                .thenExecute(() -> {
                    assertTargetShown(helper, well, VoidworksConfig.wellCapacity() + VoidworksConfig.voidstoneCapacity());
                    helper.setBlock(BESIDE_RIM, VoidworksBlocks.VOIDSTONE.get());
                    assertTargetShown(helper, well, VoidworksConfig.wellCapacity() + 2L * VoidworksConfig.voidstoneCapacity());
                })
                .thenSucceed();
    }

    /** Asserts that Jade shows {@code well}'s quarter target as a quarter of {@code capacity}. */
    private static void assertTargetShown(GameTestHelper helper, VoidWellBlockEntity well, long capacity) {
        var shown = WellReport.of(well, helper.getLevel()).lines().get(1);
        var expected = Component.translatable("voidworks.jade.well.target", FillTarget.QUARTER.label(), capacity / 4);
        helper.assertTrue(shown.equals(expected), "Jade shows the target as " + shown + ", not a quarter of " + capacity);
    }

    private static void nameTagNamesIt(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        var tag = nameTag("Home");
        owner.setItemInHand(InteractionHand.MAIN_HAND, tag);
        helper.useBlock(WELL, owner);
        helper.assertTrue(well.name().equals(Optional.of(Component.literal("Home"))), "the name tag named the Well " + well.name());
        helper.assertTrue(well.label().equals(Component.literal("Home")), "the named Well goes by " + well.label());
        helper.assertTrue(tag.isEmpty(), "the name tag was not used up");
        var blank = new ItemStack(Items.NAME_TAG);
        owner.setItemInHand(InteractionHand.MAIN_HAND, blank);
        helper.useBlock(WELL, owner);
        helper.assertTrue(blank.getCount() == 1 && well.name().equals(Optional.of(Component.literal("Home"))),
                "a name tag with no name was used up or renamed the Well " + well.name());
        helper.succeed();
    }

    /** Two cores set by command, owned by no one: one claimed by setting its target, the other by naming it. */
    private static void unownedIsClaimed(GameTestHelper helper) {
        helper.setBlock(WELL, VoidworksBlocks.VOID_WELL.get());
        helper.setBlock(OTHER, VoidworksBlocks.VOID_WELL.get());
        var first = core(helper, WELL);
        var second = core(helper, OTHER);
        helper.assertTrue(first.owner().isEmpty() && second.owner().isEmpty(), "a core no player placed has an owner");
        var setter = new Listener(helper, "target-setter");
        setter.setShiftKeyDown(true);
        helper.useBlock(WELL, setter);
        helper.assertTrue(ownedBy(first, setter), "setting its target claimed it for " + first.owner());
        helper.assertTrue(first.target() == FillTarget.QUARTER, "the claim did not set its target: " + first.target());
        var namer = new Listener(helper, "namer");
        namer.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Mine"));
        helper.useBlock(OTHER, namer);
        helper.assertTrue(ownedBy(second, namer), "naming it claimed it for " + second.owner());
        helper.assertTrue(second.name().equals(Optional.of(Component.literal("Mine"))), "the claim did not name it: " + second.name());
        namer.setShiftKeyDown(true);
        namer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(WELL, namer);
        helper.assertTrue(ownedBy(first, setter) && first.target() == FillTarget.QUARTER,
                "a claimed Well was taken by the next player");
        helper.succeed();
    }

    /** Used as a player uses it, through the core's own use, the Siphon empties into the whole Well and wakes it. */
    private static void siphonPaysIt(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        int grade = DisplacerTests.pressure(helper, WELL) + COST_STEPS;
        var siphon = new ItemStack(VoidworksItems.DRACONIC_VOID_SIPHON.get());
        siphon.set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        siphon.set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(grade, costInMotes(helper, well)));
        owner.setItemInHand(InteractionHand.MAIN_HAND, siphon);
        helper.setBlock(WELL.east(), Blocks.AIR);
        helper.useBlock(WELL, owner);
        helper.assertTrue(well.dormancy().paid() == 0, "a Siphon paid a Well that is not whole " + well.dormancy().paid());
        helper.setBlock(WELL.east(), VoidworksBlocks.WELL_CASING.get());
        helper.useBlock(WELL, owner);
        helper.assertTrue(well.dormancy().paid() == well.dormancy().cost(),
                "the Siphon paid the Well " + well.dormancy().paid() + " of " + well.dormancy().cost());
        helper.assertTrue(VoidSiphonItem.heldMotes(owner.getMainHandItem()).total() == 0,
                "the Siphon still holds " + VoidSiphonItem.heldMotes(owner.getMainHandItem()));
        helper.succeedWhen(() -> helper.assertTrue(well.dormancy().awake(), "the paid Well is still Dormant"));
    }

    /**
     * Voidstone outside the east wall's middle course pays the Well's cost and is its network, with
     * nothing on the core's own face; Voidstone beside its rim joins that network too.
     */
    private static void joinsThroughItsCasing(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        helper.setBlock(BESIDE_CASING, VoidworksBlocks.VOIDSTONE.get());
        int pressure = DisplacerTests.pressure(helper, WELL);
        VoidstoneTests.insert(helper, BESIDE_CASING, pressure + COST_STEPS, costInMotes(helper, well));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(well.dormancy().awake(), "the Well is still Dormant, paid " + well.dormancy().paid()))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, WELL.north());
                    assertSameNetwork(helper, BESIDE_CASING, "the Voidstone touching its casing");
                    helper.setBlock(BESIDE_RIM, VoidworksBlocks.VOIDSTONE.get());
                    assertSameNetwork(helper, BESIDE_RIM, "the Voidstone touching its rim");
                    VoidstoneTests.insert(helper, BESIDE_CASING, pressure + 1, 10);
                })
                // A Well's pressure stays below a Voidstone's until it is nearly full, so it takes all but the last mote.
                .thenWaitUntil(() -> helper.assertTrue(heldInWell(helper) == 9, "the Well holds " + heldInWell(helper) + ", not 9 of the 10 motes"))
                .thenSucceed();
    }

    /**
     * A Well awake in a network that touches only at its core, as one saved before its casing touched
     * for it does: loaded again, it rejoins through its casing.
     */
    private static void savedWellRejoinsThroughItsCasing(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        helper.setBlock(BESIDE_CASING, VoidworksBlocks.VOIDSTONE.get());
        VoidstoneTests.insert(helper, BESIDE_CASING, DisplacerTests.pressure(helper, WELL) + COST_STEPS, costInMotes(helper, well));
        var networks = VoidstoneNetworks.of(helper.getLevel());
        var level = helper.getLevel();
        var pos = helper.absolutePos(WELL);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(well.dormancy().awake() && networks.store(pos).isPresent(), "the Well has not woken"))
                .thenExecute(() -> {
                    var state = level.getBlockState(pos);
                    var saved = well.saveWithFullMetadata(level.registryAccess());
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    core(helper, WELL).loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
                    networks.leave(pos);
                    networks.join(pos, StoreKind.WELL);
                    helper.assertTrue(!networks.at(pos).equals(networks.at(helper.absolutePos(BESIDE_CASING))),
                            "a Well joined only at its core is in the network of the Voidstone beside its casing");
                })
                .thenWaitUntil(() -> assertSameNetwork(helper, BESIDE_CASING, "the Voidstone touching its casing once it loaded"))
                .thenSucceed();
    }

    private static void assertSameNetwork(GameTestHelper helper, BlockPos voidstone, String what) {
        var networks = VoidstoneNetworks.of(helper.getLevel());
        helper.assertTrue(networks.store(helper.absolutePos(WELL)).isPresent()
                && networks.at(helper.absolutePos(WELL)).equals(networks.at(helper.absolutePos(voidstone))),
                "the Well is not in the network of " + what);
    }

    private static long heldInWell(GameTestHelper helper) {
        return VoidstoneNetworks.of(helper.getLevel()).store(helper.absolutePos(WELL)).map(store -> store.total()).orElse(0L);
    }

    /** @return how many motes {@link #COST_STEPS} above the pressure pay {@code well}'s cost, rounded up */
    private static int costInMotes(GameTestHelper helper, VoidWellBlockEntity well) {
        int pressure = DisplacerTests.pressure(helper, WELL);
        long each = VoidEnergy.release(pressure + COST_STEPS, pressure).orElseThrow();
        return Math.toIntExact((well.dormancy().cost() + each - 1) / each);
    }

    private static void keptWhileNotWholeAndReloaded(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        owner.setShiftKeyDown(true);
        helper.useBlock(WELL, owner);
        owner.setShiftKeyDown(false);
        owner.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Home"));
        helper.useBlock(WELL, owner);
        helper.setBlock(WELL.east(), Blocks.AIR);
        helper.startSequence()
                .thenIdle(2 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(!well.whole(helper.getLevel()), "the Well with a wall taken out is whole");
                    assertKept(helper, well, owner, "no longer whole");
                    var level = helper.getLevel();
                    var pos = helper.absolutePos(WELL);
                    var state = level.getBlockState(pos);
                    var saved = well.saveWithFullMetadata(level.registryAccess());
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    var reloaded = core(helper, WELL);
                    helper.assertTrue(reloaded != well && reloaded.owner().isEmpty(), "the core put back is not a new one");
                    reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
                    assertKept(helper, reloaded, owner, "reloaded");
                })
                .thenSucceed();
    }

    private static void assertKept(GameTestHelper helper, VoidWellBlockEntity well, ServerPlayer owner, String when) {
        helper.assertTrue(ownedBy(well, owner), "the Well " + when + " is owned by " + well.owner());
        helper.assertTrue(well.target() == FillTarget.QUARTER, "the Well " + when + " has the target " + well.target());
        helper.assertTrue(well.name().equals(Optional.of(Component.literal("Home"))), "the Well " + when + " is named " + well.name());
    }

    /** Its owner's dragons find it, with its target as it changes, in the server's listing, which needs no chunk loaded; broken, it is gone. */
    private static void listedForDragons(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        placeCore(helper, owner);
        var place = Place.of(helper.getLevel(), helper.absolutePos(WELL));
        var wells = SavedVoidWells.of(helper.getLevel().getServer());
        helper.assertTrue(wells.owned(owner.getUUID()).equals(List.of(new VoidWells.Entry(place, owner.getUUID(), FillTarget.NONE))),
                "the placed Well is listed as " + wells.owned(owner.getUUID()));
        owner.setShiftKeyDown(true);
        helper.useBlock(WELL, owner);
        helper.assertTrue(wells.owned(owner.getUUID()).equals(List.of(new VoidWells.Entry(place, owner.getUUID(), FillTarget.QUARTER))),
                "the Well with a new target is listed as " + wells.owned(owner.getUUID()));
        helper.setBlock(WELL, Blocks.AIR);
        helper.assertTrue(wells.owned(owner.getUUID()).isEmpty(), "the broken Well is still listed: " + wells.owned(owner.getUUID()));
        helper.succeed();
    }

    /**
     * Dormant, its shaft shows nothing; awake, its network's fill, Voidstone and core together, which
     * a watcher reads from what it is sent; no longer whole, nothing again. Its shaft stays open air.
     */
    private static void shaftShowsFill(GameTestHelper helper) {
        buildWell(helper);
        var owner = new Listener(helper, "well-owner");
        var well = placeCore(helper, owner);
        helper.setBlock(BESIDE_CASING, VoidworksBlocks.VOIDSTONE.get());
        int pressure = DisplacerTests.pressure(helper, WELL);
        var networks = VoidstoneNetworks.of(helper.getLevel());
        var pos = helper.absolutePos(WELL);
        helper.startSequence()
                .thenIdle(2 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(!well.dormancy().awake(), "the unpaid Well is awake");
                    helper.assertTrue(well.shaft().equals(ShaftFill.EMPTY), "the Dormant Well's shaft shows " + well.shaft());
                    VoidstoneTests.insert(helper, BESIDE_CASING, pressure + COST_STEPS, costInMotes(helper, well));
                })
                .thenWaitUntil(() -> helper.assertTrue(well.dormancy().awake(), "the Well is still Dormant, paid " + well.dormancy().paid()))
                .thenExecute(() -> {
                    VoidstoneTests.insert(helper, BESIDE_CASING, pressure + 1, 40);
                    VoidstoneTests.insert(helper, BESIDE_CASING, pressure + 2, 20);
                })
                .thenWaitUntil(() -> {
                    var network = networks.at(pos).orElseThrow();
                    var expected = ShaftFill.of(network.capacity(), network.countByGrade());
                    helper.assertTrue(expected.step() > 0 && well.shaft().equals(expected),
                            "the awake Well's shaft shows " + well.shaft() + ", not its network's " + expected);
                })
                .thenExecute(() -> {
                    var level = helper.getLevel();
                    var watched = new VoidWellBlockEntity(pos, level.getBlockState(pos));
                    watched.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), well.getUpdateTag(level.registryAccess())));
                    helper.assertTrue(watched.shaft().equals(well.shaft()), "a watcher is told the shaft shows " + watched.shaft() + ", not " + well.shaft());
                    WellStructure.layout(pos, Direction.NORTH).forEach((part, kind) -> {
                        if (kind == WellStructure.Part.OPEN) {
                            helper.assertTrue(level.getBlockState(part).isAir(), "the shaft at " + part + " is not open air");
                        }
                    });
                    helper.setBlock(WELL.east(), Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(well.shaft().equals(ShaftFill.EMPTY), "the Well no longer whole shows " + well.shaft()))
                .thenSucceed();
    }

    private static void buildWell(GameTestHelper helper) {
        buildWell(helper, WELL, Direction.NORTH);
    }

    private static boolean ownedBy(VoidWellBlockEntity well, ServerPlayer player) {
        return well.owner().equals(Optional.of(new NameAndId(player.getGameProfile())));
    }

    /** The well around a core at {@code core} facing {@code facing}, with no core in it. */
    static void buildWell(GameTestHelper helper, BlockPos core, Direction facing) {
        WellStructure.layout(helper.absolutePos(core), facing).forEach((pos, part) -> helper.getLevel().setBlockAndUpdate(pos,
                switch (part) {
                    case CASING -> VoidworksBlocks.WELL_CASING.get().defaultBlockState();
                    case RIM -> VoidworksBlocks.WELL_RIM.get().defaultBlockState();
                    case OPEN, NONE -> Blocks.AIR.defaultBlockState();
                }));
    }

    /** {@code player}, looking south into the well, places its core on the wall's bottom course. */
    private static VoidWellBlockEntity placeCore(GameTestHelper helper, ServerPlayer player) {
        var below = helper.absolutePos(WELL.below());
        player.setYRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidworksBlocks.VOID_WELL_ITEM.get()));
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        helper.assertBlockPresent(VoidworksBlocks.VOID_WELL.get(), WELL);
        return core(helper, WELL);
    }

    private static VoidWellBlockEntity core(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, VoidWellBlockEntity.class);
    }

    private static ItemStack nameTag(String name) {
        var tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return tag;
    }

    /** A player of its own on the platform north of the well, holding nothing, who keeps what it is shown above the hotbar. */
    private static final class Listener extends FakePlayer {

        final List<Component> overlays = new ArrayList<>();

        Listener(GameTestHelper helper, String name) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            var feet = helper.absoluteVec(new Vec3(4.5, 1, 0.5));
            setPos(feet.x, feet.y, feet.z);
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (overlay) {
                overlays.add(message);
            }
        }
    }
}
