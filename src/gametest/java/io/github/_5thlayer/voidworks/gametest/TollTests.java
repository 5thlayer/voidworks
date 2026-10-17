// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.DragonReport;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.MatterLoad;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.StackWares;
import io.github._5thlayer.voidworks.dragon.Toll;
import io.github._5thlayer.voidworks.network.MoteNetwork;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.HatchReport;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * The Toll a Matter-line load pays as it loads, on a real server (spec § Moving matter, Toll): out of the
 * departure Well's network by the spend rule, priced to the destination, shown on the dragon and, as
 * what a network can pay for, on a provider hatch. Its price is datapack data a pack overrides.
 */
final class TollTests {

    /** Where each test's Nether Well stands, far out, where nothing loads a chunk and no other test stands. */
    private static final BlockPos PAYING_WELL = new BlockPos(12_100, 64, 4_100);
    private static final BlockPos SHOWING_WELL = new BlockPos(12_100, 64, 4_300);
    private static final BlockPos DOUBLED_WELL = new BlockPos(12_100, 64, 4_500);
    /** Another Overworld Well, whose network holds nothing. */
    private static final BlockPos POOR_WELL = new BlockPos(97_000, 64, 36_000);

    /** The pack under {@code gametest_packs/} that doubles the Toll's price. */
    private static final String DOUBLES_THE_TOLL = "doubles_the_toll";

    /** Longer than a trip takes at the default numbers: a stop to load, a dive between dimensions and a stop to unload. */
    private static final int A_TRIP = 900;

    private TollTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("toll_a_load_pays_as_it_loads_from_the_departure_network_priced_to_its_destination_and_its_dragon_shows_it", A_TRIP,
                TollTests::paidAtLoading);
        tests.test("toll_a_part_stack_pays_its_share_and_a_filled_shulker_box_what_it_holds_plus_one_at_the_shipped_price", 1,
                TollTests::stackEquivalents);
        tests.test("toll_a_provider_hatch_shows_how_many_stacks_its_network_can_pay_for_and_stalls_when_none", 1, TollTests::providerShowsIt);
        tests.testWithPack(DOUBLES_THE_TOLL, "toll_a_datapack_overrides_its_price", 1, TollTests::packOverrides);
    }

    private static void paidAtLoading(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = new NameAndId(UUID.randomUUID(), "toll-payer");
        var link = Link.of(server, owner, PAYING_WELL, Map.of(2, 2_000L));
        var provider = link.provider();
        MatterRoutingTests.fill(provider, new ItemStack(Items.COBBLESTONE, 64), 5);
        var requester = link.requester(Items.COBBLESTONE, 3);
        var dragons = SavedVoidDragons.of(server);
        var dragon = dragons.add(owner.id(), Line.MATTER, link.providing());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!dragons.get(dragon.id()).orElseThrow().load().isEmpty(), "the dragon has loaded nothing"))
                .thenExecute(() -> {
                    var loaded = dragons.get(dragon.id()).orElseThrow();
                    var load = (MatterLoad) loaded.load();
                    // Three stacks lifted a step, from the Nether to the Overworld: 512 base void energy each, paid in grade-2 motes worth one each.
                    helper.assertTrue(load.amount() == 3 && load.toll() == 3 * 512, "the dragon loaded " + load + ", not three stacks for a Toll of 1,536");
                    helper.assertTrue(link.motes().equals(Map.of(2, 2_000L - 3 * 512)),
                            "the departure network holds " + link.motes() + " after paying the Toll");
                    var report = DragonReport.of(loaded, place -> Component.empty(), Optional.empty(), VoidworksConfig.lifeStages(Line.MATTER),
                            VoidworksConfig.dragonMatterLoad());
                    var line = Component.translatable("voidworks.jade.dragon.stacks", 3L, (long) VoidworksConfig.dragonMatterLoad(), 3L * 512);
                    helper.assertTrue(report.tail().getFirst().equals(line), "the dragon shows " + report.tail().getFirst() + ", not " + line);
                })
                .thenWaitUntil(() -> helper.assertTrue(MatterRoutingTests.count(requester, Items.COBBLESTONE) == 3 * 64,
                        "the requester holds " + MatterRoutingTests.count(requester, Items.COBBLESTONE) + " cobblestone"))
                .thenExecute(() -> {
                    // Unloading pays nothing more.
                    helper.assertTrue(link.motes().equals(Map.of(2, 2_000L - 3 * 512)),
                            "the departure network holds " + link.motes() + " after the delivery");
                    dragons.remove(dragon.id());
                    link.remove();
                })
                .thenSucceed();
    }

    private static void stackEquivalents(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var toll = Toll.of(VoidworksBlocks.WELL_HATCH.get().defaultBlockState());
        helper.assertTrue(toll.equals(new Toll(256)), "the shipped Toll is " + toll + ", not 256 base void energy a stack-equivalent");
        var half = StackWares.of(new ItemStack(Items.COBBLESTONE, 32), registries);
        helper.assertTrue(toll.of(half, 2, 2) == 128, "half a stack of cobblestone pays " + toll.of(half, 2, 2) + ", not 128");
        var pearls = StackWares.of(new ItemStack(Items.ENDER_PEARL, 16), registries);
        helper.assertTrue(toll.of(pearls, 2, 2) == 256, "16 ender pearls pay " + toll.of(pearls, 2, 2) + ", not 256");
        // A shulker box holding 26 stacks of diamond blocks and a bundle of 8 ender pearls: 26, the bundle 1 and its pearls 1/2, and 1 for the box.
        var bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(
                List.of(ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.ENDER_PEARL, 8)))));
        var held = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 26; slot++) {
            held.add(new ItemStack(Items.DIAMOND_BLOCK, 64));
        }
        held.add(bundle);
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(held));
        var ware = StackWares.of(box, registries);
        long expected = 26 * 256 + 256 + 128 + 256;
        helper.assertTrue(toll.of(ware, 3, 3) == expected, "the filled shulker box pays " + toll.of(ware, 3, 3) + ", not " + expected
                + ", holding " + ware.held());
        helper.succeed();
    }

    private static void providerShowsIt(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = new NameAndId(UUID.randomUUID(), "toll-shower");
        // Worth 1,024 base void energy in the Nether: two stacks lifted a step to the Overworld, where they are wanted.
        var link = Link.of(server, owner, SHOWING_WELL, Map.of(2, 1_024L));
        var provider = link.provider();
        MatterRoutingTests.fill(provider, new ItemStack(Items.COBBLESTONE, 64), 9);
        var requester = link.requester(Items.COBBLESTONE, 9);
        var payable = Component.translatable("voidworks.jade.hatch.payable", 9L, 2L);
        helper.assertTrue(lines(server, provider).equals(List.of(payable)), "the provider shows " + lines(server, provider) + ", not " + payable);
        helper.assertTrue(lines(server, requester).equals(List.of(Component.translatable("voidworks.jade.hatch.requesting"))),
                "the requester shows " + lines(server, requester));
        // A provider on a network holding nothing stalls.
        var poor = Place.of(server.overworld(), POOR_WELL);
        VoidstoneNetworks.of(server.overworld()).rejoin(POOR_WELL, StoreKind.WELL, Set.of(), Map.of());
        SavedVoidWells.of(server).put(new VoidWells.Entry(poor, owner.id(), FillTarget.NONE));
        var stalled = SavedWellHatches.of(server).put(Place.of(server.overworld(), POOR_WELL.east(3)), Optional.of(poor), Optional.of(owner),
                WellHatches.Mode.PROVIDER, 27);
        MatterRoutingTests.fill(stalled, new ItemStack(Items.COBBLESTONE, 64), 1);
        var stall = Component.translatable("voidworks.jade.hatch.stall.toll");
        helper.assertTrue(lines(server, stalled).equals(List.of(stall)), "the provider on a network holding nothing shows " + lines(server, stalled));
        SavedWellHatches.of(server).remove(stalled.place());
        VoidstoneNetworks.of(server.overworld()).leave(POOR_WELL);
        SavedVoidWells.of(server).remove(poor);
        link.remove();
        helper.succeed();
    }

    private static void packOverrides(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var toll = Toll.of(VoidworksBlocks.WELL_HATCH.get().defaultBlockState());
        helper.assertTrue(toll.equals(new Toll(512)), "with the pack on, the Toll is " + toll + ", not 512 base void energy a stack-equivalent");
        // Its routing prices with it: 1,024 pays for two stacks on a level trip, wanted nowhere else.
        var link = Link.of(server, new NameAndId(UUID.randomUUID(), "toll-doubler"), DOUBLED_WELL, Map.of(2, 1_024L));
        var provider = link.provider();
        MatterRoutingTests.fill(provider, new ItemStack(Items.COBBLESTONE, 64), 9);
        var payable = Component.translatable("voidworks.jade.hatch.payable", 9L, 2L);
        helper.assertTrue(lines(server, provider).equals(List.of(payable)), "the provider shows " + lines(server, provider) + ", not " + payable);
        link.remove();
        helper.succeed();
    }

    private static List<Component> lines(MinecraftServer server, WellHatches.Hatch hatch) {
        return HatchReport.of(hatch, server).lines();
    }

    /**
     * An owner's Nether Well at {@code at}, its network holding {@code motes}, and Overworld Well at the same
     * place in Overworld blocks, so a dive takes only the time between dimensions, each on a network of its
     * own, and the hatches put against them. The game-test pack sets the End's pressure far above the
     * shipped one, so the link climbs from the Nether, a step below the Overworld.
     */
    private record Link(MinecraftServer server, NameAndId owner, BlockPos at, List<WellHatches.Hatch> hatches) {

        static Link of(MinecraftServer server, NameAndId owner, BlockPos at, Map<Integer, Long> motes) {
            VoidstoneNetworks.of(nether(server)).rejoin(at, StoreKind.WELL, Set.of(), motes);
            VoidstoneNetworks.of(server.overworld()).rejoin(over(at), StoreKind.WELL, Set.of(), Map.of());
            var link = new Link(server, owner, at, new ArrayList<>());
            SavedVoidWells.of(server).put(new VoidWells.Entry(link.providing(), owner.id(), FillTarget.NONE));
            SavedVoidWells.of(server).put(new VoidWells.Entry(link.requesting(), owner.id(), FillTarget.NONE));
            return link;
        }

        Place providing() {
            return Place.of(nether(server), at);
        }

        Place requesting() {
            return Place.of(server.overworld(), over(at));
        }

        /** @return a provider against the providing Well */
        WellHatches.Hatch provider() {
            var hatch = SavedWellHatches.of(server).put(Place.of(nether(server), at.east(3)), Optional.of(providing()), Optional.of(owner),
                    WellHatches.Mode.PROVIDER, 27);
            hatches.add(hatch);
            return hatch;
        }

        /** @return a requester against the requesting Well, asking for {@code stacks} of {@code item} */
        WellHatches.Hatch requester(Item item, int stacks) {
            var hatch = SavedWellHatches.of(server).put(Place.of(server.overworld(), over(at).east(3)), Optional.of(requesting()), Optional.of(owner),
                    WellHatches.Mode.REQUESTER, 27);
            for (int stack = 0; stack < stacks; stack++) {
                hatch.ask(item);
            }
            hatches.add(hatch);
            return hatch;
        }

        /** @return what the providing Well's network holds, of the grades it holds any of */
        SortedMap<Integer, Long> motes() {
            var motes = new TreeMap<>(VoidstoneNetworks.of(nether(server)).at(at).map(MoteNetwork::countByGrade).orElseThrow());
            motes.values().removeIf(count -> count == 0);
            return motes;
        }

        void remove() {
            hatches.forEach(hatch -> SavedWellHatches.of(server).remove(hatch.place()));
            VoidstoneNetworks.of(nether(server)).leave(at);
            VoidstoneNetworks.of(server.overworld()).leave(over(at));
            SavedVoidWells.of(server).remove(providing());
            SavedVoidWells.of(server).remove(requesting());
        }

        private static ServerLevel nether(MinecraftServer server) {
            return server.getLevel(Level.NETHER);
        }

        /** @return the place in the Overworld that {@code nether} in the Nether stands at, in Overworld blocks */
        private static BlockPos over(BlockPos nether) {
            return new BlockPos(nether.getX() * 8, nether.getY(), nether.getZ() * 8);
        }
    }
}
