// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.EggKind;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.SoakingEggBlock;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.mutation.CrucibleReport;
import io.github._5thlayer.voidworks.mutation.Families;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlockEntity;
import io.github._5thlayer.voidworks.mutation.Upgrade;
import io.github._5thlayer.voidworks.mutation.VoidCrucibleBlockEntity;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.VoidWells;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DragonEggBlock;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The Matter line founded on a real server (ADR 0011): a Mutation Chamber or a Void Crucible upgrades
 * a Drained Dragon Egg to a Matter Dragon Egg, one for one, and nothing upgrades it back; the vanilla
 * egg has no upgrade. A Matter Dragon Egg falls as the other eggs do until it soaks, soaks at the
 * price any egg does, and hatches a Dragonling of the Matter line, whose grown dragon's record is of
 * it too. A Matter-line dragon loads no motes. The tests stand in the Overworld, where a mote one
 * grade above the pressure releases {@link VoidEnergy#BASE}.
 */
final class MatterLineTests {

    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    /** Where a mote store touches the Chamber. */
    private static final BlockPos BESIDE = MACHINE.east();

    private static final BlockPos EGG = new BlockPos(4, 1, 4);
    /** Where Voidstone touches the egg, beside the test's own mote store, as in {@code EggTests}. */
    private static final BlockPos NETWORK = EGG.west();
    private static final BlockPos STORE = EGG.east();
    /** A second egg, apart from the first, touching Voidstone of its own. */
    private static final BlockPos OTHER_EGG = new BlockPos(4, 1, 1);

    /** An Overworld reservoir far out, where nothing loads a chunk and no other test stands. */
    private static final BlockPos RESERVOIR = new BlockPos(80_000, 64, 32_000);
    /** A Nether Well below its target, at the same place in Overworld blocks. */
    private static final BlockPos SHORT_WELL = new BlockPos(10_000, 64, 4_000);

    /** The Chamber's shipped Dormant cost, in motes one step above the pressure. */
    private static final int DORMANT_MOTES = 16;
    /** The egg Rung's cost, in motes one step above the pressure. */
    private static final int EGG_RUNG_MOTES = 8;
    /** Tries enough that the Rung's even chance all but surely comes up: one in a million fails them all. */
    private static final int TRIES = 20;

    private static final int TICKS = 600;
    /** Longer than a Mote-line dragon takes to load, dive and unload at the default numbers. */
    private static final int A_TRIP = 900;

    private MatterLineTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("matter_line_the_drained_egg_upgrades_to_the_matter_egg_one_for_one_and_nothing_upgrades_back", 1,
                MatterLineTests::eggRung);
        tests.test("matter_line_a_mutation_chamber_upgrades_a_drained_dragon_egg_to_a_matter_dragon_egg", TICKS,
                MatterLineTests::chamberUpgrades);
        tests.test("matter_line_a_void_crucible_works_a_drained_dragon_egg_toward_a_matter_dragon_egg", 40,
                MatterLineTests::crucibleUpgrades);
        tests.test("matter_line_a_matter_dragon_egg_not_soaking_falls_as_the_vanilla_egg_does", TICKS, MatterLineTests::eggFalls);
        tests.test("matter_line_a_matter_dragon_egg_soaks_at_any_egg_s_price_and_counts_toward_the_next", 1, MatterLineTests::eggPrice);
        tests.test("matter_line_a_matter_dragon_egg_hatches_a_dragonling_whose_grown_record_is_of_the_matter_line", TICKS,
                MatterLineTests::hatchesTheMatterLine);
        tests.test("matter_line_a_dragonling_keeps_its_line_across_a_save_and_one_from_before_lines_is_of_the_mote_line", 1,
                MatterLineTests::dragonlingKeepsItsLine);
        tests.test("matter_line_a_matter_line_dragon_never_loads_motes", A_TRIP + 100, MatterLineTests::carriesNoMotes);
    }

    /** The egg Rung is steep but one for one: 800 void energy at an even chance. */
    private static void eggRung(GameTestHelper helper) {
        var upgrade = Families.next(new ItemStack(VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get()));
        var expected = new Upgrade<Item>(VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get(), 1, 800, 0.5);
        helper.assertTrue(upgrade.equals(Optional.of(expected)), "a Drained Dragon Egg upgrades by " + upgrade + ", not " + expected);
        for (var item : new Item[] {VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get(), Items.DRAGON_EGG}) {
            var none = Families.next(new ItemStack(item));
            helper.assertTrue(none.isEmpty(), item + " upgrades by " + none);
        }
        helper.succeed();
    }

    private static void chamberUpgrades(GameTestHelper helper) {
        helper.setBlock(MACHINE, VoidworksBlocks.MUTATION_CHAMBER.get());
        var chamber = helper.getBlockEntity(MACHINE, MutationChamberBlockEntity.class);
        chamber.inventory().set(MutationChamberBlockEntity.INPUT, ItemResource.of(VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get()), 1);
        SpendTests.placeStore(helper, BESIDE, new MoteStore().add(grade(helper, MACHINE), DORMANT_MOTES + TRIES * EGG_RUNG_MOTES));
        helper.succeedWhen(() -> {
            helper.assertTrue(chamber.input().isEmpty(), "the Chamber still holds " + chamber.input());
            helper.assertTrue(ItemStack.matches(chamber.output(), new ItemStack(VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get())),
                    "the Chamber's output holds " + chamber.output() + ", not one Matter Dragon Egg");
        });
    }

    /** The Rung takes a Crucible hours, so it shows only that it takes the egg and works it toward a Matter Dragon Egg. */
    private static void crucibleUpgrades(GameTestHelper helper) {
        helper.setBlock(MACHINE, VoidworksBlocks.VOID_CRUCIBLE.get());
        var crucible = helper.getBlockEntity(MACHINE, VoidCrucibleBlockEntity.class);
        crucible.inventory().set(VoidCrucibleBlockEntity.INPUT, ItemResource.of(VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get()), 1);
        helper.succeedWhen(() -> {
            var report = CrucibleReport.of(crucible, helper.getLevel());
            helper.assertTrue(report.next().isPresent() && report.next().get().result().is(VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get())
                    && report.next().get().count() == 1, "the Crucible works toward " + report.next() + ", not one Matter Dragon Egg");
            helper.assertTrue(report.progress() > 0 && report.stall().isEmpty(), "the Crucible is not working the egg: " + report);
        });
    }

    private static void eggFalls(GameTestHelper helper) {
        helper.assertTrue(VoidworksBlocks.MATTER_DRAGON_EGG.get() instanceof DragonEggBlock, "a Matter Dragon Egg is not a dragon egg");
        var high = EGG.above(2);
        helper.setBlock(high, VoidworksBlocks.MATTER_DRAGON_EGG.get());
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, high);
            helper.assertBlockPresent(VoidworksBlocks.MATTER_DRAGON_EGG.get(), EGG);
        });
    }

    /** A first egg costs the first price whatever its Line, and a Drained egg after it the second. */
    private static void eggPrice(GameTestHelper helper) {
        helper.setBlock(NETWORK, VoidworksBlocks.VOIDSTONE.get());
        helper.setBlock(OTHER_EGG.west(), VoidworksBlocks.VOIDSTONE.get());
        var player = EggTests.player(helper);
        EggTests.placeOnTheFloor(helper, player, VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get(), EGG);
        helper.assertTrue(helper.getBlockState(EGG).getValue(SoakingEggBlock.EGG) == EggKind.MATTER, "the soaking egg forgot it was a Matter Dragon Egg");
        long first = EggTests.soaking(helper, EGG).dormancy().cost();
        helper.assertTrue(first == EggTests.eggPrice().firstPrice(), "a first Matter Dragon Egg costs " + first);
        EggTests.placeOnTheFloor(helper, player, VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get(), OTHER_EGG);
        long second = EggTests.soaking(helper, OTHER_EGG).dormancy().cost();
        helper.assertTrue(second == EggTests.eggPrice().of(1), "an egg after a Matter Dragon Egg costs " + second + ", not " + EggTests.eggPrice().of(1));
        helper.getLevel().destroyBlock(helper.absolutePos(EGG), true);
        helper.assertItemEntityCountIs(VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get(), EGG, 2.0, 1);
        helper.succeed();
    }

    /** It soaks and hatches a Matter-line Dragonling, which grows into a Matter-line dragon's record. */
    private static void hatchesTheMatterLine(GameTestHelper helper) {
        int grade = grade(helper, EGG);
        SpendTests.placeStore(helper, STORE, new MoteStore().add(grade, Math.toIntExact(EggTests.eggPrice().firstPrice() / VoidEnergy.BASE)));
        helper.setBlock(NETWORK, VoidworksBlocks.VOIDSTONE.get());
        var player = EggTests.player(helper);
        EggTests.placeOnTheFloor(helper, player, VoidworksBlocks.MATTER_DRAGON_EGG_ITEM.get(), EGG);
        var hatched = new VoidDragon[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.AIR, EGG);
                    var dragonlings = helper.getEntities(VoidworksEntities.VOID_DRAGON.get(), EGG, 3.0);
                    helper.assertTrue(dragonlings.size() == 1, dragonlings.size() + " Dragonlings hatched, not one");
                    hatched[0] = dragonlings.getFirst();
                })
                .thenExecute(() -> {
                    helper.assertTrue(hatched[0].line() == Line.MATTER, "the Dragonling is of the " + hatched[0].line() + " line");
                    hatched[0].setTicksToGrow(1);
                })
                .thenWaitUntil(() -> helper.assertTrue(hatched[0].isRemoved(), "the Dragonling has not grown up"))
                .thenExecute(() -> {
                    var owned = SavedVoidDragons.of(helper.getLevel().getServer()).all().stream()
                            .filter(dragon -> dragon.owner().equals(player.getUUID())).toList();
                    helper.assertTrue(owned.size() == 1 && owned.getFirst().line() == Line.MATTER,
                            "its owner's dragons are " + owned + ", not one of the Matter line");
                    SavedVoidDragons.of(helper.getLevel().getServer()).remove(owned.getFirst().id());
                })
                .thenSucceed();
    }

    private static void dragonlingKeepsItsLine(GameTestHelper helper) {
        var level = helper.getLevel();
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), EGG.above());
        dragonling.hatchFor(UUID.randomUUID(), Line.MATTER);
        var saved = DragonSaves.saved(helper, dragonling);
        helper.assertTrue(DragonSaves.read(helper, saved).line() == Line.MATTER, "a Matter-line Dragonling reloads of another line");
        saved.remove("line");
        helper.assertTrue(DragonSaves.read(helper, saved).line() == Line.MOTE, "a Dragonling saved before Lines reloads of the Matter line");
        dragonling.discard();
        helper.succeed();
    }


    /** At a reservoir, with a Well of its owner's short of its target, a Matter-line dragon takes nothing for it, trip after trip's time. */
    private static void carriesNoMotes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var nether = server.getLevel(Level.NETHER);
        var owner = UUID.randomUUID();
        var reservoir = Place.of(overworld, RESERVOIR);
        var shortWell = Place.of(nether, SHORT_WELL);
        VoidstoneNetworks.of(overworld).rejoin(RESERVOIR, StoreKind.WELL, Set.of(), Map.of(4, 1_000L));
        VoidstoneNetworks.of(nether).rejoin(SHORT_WELL, StoreKind.WELL, Set.of(), Map.of());
        var wells = SavedVoidWells.of(server);
        wells.put(new VoidWells.Entry(reservoir, owner, FillTarget.NONE));
        wells.put(new VoidWells.Entry(shortWell, owner, FillTarget.QUARTER));
        var dragons = SavedVoidDragons.of(server);
        var dragon = dragons.add(owner, Line.MATTER, reservoir);
        var sequence = helper.startSequence();
        for (int checked = 0; checked < A_TRIP; checked += VoidworksConfig.soakInterval()) {
            sequence.thenExecuteAfter(VoidworksConfig.soakInterval(), () -> {
                var now = dragons.get(dragon.id()).orElseThrow();
                helper.assertTrue(now.load().isEmpty() && now.trip().isEmpty(), "the Matter-line dragon took up " + now.load() + " on " + now.trip());
            });
        }
        sequence.thenExecute(() -> {
            helper.assertTrue(RoutingTests.total(overworld, RESERVOIR) == 1_000, "the reservoir holds " + RoutingTests.total(overworld, RESERVOIR));
            helper.assertTrue(RoutingTests.total(nether, SHORT_WELL) == 0, "the short Well holds " + RoutingTests.total(nether, SHORT_WELL));
            dragons.remove(dragon.id());
            VoidstoneNetworks.of(overworld).leave(RESERVOIR);
            VoidstoneNetworks.of(nether).leave(SHORT_WELL);
            wells.remove(reservoir);
            wells.remove(shortWell);
        }).thenSucceed();
    }

    /** @return the grade one above the Void Pressure at {@code pos}, worth {@link VoidEnergy#BASE} a mote there */
    private static int grade(GameTestHelper helper, BlockPos pos) {
        return DisplacerTests.pressure(helper, pos) + 1;
    }
}
