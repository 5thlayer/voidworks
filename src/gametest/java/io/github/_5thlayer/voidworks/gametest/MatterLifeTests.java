// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellHatches;
import io.github._5thlayer.voidworks.well.WellStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Matter-line dragon's life on a real server, counted in stacks: an adult one stack short of an egg
 * unloads a stack at the test's Well, whose chunk is loaded, and a Matter Dragon Egg, not a Drained
 * one, pops out of its shaft. Its other Well is in the Nether under the test's, in a chunk nothing
 * loads. When it lays and ages, and when it dies, is {@code MatterRoutingTest}'s.
 */
final class MatterLifeTests {

    /** The core, in the middle of its north wall's middle course, its floor on the platform: {@code PresenceTests}' Well. */
    private static final BlockPos WELL = new BlockPos(4, 3, 2);
    /** The height of the Nether Well, clear of every other test's under the same place. */
    private static final int UNDER_Y = 160;
    /** Waking the Well, then a stop to load, a dive and a stop to unload, at the default numbers, with room to spare. */
    private static final int TICKS = 1_200;

    private MatterLifeTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("matter_life_an_adult_matter_line_dragon_lays_a_matter_dragon_egg_in_the_shaft_of_the_well_it_unloads_its_stacks_at", TICKS,
                MatterLifeTests::laysAMatterEgg);
    }

    private static void laysAMatterEgg(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        var owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "matter-owner"));
        var feet = helper.absoluteVec(new Vec3(4.5, 1, 0.5));
        owner.setPos(feet.x, feet.y, feet.z);
        PresenceTests.wakeWell(helper, owner, WELL);
        var here = Place.of(helper.getLevel(), helper.absolutePos(WELL));
        var under = PresenceTests.under(helper, nether, UNDER_Y);
        // Relative, as GameTestHelper.relativePos turns an unrotated test half round.
        var shaft = WellStructure.shaft(WELL, Direction.NORTH);
        var life = VoidworksConfig.life(Line.MATTER);
        var hatches = SavedWellHatches.of(server);
        var name = new NameAndId(owner.getUUID(), "matter-owner");
        var dragonId = new AtomicReference<UUID>();
        var placed = new AtomicReference<List<WellHatches.Hatch>>();
        var laid = new AtomicReference<ItemEntity>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(PresenceTests.stored(helper, WELL).isPresent(), "the Well has not woken"))
                .thenExecute(() -> {
                    RoutingTests.assertUnloaded(helper, nether, under.pos());
                    VoidstoneNetworks.of(nether).rejoin(under.pos(), StoreKind.WELL, Set.of(), Map.of());
                    SavedVoidWells.of(server).put(new VoidWells.Entry(under, owner.getUUID(), FillTarget.NONE));
                    var provider = hatches.put(Place.of(nether, under.pos().east(3)), Optional.of(under), Optional.of(name), WellHatches.Mode.PROVIDER, 27);
                    try (var transaction = Transaction.openRoot()) {
                        provider.contents().insert(ItemResource.of(new ItemStack(Items.COBBLESTONE)), 64, transaction);
                        transaction.commit();
                    }
                    var requester = hatches.put(Place.of(helper.getLevel(), helper.absolutePos(WELL).east(3)), Optional.of(here), Optional.of(name),
                            WellHatches.Mode.REQUESTER, 27);
                    requester.ask(Items.COBBLESTONE);
                    placed.set(List.of(provider, requester));
                    var dragons = SavedVoidDragons.of(server);
                    var grown = dragons.add(owner.getUUID(), Line.MATTER, under);
                    // An adult, a stack short of its next egg.
                    dragons.update(new VoidDragonRecord(grown.id(), grown.owner(), grown.line(), grown.health(), grown.rest(), grown.load(), grown.trip(),
                            life.perEgg() - 1, life.stages().length()));
                    dragonId.set(grown.id());
                })
                .thenWaitUntil(() -> {
                    var eggs = helper.getEntities(EntityType.ITEM, shaft, 3.0);
                    helper.assertTrue(eggs.size() == 1, eggs.size() + " items in the shaft, not one egg");
                    laid.set(eggs.getFirst());
                })
                .thenExecute(() -> {
                    var egg = laid.get().getItem();
                    helper.assertTrue(egg.is(VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get()) && egg.getCount() == 1, "the dragon laid " + egg);
                    var after = PresenceTests.record(helper, dragonId.get());
                    helper.assertTrue(after.rest().equals(here), "the dragon laid at " + after.rest() + ", not the Well");
                    helper.assertTrue(after.towardEgg() == 0, "the dragon's count is " + after.towardEgg() + " after its egg, not none");
                    helper.assertTrue(after.cargo() == life.stages().length() + 1, "the dragon has unloaded " + after.cargo() + " stacks in its life");
                    laid.get().discard();
                    SavedVoidDragons.of(server).remove(dragonId.get());
                    placed.get().forEach(hatch -> hatches.remove(hatch.place()));
                    VoidstoneNetworks.of(nether).leave(under.pos());
                    SavedVoidWells.of(server).remove(under);
                    helper.setBlock(WELL, Blocks.AIR);
                })
                .thenSucceed();
    }
}
