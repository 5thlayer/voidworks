// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.mutation.Breakdown;
import io.github._5thlayer.voidworks.mutation.BreakdownsPayload;
import io.github._5thlayer.voidworks.mutation.MutationItems;
import io.github._5thlayer.voidworks.mutation.UnmakingChamberBlockEntity;
import io.github._5thlayer.voidworks.mutation.UnmakingRecipes;
import io.github._5thlayer.voidworks.mutation.UnmakingReport;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Unmaking Chamber on a real server: placed Dormant, it soaks Voidworks' shipped cost, then
 * breaks down what its input holds into its one crafting recipe's ingredients, taking the recipe's
 * count and spending 100 void energy per ingredient item from a touching mote store by the spend rule
 * at its block; each item comes back by chance at its yield. The recipes are vanilla's; the game-test
 * pack's {@code voidworks:breakdown} entries fill in sticks, which two recipes make, as two oak planks
 * for four, and bar gold blocks. The tests stand in the Overworld, where the grade above the pressure
 * releases {@link VoidEnergy#BASE} a mote, so a mote pays for one ingredient item.
 */
final class UnmakingChamberTests {

    /** The Chamber's shipped Dormant cost, in motes one step above the pressure. */
    private static final int DORMANT_MOTES = 16;

    private static final BlockPos CHAMBER = new BlockPos(4, 1, 4);
    /** Where a mote store touches the Chamber. */
    private static final BlockPos BESIDE = CHAMBER.east();

    /** Long enough to wake and make several breakdowns at the configured intervals. */
    private static final int TICKS = 300;

    private UnmakingChamberTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("unmaking_chamber_stays_dormant_until_its_shipped_cost_is_paid", TICKS, UnmakingChamberTests::staysDormantUntilPaid);
        tests.test("unmaking_chamber_reads_a_crafting_recipe_in_reverse", 1, UnmakingChamberTests::readsRecipes);
        tests.test("unmaking_chamber_breaks_down_by_the_recipes_count_and_spends_per_ingredient_item", TICKS,
                UnmakingChamberTests::breaksDownByCountAndCost);
        tests.test("unmaking_chamber_breaks_down_a_worn_enchanted_item", TICKS, UnmakingChamberTests::wornAndEnchanted);
        tests.test("unmaking_chamber_without_motes_enough_spends_nothing", TICKS, UnmakingChamberTests::cannotAfford);
        tests.test("unmaking_chamber_with_outputs_that_cannot_take_a_full_return_spends_nothing", TICKS, UnmakingChamberTests::outputsFull);
        tests.test("unmaking_chamber_forgets_its_yield_when_broken", TICKS, UnmakingChamberTests::forgetsWhenBroken);
        tests.test("unmaking_chamber_item_face_takes_only_items_with_a_breakdown_and_gives_from_every_output", 1, UnmakingChamberTests::itemFace);
        tests.test("unmaking_chamber_takes_items_from_a_hand_and_gives_the_outputs_to_an_empty_one", 1, UnmakingChamberTests::byHand);
        tests.test("unmaking_chamber_has_a_recipe", 1, UnmakingChamberTests::hasARecipe);
        tests.test("unmaking_chamber_breakdowns_sent_to_clients_are_the_chambers", 1, UnmakingChamberTests::breakdownsSentAreTheChambers);
    }

    /** Ten motes of the sixteen it costs: it soaks them, and breaks nothing down. */
    private static void staysDormantUntilPaid(GameTestHelper helper) {
        var chamber = place(helper);
        helper.assertTrue(chamber.dormancy().cost() == DORMANT_MOTES * VoidEnergy.BASE,
                "the Chamber's cost is " + chamber.dormancy().cost() + ", not the shipped " + DORMANT_MOTES * VoidEnergy.BASE);
        helper.assertTrue(dormantLook(helper), "a newly placed Chamber does not look Dormant");
        insert(chamber, Items.IRON_BLOCK, 1);
        SpendTests.placeStore(helper, BESIDE, new MoteStore().add(grade(helper), 10));
        helper.startSequence()
                .thenIdle(4 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertTrue(!chamber.dormancy().awake() && dormantLook(helper), "the Chamber woke paid " + chamber.dormancy().paid());
                    helper.assertTrue(chamber.input().is(Items.IRON_BLOCK) && outputs(chamber) == 0, "the Dormant Chamber broke its iron block down");
                    helper.assertTrue(chamber.spent() == 0, "the Dormant Chamber counts " + chamber.spent() + " spent on breakdowns");
                })
                .thenSucceed();
    }

    /**
     * An iron block is nine ingots; sixteen glass panes, six glass; a chest, eight of its tag's first
     * planks; a cake, its sugar, egg and wheat, never its milk buckets. Light gray dye has several
     * recipes, flint none, and fireworks no fixed ingredients, so none of them has a breakdown, and the pack fills in sticks and bars gold
     * blocks, each with a recipe of its own.
     */
    private static void readsRecipes(GameTestHelper helper) {
        assertBreakdown(helper, Items.IRON_BLOCK, Optional.of(new Breakdown<>(1, Map.of(Items.IRON_INGOT, 9))));
        assertBreakdown(helper, Items.GLASS_PANE, Optional.of(new Breakdown<>(16, Map.of(Items.GLASS, 6))));
        assertBreakdown(helper, Items.CHEST, Optional.of(new Breakdown<>(1, Map.of(Items.OAK_PLANKS, 8))));
        assertBreakdown(helper, Items.CAKE, Optional.of(new Breakdown<>(1, Map.of(Items.SUGAR, 2, Items.EGG, 1, Items.WHEAT, 3))));
        assertBreakdown(helper, Items.LIGHT_GRAY_DYE, Optional.empty());
        assertBreakdown(helper, Items.FLINT, Optional.empty());
        // Paper and gunpowder make plain rockets, but so does the rocket recipe that takes stars.
        assertBreakdown(helper, Items.FIREWORK_ROCKET, Optional.empty());
        assertBreakdown(helper, Items.FIREWORK_STAR, Optional.empty());
        // A banner's shaped recipe is not its only one: banner copies make it too.
        assertBreakdown(helper, Items.WHITE_BANNER, Optional.empty());
        assertBreakdown(helper, Items.STICK, Optional.of(new Breakdown<>(4, Map.of(Items.OAK_PLANKS, 2))));
        assertBreakdown(helper, Items.GOLD_BLOCK, Optional.empty());
        helper.succeed();
    }

    /**
     * Thirty-three glass panes: two breakdowns of sixteen, each six glass for six motes, and the last
     * pane waits for fifteen more. No more glass comes back than went in.
     */
    private static void breaksDownByCountAndCost(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.GLASS_PANE, 33);
        int grade = grade(helper);
        // Motes enough for a third breakdown, so only the count holds back the last pane.
        var store = new MoteStore().add(grade, DORMANT_MOTES + 3 * 6);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(chamber.input().getCount() == 1, "the input holds " + chamber.input()))
                .thenIdle(3 * VoidworksConfig.mutationInterval())
                .thenExecute(() -> {
                    helper.assertTrue(!dormantLook(helper), "the working Chamber still looks Dormant");
                    helper.assertTrue(chamber.input().getCount() == 1, "the input holds " + chamber.input() + ", not the one pane left");
                    helper.assertTrue(outputs(chamber) <= 12 && onlyHolds(chamber, Items.GLASS),
                            "the outputs hold " + outputs(chamber) + " items, more than the twelve glass two breakdowns put in, or other than glass");
                    helper.assertTrue(chamber.spent() == 2 * 6 * 100, "the Chamber counts " + chamber.spent() + " spent, not two breakdowns' 1200");
                    helper.assertTrue(chamber.currentYield() > 0.25 && chamber.currentYield() < 0.75, "the Chamber's yield is " + chamber.currentYield());
                    helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade, 6)),
                            "the store holds " + VoidSpender.countByGrade(store) + ", not the six left after its cost and two breakdowns");
                    var lines = report(helper, chamber).lines();
                    helper.assertTrue(lines.getLast().equals(Component.translatable("voidworks.jade.upgrade.stall.too_few", 15).withStyle(ChatFormatting.RED)),
                            "the Chamber's last line is " + lines.getLast() + ", not that it waits for fifteen more, in red");
                })
                .thenSucceed();
    }

    /** A damaged, enchanted, named iron sword breaks down as a new one would, into its ingots and stick. */
    private static void wornAndEnchanted(GameTestHelper helper) {
        var chamber = place(helper);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(100);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
        sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), 3);
        helper.assertTrue(chamber.items().insertFromHand(sword) == 1, "the Chamber did not take a worn, enchanted sword");
        var store = new MoteStore().add(grade(helper), DORMANT_MOTES + 3);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(chamber.input().isEmpty(), "the input still holds " + chamber.input()))
                .thenExecute(() -> {
                    helper.assertTrue(VoidSpender.countByGrade(store).isEmpty(), "the store holds " + VoidSpender.countByGrade(store));
                    for (int slot = MutationItems.FIRST_OUTPUT; slot < chamber.inventory().size(); slot++) {
                        var held = chamber.items().held(slot);
                        helper.assertTrue(held.isEmpty() || held.isComponentsPatchEmpty() && (held.is(Items.IRON_INGOT) || held.is(Items.STICK)),
                                "an output holds " + held + ", not a plain ingot or stick");
                    }
                })
                .thenSucceed();
    }

    /** Eight motes of an iron block's nine, beside worthless ones of the pressure's own grade. */
    private static void cannotAfford(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.IRON_BLOCK, 1);
        int grade = grade(helper);
        var store = new MoteStore().add(grade, DORMANT_MOTES + 8).add(grade - 1, 50);
        SpendTests.placeStore(helper, BESIDE, store);
        awakeThenIdle(helper, chamber, () -> {
            helper.assertTrue(chamber.input().is(Items.IRON_BLOCK) && outputs(chamber) == 0, "the Chamber broke down what it could not afford");
            assertStall(helper, chamber, UnmakingChamberBlockEntity.Stall.CANNOT_AFFORD);
            helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade - 1, 50, grade, 8)), "the store holds " + VoidSpender.countByGrade(store));
        });
    }

    /**
     * Every output but one full of stone, and that one holding sixty glass: four more fit, not the six
     * a breakdown of glass panes could return, so it waits and spends nothing.
     */
    private static void outputsFull(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.GLASS_PANE, 16);
        for (int slot = MutationItems.FIRST_OUTPUT; slot < chamber.inventory().size() - 1; slot++) {
            chamber.inventory().set(slot, ItemResource.of(Items.STONE), 64);
        }
        chamber.inventory().set(chamber.inventory().size() - 1, ItemResource.of(Items.GLASS), 60);
        int grade = grade(helper);
        var store = new MoteStore().add(grade, DORMANT_MOTES + 6);
        SpendTests.placeStore(helper, BESIDE, store);
        awakeThenIdle(helper, chamber, () -> {
            helper.assertTrue(chamber.input().getCount() == 16, "the input holds " + chamber.input());
            assertStall(helper, chamber, UnmakingChamberBlockEntity.Stall.OUTPUT_FULL);
            helper.assertTrue(VoidSpender.countByGrade(store).equals(Map.of(grade, 6)), "the store holds " + VoidSpender.countByGrade(store));
        });
    }

    /** One breakdown raises its yield; broken and placed again, it starts from the bottom. */
    private static void forgetsWhenBroken(GameTestHelper helper) {
        var chamber = place(helper);
        insert(chamber, Items.IRON_BLOCK, 1);
        SpendTests.placeStore(helper, BESIDE, new MoteStore().add(grade(helper), DORMANT_MOTES + 9));
        double start = VoidworksConfig.unmakingYield().start();
        helper.assertTrue(chamber.currentYield() == start, "a new Chamber's yield is " + chamber.currentYield() + ", not " + start);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(chamber.spent() == 900, "the Chamber counts " + chamber.spent() + " spent"))
                .thenExecute(() -> {
                    helper.assertTrue(chamber.currentYield() > start, "the yield did not rise from " + start);
                    var player = helper.makeMockPlayer(GameType.SURVIVAL);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                    helper.getLevel().destroyBlock(helper.absolutePos(CHAMBER), true, player);
                    var again = place(helper);
                    helper.assertTrue(again.spent() == 0 && again.currentYield() == start, "the Chamber placed again counts " + again.spent() + " spent");
                })
                .thenSucceed();
    }

    private static void itemFace(GameTestHelper helper) {
        var chamber = place(helper);
        var face = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(CHAMBER), Direction.UP);
        helper.assertTrue(face != null, "the Chamber has no item capability");
        helper.assertTrue(chamber.inventory().size() == 1 + UnmakingChamberBlockEntity.OUTPUTS, "the Chamber has no nine outputs");
        chamber.inventory().set(MutationItems.FIRST_OUTPUT + 8, ItemResource.of(Items.IRON_INGOT), 2);
        try (var transaction = Transaction.openRoot()) {
            for (var refused : new Item[] {Items.FLINT, Items.LIGHT_GRAY_DYE, Items.GOLD_BLOCK}) {
                helper.assertTrue(face.insert(MutationItems.INPUT, ItemResource.of(refused), 1, transaction) == 0,
                        "the face took " + refused + ", which has no breakdown");
            }
            helper.assertTrue(face.insert(MutationItems.FIRST_OUTPUT, ItemResource.of(Items.IRON_BLOCK), 1, transaction) == 0,
                    "the face put an iron block into an output");
            helper.assertTrue(face.insert(MutationItems.INPUT, ItemResource.of(Items.STICK), 4, transaction) == 4,
                    "the face did not take four sticks, which the pack gives a breakdown");
            helper.assertTrue(face.extract(MutationItems.FIRST_OUTPUT + 8, ItemResource.of(Items.IRON_INGOT), 2, transaction) == 2,
                    "the face did not give its last output's two ingots");
            transaction.commit();
        }
        helper.assertTrue(chamber.input().is(Items.STICK) && chamber.input().getCount() == 4, "the input holds " + chamber.input());
        helper.succeed();
    }

    private static void byHand(GameTestHelper helper) {
        var chamber = place(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var level = helper.getLevel();
        var pos = helper.absolutePos(CHAMBER);
        var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        var state = level.getBlockState(pos);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT, 3));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().getCount() == 3 && chamber.input().isEmpty(), "the Chamber took flint, which has no breakdown");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST, 2));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().isEmpty() && chamber.input().is(Items.CHEST), "the hand still holds " + player.getMainHandItem());

        chamber.inventory().set(MutationItems.FIRST_OUTPUT, ItemResource.of(Items.OAK_PLANKS), 5);
        chamber.inventory().set(MutationItems.FIRST_OUTPUT + 4, ItemResource.of(Items.OAK_PLANKS), 3);
        state.useWithoutItem(level, player, hit);
        helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 8, "the empty hand was not given every output's planks");
        helper.assertTrue(chamber.input().getCount() == 2, "the input holds " + chamber.input());
        helper.succeed();
    }

    /**
     * What clients are sent for a recipe viewer (#52) is what the Chamber breaks down, item for item:
     * the pack's sticks among them and its barred gold block not, and it comes through the payload's
     * codec unchanged.
     */
    private static void breakdownsSentAreTheChambers(GameTestHelper helper) {
        var all = UnmakingRecipes.all();
        for (var item : BuiltInRegistries.ITEM) {
            var chambers = item == Items.AIR ? Optional.<Breakdown<Item>>empty() : UnmakingRecipes.of(new ItemStack(item));
            helper.assertTrue(chambers.equals(Optional.ofNullable(all.get(item))),
                    "clients are sent " + all.get(item) + " for " + item + ", but the Chamber breaks it down as " + chambers);
        }
        helper.assertTrue(all.containsKey(Items.STICK) && !all.containsKey(Items.GOLD_BLOCK),
                "clients are not sent the pack's sticks, or are sent its barred gold block");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        BreakdownsPayload.STREAM_CODEC.encode(buffer, new BreakdownsPayload(all));
        helper.assertTrue(BreakdownsPayload.STREAM_CODEC.decode(buffer).breakdowns().equals(all), "the breakdowns payload does not come through unchanged");
        helper.succeed();
    }

    private static void hasARecipe(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "unmaking_chamber"));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no recipe " + key.identifier());
        helper.succeed();
    }

    // -- helpers --------------------------------------------------------------------------------

    private static UnmakingChamberBlockEntity place(GameTestHelper helper) {
        helper.setBlock(CHAMBER, VoidworksBlocks.UNMAKING_CHAMBER.get());
        return helper.getBlockEntity(CHAMBER, UnmakingChamberBlockEntity.class);
    }

    private static UnmakingReport report(GameTestHelper helper, UnmakingChamberBlockEntity chamber) {
        return UnmakingReport.of(chamber, helper.getLevel());
    }

    private static void assertBreakdown(GameTestHelper helper, Item item, Optional<Breakdown<Item>> expected) {
        var breakdown = UnmakingRecipes.of(new ItemStack(item));
        helper.assertTrue(breakdown.equals(expected), item + " breaks down as " + breakdown + ", not " + expected);
    }

    private static void assertStall(GameTestHelper helper, UnmakingChamberBlockEntity chamber, UnmakingChamberBlockEntity.Stall expected) {
        var stall = chamber.stall(helper.getLevel());
        helper.assertTrue(stall.equals(Optional.of(expected)), "the Chamber reports " + stall + ", not stalled by " + expected);
    }

    private static void insert(UnmakingChamberBlockEntity chamber, Item item, int count) {
        chamber.inventory().set(MutationItems.INPUT, ItemResource.of(item), count);
    }

    /** @return how many items its outputs hold together */
    private static int outputs(UnmakingChamberBlockEntity chamber) {
        int held = 0;
        for (int slot = MutationItems.FIRST_OUTPUT; slot < chamber.inventory().size(); slot++) {
            held += chamber.inventory().getAmountAsInt(slot);
        }
        return held;
    }

    private static boolean onlyHolds(UnmakingChamberBlockEntity chamber, Item item) {
        for (int slot = MutationItems.FIRST_OUTPUT; slot < chamber.inventory().size(); slot++) {
            var held = chamber.items().held(slot);
            if (!held.isEmpty() && !held.is(item)) {
                return false;
            }
        }
        return true;
    }

    /** Waits for the Chamber to wake, gives it time to try breaking down, then checks {@code check}. */
    private static void awakeThenIdle(GameTestHelper helper, UnmakingChamberBlockEntity chamber, Runnable check) {
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(chamber.dormancy().awake(), "the Chamber has not woken"))
                .thenIdle(3 * VoidworksConfig.mutationInterval())
                .thenExecute(check)
                .thenSucceed();
    }

    private static boolean dormantLook(GameTestHelper helper) {
        return helper.getBlockState(CHAMBER).getValue(Dormancy.DORMANT);
    }

    /** The grade one step above the Void Pressure at the Chamber, so a mote releases {@link VoidEnergy#BASE}. */
    private static int grade(GameTestHelper helper) {
        return DisplacerTests.pressure(helper, CHAMBER) + 1;
    }
}
