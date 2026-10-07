// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.github._5thlayer.voidworks.energy.VoidEnergy.Spend;
import io.github._5thlayer.voidworks.item.MoteItem;
import io.github._5thlayer.voidworks.item.VoidworksDataComponents;
import io.github._5thlayer.voidworks.item.VoidworksItems;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The seam every spender calls: release at least some void energy from an inventory, where the
 * spender stands. It joins the spend rule ({@link VoidEnergy}), the motes in the inventory and the
 * Void Pressure at the position. Mutation, void gear, zone upgrades and hatching spend through
 * it; healing a Void Dragon is the one spend that does not.
 *
 * <p>It works on any NeoForge item inventory, so a machine's own inventory, a player's
 * ({@code PlayerInventoryWrapper.of(player)}) and a vanilla container
 * ({@code VanillaContainerWrapper.of(container)}) all work.
 */
public final class VoidSpender {

    private VoidSpender() {
    }

    /**
     * Spends motes from {@code inventory} to release at least {@code required} void energy at
     * {@code pos}: the lowest grade still worth something there first, moving up the grades only as
     * needed. The spend is all or nothing: when the inventory cannot afford it, nothing is taken.
     *
     * @param pos where the spender stands: a machine's block, the player's feet
     * @return what was taken and the void energy it released, or empty when the spend is refused
     */
    public static Optional<Spend> spend(ResourceHandler<ItemResource> inventory, ServerLevel level, BlockPos pos, long required) {
        return spend(inventory, VoidPressure.at(level, pos), required);
    }

    /**
     * As {@link #spend(ResourceHandler, ServerLevel, BlockPos, long)}, for a caller that already
     * knows the Void Pressure where it stands.
     */
    static Optional<Spend> spend(ResourceHandler<ItemResource> inventory, int pressure, long required) {
        // A grade can be spelled by more than one resource: the bare item and one carrying the
        // grade component are different resources of the same grade.
        var resourcesByGrade = new TreeMap<Integer, Map<ItemResource, Integer>>();
        var countsByGrade = new LinkedHashMap<Integer, Integer>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var resource = inventory.getResource(slot);
            if (resource.isEmpty() || !resource.is(VoidworksItems.MOTE.get())) {
                continue;
            }
            int count = inventory.getAmountAsInt(slot);
            int grade = resource.getComponents().getOrDefault(VoidworksDataComponents.GRADE.get(), MoteItem.LOWEST_GRADE);
            resourcesByGrade.computeIfAbsent(grade, g -> new LinkedHashMap<>()).merge(resource, count, Integer::sum);
            countsByGrade.merge(grade, count, Integer::sum);
        }

        var plan = VoidEnergy.plan(countsByGrade, pressure, required);
        if (plan.isEmpty()) {
            return plan;
        }
        // Closed without a commit, the transaction puts back whatever was taken.
        try (var transaction = Transaction.openRoot()) {
            for (var taking : plan.get().motes().entrySet()) {
                int left = taking.getValue();
                for (var held : resourcesByGrade.get(taking.getKey()).entrySet()) {
                    left -= inventory.extract(held.getKey(), Math.min(left, held.getValue()), transaction);
                }
                if (left > 0) {
                    return Optional.empty();
                }
            }
            transaction.commit();
        }
        return plan;
    }
}
