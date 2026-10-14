// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A Void Siphon set to charge charges the FE items its holder wears or holds: any mod's item that
 * takes FE through NeoForge's item energy capability (ADR 0007). Voidworks makes no gear of its own.
 *
 * <p>Each Siphon, in inventory order, spends its motes by the spend rule at the player's feet, the
 * lowest grade still worth something first, and the void energy they release becomes
 * {@link VoidworksConfig#fePerVoidEnergy() FE} in the items, the chest first, then the legs, the
 * feet, the head, the main hand and the off hand. It spends a mote only when the items have room
 * for all the FE it releases, so neither is lost, and at most its tier's rate of motes a step. No
 * other inventory slot is charged, so a Siphon never fills batteries to carry to a base.
 */
public final class FeCharging {

    /** The slots charged, in order. */
    private static final List<EquipmentSlot> SLOTS =
            List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    private FeCharging() {
    }

    /**
     * Charges the FE items {@code player} wears or holds from each Siphon they carry set to charge,
     * once.
     *
     * @return the FE charged, from every Siphon together
     */
    public static long charge(ServerLevel level, Player player) {
        if (!wearsOrHolds(player)) {
            return 0;
        }
        int pressure = VoidPressure.at(level, player.blockPosition());
        var inventory = PlayerInventoryWrapper.of(player);
        long charged = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof VoidSiphonItem siphon && VoidSiphonItem.mode(stack) == SiphonMode.CHARGE) {
                // Asked again for each Siphon, as the one before may have filled them.
                charged += chargeFrom(VoidSiphonItem.moteStore(ItemAccess.forPlayerSlot(player, slot)), siphon.rate(), feItems(player), pressure);
            }
        }
        return charged;
    }

    /** @return whether {@code player} wears or holds an FE item, which a Siphon set to charge charges */
    public static boolean wearsOrHolds(Player player) {
        return !feItems(player).isEmpty();
    }

    /** Spends what {@code siphon} can of its motes into {@code items}, all or nothing in one transaction. */
    private static long chargeFrom(ResourceHandler<MoteResource> siphon, int rate, List<EnergyHandler> items, int pressure) {
        long room = 0;
        for (var item : items) {
            room += room(item);
        }
        var spend = plan(VoidSpender.countByGrade(siphon), pressure, rate, room);
        long fe = 0;
        for (var entry : spend.entrySet()) {
            fe += entry.getValue() * fe(entry.getKey(), pressure);
        }
        if (fe == 0) {
            return 0;
        }
        try (var transaction = Transaction.openRoot()) {
            for (var entry : spend.entrySet()) {
                if (siphon.extract(MoteResource.of(entry.getKey()), entry.getValue(), transaction) != entry.getValue()) {
                    return 0;
                }
            }
            long left = fe;
            for (var item : items) {
                left -= item.insert((int) Math.min(left, Integer.MAX_VALUE), transaction);
            }
            // An item that took less than it offered room for: nothing is spent rather than lost.
            if (left > 0) {
                return 0;
            }
            transaction.commit();
        }
        return fe;
    }

    /**
     * The motes to spend, grade to count: the lowest grade worth something at {@code pressure}
     * first, as many whole motes as the {@code room} in FE takes, up to {@code rate}. A grade
     * whose motes do not all fit ends it, since every higher grade releases more.
     */
    private static Map<Integer, Integer> plan(Map<Integer, Integer> motes, int pressure, int rate, long room) {
        var spend = new TreeMap<Integer, Integer>();
        int left = rate;
        for (var entry : motes.entrySet()) {
            long each = fe(entry.getKey(), pressure);
            if (each == 0) {
                continue;
            }
            int count = (int) Math.min(Math.min(entry.getValue(), left), room / each);
            if (count > 0) {
                spend.put(entry.getKey(), count);
                left -= count;
                room -= count * each;
            }
            if (count < entry.getValue()) {
                break;
            }
        }
        return spend;
    }

    /** @return the FE one mote of {@code grade} charges at {@code pressure}, or 0 where it is worth nothing */
    private static long fe(int grade, int pressure) {
        return VoidEnergy.release(grade, pressure).orElse(0) * VoidworksConfig.fePerVoidEnergy();
    }

    /** @return how much FE {@code item} takes now, in one insert, without taking it */
    private static long room(EnergyHandler item) {
        // Closed without a commit, the transaction puts back what was inserted.
        try (var transaction = Transaction.openRoot()) {
            return item.insert(Integer.MAX_VALUE, transaction);
        }
    }

    /** @return the energy handlers of the FE items {@code player} wears or holds, in the order they are charged */
    private static List<EnergyHandler> feItems(Player player) {
        var items = new ArrayList<EnergyHandler>();
        for (var slot : SLOTS) {
            var handler = ItemAccess.forPlayerSlot(player, index(player, slot)).getCapability(Capabilities.Energy.ITEM);
            if (handler != null) {
                items.add(handler);
            }
        }
        return items;
    }

    /** @return the inventory slot {@code slot} is */
    private static int index(Player player, EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> player.getInventory().getSelectedSlot();
            case OFFHAND -> Inventory.SLOT_OFFHAND;
            default -> slot.getIndex(Inventory.INVENTORY_SIZE);
        };
    }
}
