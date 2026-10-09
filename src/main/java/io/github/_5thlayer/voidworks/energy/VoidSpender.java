// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.HashMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.primitives.Ints;
import io.github._5thlayer.voidworks.energy.VoidEnergy.Spend;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The seam every spender calls: release at least some void energy from a mote store, where the
 * spender stands. It joins the spend rule ({@link VoidEnergy}), the motes in the store and the
 * Void Pressure at the position. Mutation, void gear, zone upgrades and hatching spend through
 * it; healing a Void Dragon is the one spend that does not.
 *
 * <p>It works on any {@code ResourceHandler<MoteResource>}: a block's, as
 * {@link io.github._5thlayer.voidworks.VoidworksCapabilities#MOTES} provides it, or a machine's own.
 */
public final class VoidSpender {

    private VoidSpender() {
    }

    /**
     * Spends motes from {@code motes} to release at least {@code required} void energy at
     * {@code pos}: the lowest grade still worth something there first, moving up the grades only as
     * needed. The spend is all or nothing: when the store cannot afford it, nothing is taken.
     *
     * @param pos where the spender stands: a machine's block, the player's feet
     * @return what was taken and the void energy it released, or empty when the spend is refused
     */
    public static Optional<Spend> spend(ResourceHandler<MoteResource> motes, ServerLevel level, BlockPos pos, long required) {
        return spend(motes, VoidPressure.at(level, pos), required);
    }

    /**
     * As {@link #spend(ResourceHandler, ServerLevel, BlockPos, long)}, for a caller that already
     * knows the Void Pressure where it stands.
     */
    static Optional<Spend> spend(ResourceHandler<MoteResource> motes, int pressure, long required) {
        var plan = VoidEnergy.plan(countByGrade(motes), pressure, required);
        if (plan.isEmpty()) {
            return plan;
        }
        // Taken slot by slot: a store may keep the motes of one grade in several slots.
        var left = new HashMap<>(plan.get().motes());
        // Closed without a commit, the transaction puts back whatever was taken.
        try (var transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < motes.size(); slot++) {
                var resource = motes.getResource(slot);
                if (resource.isEmpty()) {
                    continue;
                }
                int wanted = left.getOrDefault(resource.grade(), 0);
                if (wanted > 0) {
                    left.put(resource.grade(), wanted - motes.extract(slot, resource, wanted, transaction));
                }
            }
            if (left.values().stream().anyMatch(count -> count > 0)) {
                return Optional.empty();
            }
            transaction.commit();
        }
        return plan;
    }

    /**
     * The motes in {@code motes} as grade to count, lowest grade first. A count past
     * {@link Integer#MAX_VALUE} stays there.
     */
    public static SortedMap<Integer, Integer> countByGrade(ResourceHandler<MoteResource> motes) {
        var counts = new TreeMap<Integer, Integer>();
        for (int slot = 0; slot < motes.size(); slot++) {
            var resource = motes.getResource(slot);
            long amount = motes.getAmountAsLong(slot);
            if (!resource.isEmpty() && amount > 0) {
                counts.merge(resource.grade(), Ints.saturatedCast(amount), (a, b) -> Ints.saturatedCast((long) a + b));
            }
        }
        return counts;
    }
}
