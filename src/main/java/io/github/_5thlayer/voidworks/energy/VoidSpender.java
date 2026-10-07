// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.HashMap;
import java.util.Optional;

import io.github._5thlayer.voidworks.energy.VoidEnergy.Spend;
import io.github._5thlayer.voidworks.item.MoteItem;
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
        var plan = VoidEnergy.plan(MoteItem.countByGrade(inventory), pressure, required);
        if (plan.isEmpty()) {
            return plan;
        }
        // Taken slot by slot: the motes of one grade can lie in several slots, and as different
        // resources when they differ in another component, such as a name given in an anvil.
        var left = new HashMap<>(plan.get().motes());
        // Closed without a commit, the transaction puts back whatever was taken.
        try (var transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                var resource = inventory.getResource(slot);
                if (!resource.is(VoidworksItems.MOTE.get())) {
                    continue;
                }
                int grade = MoteItem.gradeOf(resource);
                int wanted = left.getOrDefault(grade, 0);
                if (wanted > 0) {
                    left.put(grade, wanted - inventory.extract(slot, resource, wanted, transaction));
                }
            }
            if (left.values().stream().anyMatch(count -> count > 0)) {
                return Optional.empty();
            }
            transaction.commit();
        }
        return plan;
    }
}
