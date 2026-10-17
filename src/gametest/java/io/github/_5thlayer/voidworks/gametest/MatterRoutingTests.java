// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.MatterLoad;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.StackWares;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Matter-line dragon carrying items between Well hatches on a real server, all in saved data with no
 * chunk loaded at either end (ADR 0011): a requester on one Well is filled from a provider on another,
 * in another dimension, and another owner's hatches on those Wells are never touched. A load of items,
 * components and all, comes back unchanged from a save.
 */
final class MatterRoutingTests {

    /** An Overworld Well far out, where nothing loads a chunk and no other test stands. */
    private static final BlockPos PROVIDING_WELL = new BlockPos(96_000, 64, 32_000);
    /** A Nether Well at the same place in Overworld blocks, so the dive takes only the time between dimensions. */
    private static final BlockPos REQUESTING_WELL = new BlockPos(12_000, 64, 4_000);

    private static final int REQUESTED_STACKS = 3;
    /** Longer than a trip takes at the default numbers: a stop to load, a dive between dimensions and a stop to unload. */
    private static final int A_TRIP = 900;

    private MatterRoutingTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("matter_routing_a_requester_is_filled_from_a_provider_in_another_dimension_with_no_chunk_loaded", A_TRIP,
                MatterRoutingTests::delivers);
        tests.test("matter_routing_a_load_of_items_comes_back_unchanged_from_a_save", 1, MatterRoutingTests::loadSurvivesASave);
    }

    private static void delivers(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var nether = server.getLevel(Level.NETHER);
        var owner = new NameAndId(UUID.randomUUID(), "matter-owner");
        var other = new NameAndId(UUID.randomUUID(), "someone-else");
        var providing = Place.of(overworld, PROVIDING_WELL);
        var requesting = Place.of(nether, REQUESTING_WELL);
        VoidstoneNetworks.of(overworld).rejoin(PROVIDING_WELL, StoreKind.WELL, Set.of(), Map.of());
        VoidstoneNetworks.of(nether).rejoin(REQUESTING_WELL, StoreKind.WELL, Set.of(), Map.of());
        var wells = SavedVoidWells.of(server);
        wells.put(new VoidWells.Entry(providing, owner.id(), FillTarget.NONE));
        wells.put(new VoidWells.Entry(requesting, owner.id(), FillTarget.NONE));

        var hatches = SavedWellHatches.of(server);
        var provider = hatches.put(Place.of(overworld, PROVIDING_WELL.east(3)), Optional.of(providing), Optional.of(owner), WellHatches.Mode.PROVIDER, 27);
        fill(provider, new ItemStack(Items.COBBLESTONE, 64), 5);
        var requester = hatches.put(Place.of(nether, REQUESTING_WELL.east(3)), Optional.of(requesting), Optional.of(owner), WellHatches.Mode.REQUESTER, 27);
        for (int stack = 0; stack < REQUESTED_STACKS; stack++) {
            requester.ask(Items.COBBLESTONE);
        }
        // Another's hatches on those Wells, which no dragon of the owner's touches.
        var othersProvider = hatches.put(Place.of(overworld, PROVIDING_WELL.west(3)), Optional.of(providing), Optional.of(other),
                WellHatches.Mode.PROVIDER, 27);
        fill(othersProvider, new ItemStack(Items.COBBLESTONE, 64), 1);
        var othersRequester = hatches.put(Place.of(nether, REQUESTING_WELL.west(3)), Optional.of(requesting), Optional.of(other),
                WellHatches.Mode.REQUESTER, 27);
        othersRequester.ask(Items.COBBLESTONE);

        var dragons = SavedVoidDragons.of(server);
        var dragon = dragons.add(owner.id(), Line.MATTER, providing);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(count(requester, Items.COBBLESTONE) == REQUESTED_STACKS * 64,
                            "the requester holds " + count(requester, Items.COBBLESTONE) + " cobblestone");
                    helper.assertTrue(dragons.get(dragon.id()).orElseThrow().load().isEmpty(), "the dragon still carries its load");
                })
                .thenExecute(() -> {
                    helper.assertTrue(count(provider, Items.COBBLESTONE) == 2 * 64, "the provider holds " + count(provider, Items.COBBLESTONE) + " cobblestone");
                    helper.assertTrue(count(othersProvider, Items.COBBLESTONE) == 64, "another's provider gave its cobblestone");
                    helper.assertTrue(count(othersRequester, Items.COBBLESTONE) == 0, "another's requester was filled");
                    helper.assertTrue(!loaded(overworld, PROVIDING_WELL) && !loaded(nether, REQUESTING_WELL), "a chunk at either end is loaded");
                    dragons.remove(dragon.id());
                    List.of(provider, requester, othersProvider, othersRequester).forEach(hatch -> hatches.remove(hatch.place()));
                    VoidstoneNetworks.of(overworld).leave(PROVIDING_WELL);
                    VoidstoneNetworks.of(nether).leave(REQUESTING_WELL);
                    wells.remove(providing);
                    wells.remove(requesting);
                })
                .thenSucceed();
    }

    /** A dragon carrying cobblestone and a filled shulker box, on its way with a claim on both, is saved and read back as it was. */
    private static void loadSurvivesASave(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND_BLOCK, 64))));
        var load = new MatterLoad(List.of(StackWares.of(new ItemStack(Items.COBBLESTONE, 64), registries), StackWares.of(new ItemStack(Items.COBBLESTONE, 10), registries),
                StackWares.of(box, registries)));
        helper.assertTrue(load.amount() == 3, "cobblestone in two stacks and a shulker box make " + load.amount() + " stacks, not three");
        var here = Place.of(helper.getLevel(), BlockPos.ZERO);
        var trip = new Trip(Optional.empty(), here, 3, false, Trip.Leg.TO_DESTINATION, 100, 0, 0, Optional.empty(), load.items());
        var dragon = new VoidDragonRecord(UUID.randomUUID(), UUID.randomUUID(), Line.MATTER, 80, here, load, Optional.of(trip), 0, 0);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var reread = VoidDragonRecord.CODEC.parse(ops, VoidDragonRecord.CODEC.encodeStart(ops, dragon).getOrThrow()).getOrThrow();
        helper.assertTrue(reread.equals(dragon), "the dragon reads back as " + reread + ", not " + dragon);
        var shulker = StackWares.stack(((MatterLoad) reread.load()).wares().getLast(), registries);
        helper.assertTrue(ItemStack.isSameItemSameComponents(shulker, box), "the shulker box reads back as " + shulker + ", holding "
                + shulker.get(DataComponents.CONTAINER));
        helper.succeed();
    }

    /** Fills {@code hatch} with {@code stacks} of {@code stack}, through its item faces. */
    static void fill(WellHatches.Hatch hatch, ItemStack stack, int stacks) {
        try (var transaction = Transaction.openRoot()) {
            hatch.contents().insert(ItemResource.of(stack), stack.getCount() * stacks, transaction);
            transaction.commit();
        }
    }

    /** @return how many of {@code item} {@code hatch} holds */
    static long count(WellHatches.Hatch hatch, Item item) {
        return hatch.contents().copyToList().stream().filter(stack -> stack.is(item)).mapToLong(ItemStack::getCount).sum();
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        var chunk = ChunkPos.containing(pos);
        return level.getChunkSource().hasChunk(chunk.x(), chunk.z());
    }
}
