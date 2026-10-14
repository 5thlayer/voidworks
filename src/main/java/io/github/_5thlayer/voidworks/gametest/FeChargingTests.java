// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.Map;

import com.mojang.serialization.Codec;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import io.github._5thlayer.voidworks.siphon.FeCharging;
import io.github._5thlayer.voidworks.siphon.SiphonCharging;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.SiphonMotes;
import io.github._5thlayer.voidworks.siphon.SiphonTier;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

/**
 * A Void Siphon set to charge charging the FE items its holder wears or holds, through the Siphon's
 * step each interval, on a real server (ADR 0007). A vanilla item, a lightning rod, stands in for
 * another mod's FE item: the game test run alone gives it NeoForge's item energy capability, so
 * the tests need no FE mod. The platform is in the Overworld, where a mote one grade above the
 * pressure releases the base void energy.
 */
final class FeChargingTests {

    /** The stand-in FE item. */
    static final Item FE_ITEM = Items.LIGHTNING_ROD;

    /** The FE the stand-in holds, with no limit to an insert but its room. */
    static final int CAPACITY = 100_000;

    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Voidworks.MOD_ID);

    /** {@code voidworks:test_energy}: the FE the stand-in holds. */
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            COMPONENTS.registerComponentType("test_energy", builder -> builder.persistent(Codec.INT));

    private static final BlockPos FLOOR = new BlockPos(4, 0, 4);
    private static final BlockPos ABOVE = FLOOR.above();

    private FeChargingTests() {
    }

    /** For the game test run alone: the stand-in's energy and its capability. */
    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        modBus.addListener(FeChargingTests::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ENERGY.get(), CAPACITY), FE_ITEM);
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_charging_siphon_off_voidstone_charges_a_worn_fe_item", 1, FeChargingTests::chargesOffVoidstone);
        tests.test("an_emptying_siphon_charges_no_fe_item", 1, FeChargingTests::emptyingChargesNothing);
        tests.test("a_charging_siphon_on_voidstone_fills_from_the_network_and_charges_at_once", 1, FeChargingTests::passesThrough);
        tests.test("a_siphon_charges_no_fe_item_elsewhere_in_the_inventory", 1, FeChargingTests::notElsewhere);
        tests.test("a_mote_spent_in_the_nether_charges_twice_the_fe", 1, FeChargingTests::netherChargesTwice);
        tests.test("a_siphon_spends_the_lowest_grade_worth_something_into_fe_first", 1, FeChargingTests::lowestWorthFirst);
        tests.test("a_siphon_spends_no_mote_whose_fe_would_not_fit", 1, FeChargingTests::noMoteThatWouldNotFit);
        tests.test("a_mote_fe_is_shared_between_fe_items_with_room", 1, FeChargingTests::sharedBetweenItems);
        tests.test("a_siphon_charges_at_most_its_rate_into_fe_items", 1, FeChargingTests::atMostTheRate);
        tests.test("fe_items_charge_chest_first", 1, FeChargingTests::chestFirst);
        tests.test("siphons_charge_fe_items_in_inventory_order", 1, FeChargingTests::siphonsInOrder);
    }

    private static void chargesOffVoidstone(GameTestHelper helper) {
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), 3));
        wear(player, EquipmentSlot.CHEST, 0);
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, 3 * fe(helper));
        assertMotes(helper, player.getMainHandItem(), Map.of());
        helper.succeed();
    }

    private static void emptyingChargesNothing(GameTestHelper helper) {
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), 3));
        player.getMainHandItem().set(VoidworksItems.SIPHON_MODE.get(), SiphonMode.EMPTY);
        wear(player, EquipmentSlot.CHEST, 0);
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, 0);
        assertMotes(helper, player.getMainHandItem(), Map.of(grade(helper), 3));
        helper.succeed();
    }

    /** Five motes in the network, room for two: the Siphon takes all five and spends two. */
    private static void passesThrough(GameTestHelper helper) {
        helper.assertTrue(VoidworksConfig.siphonRate(SiphonTier.BASE) >= 5, "this test needs a rate of at least 5");
        helper.setBlock(FLOOR, VoidworksBlocks.VOIDSTONE.get());
        VoidstoneTests.insert(helper, FLOOR, grade(helper), 5);
        var player = player(helper, SiphonMotes.EMPTY);
        wear(player, EquipmentSlot.CHEST, CAPACITY - 2 * fe(helper));
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, CAPACITY);
        assertMotes(helper, player.getMainHandItem(), Map.of(grade(helper), 3));
        helper.succeed();
    }

    private static void notElsewhere(GameTestHelper helper) {
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), 3));
        player.getInventory().setItem(9, new ItemStack(FE_ITEM));
        SiphonCharging.step(helper.getLevel(), player);
        var stored = player.getInventory().getItem(9).getOrDefault(ENERGY.get(), 0);
        helper.assertTrue(stored == 0, "the FE item in the inventory holds " + stored);
        assertMotes(helper, player.getMainHandItem(), Map.of(grade(helper), 3));
        helper.succeed();
    }

    /** The Overworld's grade, one step above it, is two above the Nether's pressure. */
    private static void netherChargesTwice(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), 1));
        int pressure = VoidPressure.at(nether, player.blockPosition());
        helper.assertTrue(pressure == pressure(helper) - 1, "the Nether's pressure " + pressure + " is not one below the Overworld's");
        wear(player, EquipmentSlot.CHEST, 0);
        // Its chunk is not loaded, so the step's exchange with Voidstone is left out.
        FeCharging.charge(nether, player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, 2 * fe(helper));
        helper.succeed();
    }

    /** Motes worth nothing here, one step and two steps above: room for one step's FE takes the one step. */
    private static void lowestWorthFirst(GameTestHelper helper) {
        int grade = grade(helper);
        var player = player(helper, SiphonMotes.EMPTY.with(grade - 1, 2).with(grade, 1).with(grade + 1, 1));
        wear(player, EquipmentSlot.CHEST, CAPACITY - fe(helper));
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, CAPACITY);
        assertMotes(helper, player.getMainHandItem(), Map.of(grade - 1, 2, grade + 1, 1));
        helper.succeed();
    }

    /** A mote two steps up releases twice a step's FE, and the room is a step and a half. */
    private static void noMoteThatWouldNotFit(GameTestHelper helper) {
        int grade = grade(helper);
        var player = player(helper, SiphonMotes.EMPTY.with(grade + 1, 1));
        int stored = CAPACITY - fe(helper) * 3 / 2;
        wear(player, EquipmentSlot.CHEST, stored);
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, stored);
        assertMotes(helper, player.getMainHandItem(), Map.of(grade + 1, 1));
        helper.succeed();
    }

    private static void sharedBetweenItems(GameTestHelper helper) {
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), 1));
        wear(player, EquipmentSlot.CHEST, CAPACITY - fe(helper) / 2);
        wear(player, EquipmentSlot.LEGS, CAPACITY - fe(helper) / 2);
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, CAPACITY);
        assertEnergy(helper, player, EquipmentSlot.LEGS, CAPACITY);
        assertMotes(helper, player.getMainHandItem(), Map.of());
        helper.succeed();
    }

    private static void atMostTheRate(GameTestHelper helper) {
        int rate = VoidworksConfig.siphonRate(SiphonTier.BASE);
        helper.assertTrue((long) (rate + 3) * fe(helper) <= CAPACITY, "the stand-in has no room for more than the rate");
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), rate + 3));
        wear(player, EquipmentSlot.CHEST, 0);
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, rate * fe(helper));
        assertMotes(helper, player.getMainHandItem(), Map.of(grade(helper), 3));
        helper.succeed();
    }

    /** Two motes, room for one in the chest: the chest fills, and the legs take the other. */
    private static void chestFirst(GameTestHelper helper) {
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), 2));
        wear(player, EquipmentSlot.LEGS, 0);
        wear(player, EquipmentSlot.CHEST, CAPACITY - fe(helper));
        SiphonCharging.step(helper.getLevel(), player);
        assertEnergy(helper, player, EquipmentSlot.CHEST, CAPACITY);
        assertEnergy(helper, player, EquipmentSlot.LEGS, fe(helper));
        helper.succeed();
    }

    /** Room for one mote, and a Siphon holding one in each of the first two slots: the first spends it. */
    private static void siphonsInOrder(GameTestHelper helper) {
        var player = player(helper, SiphonMotes.EMPTY.with(grade(helper), 1));
        var second = new ItemStack(VoidworksItems.VOID_SIPHON.get());
        second.set(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY.with(grade(helper), 1));
        int slot = player.getInventory().getSelectedSlot() + 1;
        player.getInventory().setItem(slot, second);
        wear(player, EquipmentSlot.CHEST, CAPACITY - fe(helper));
        SiphonCharging.step(helper.getLevel(), player);
        assertMotes(helper, player.getMainHandItem(), Map.of());
        assertMotes(helper, player.getInventory().getItem(slot), Map.of(grade(helper), 1));
        helper.succeed();
    }

    // -- helpers --------------------------------------------------------------------------------

    /** A survival player standing on the platform, holding a Void Siphon of {@code motes} in the main hand. */
    private static Player player(GameTestHelper helper, SiphonMotes motes) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var feet = helper.absoluteVec(ABOVE.getBottomCenter());
        player.setPos(feet.x, feet.y, feet.z);
        var siphon = new ItemStack(VoidworksItems.VOID_SIPHON.get());
        siphon.set(VoidworksItems.SIPHON_MOTES.get(), motes);
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), siphon);
        return player;
    }

    /**
     * Puts the stand-in FE item, holding {@code stored} FE, in {@code slot}, equippable there as
     * armour that runs on FE is: an armour slot takes back only what can be worn in it.
     */
    private static void wear(Player player, EquipmentSlot slot, int stored) {
        var item = new ItemStack(FE_ITEM);
        item.set(ENERGY.get(), stored);
        if (slot.isArmor()) {
            item.set(DataComponents.EQUIPPABLE, Equippable.builder(slot).build());
        }
        player.setItemSlot(slot, item);
    }

    private static void assertEnergy(GameTestHelper helper, Player player, EquipmentSlot slot, long expected) {
        int stored = player.getItemBySlot(slot).getOrDefault(ENERGY.get(), 0);
        helper.assertTrue(stored == expected, "the FE item in " + slot.getName() + " holds " + stored + " FE, not " + expected);
    }

    private static void assertMotes(GameTestHelper helper, ItemStack siphon, Map<Integer, Integer> expected) {
        var held = VoidSiphonItem.heldMotes(siphon).byGrade();
        helper.assertTrue(held.equals(expected), "the Siphon holds " + held + ", not " + expected);
    }

    /** The platform's Void Pressure, where the player stands. */
    private static int pressure(GameTestHelper helper) {
        return VoidPressure.at(helper.getLevel(), helper.absolutePos(ABOVE));
    }

    /** The grade one step above the platform's pressure, which the platform harvests. */
    private static int grade(GameTestHelper helper) {
        return pressure(helper) + 1;
    }

    /** The FE one mote one step above the platform's pressure charges. */
    private static int fe(GameTestHelper helper) {
        return Math.toIntExact(VoidEnergy.release(grade(helper), pressure(helper)).orElseThrow() * VoidworksConfig.fePerVoidEnergy());
    }
}
