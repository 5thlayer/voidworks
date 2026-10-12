// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        if (plan.isEmpty() || take(List.of(motes), plan.get()).isEmpty()) {
            return Optional.empty();
        }
        return plan;
    }

    /**
     * Spends motes from {@code motes} to release as much void energy at {@code pos} as they hold, up
     * to {@code most}, by the same rule as {@link #spend}: what a Dormant machine soaks. Where the
     * motes are worth nothing, it takes nothing.
     *
     * @return what was taken and the void energy it released, with no motes when nothing was
     */
    public static Spend spendUpTo(ResourceHandler<MoteResource> motes, ServerLevel level, BlockPos pos, long most) {
        var plan = VoidEnergy.planUpTo(countByGrade(motes), VoidPressure.at(level, pos), most);
        return take(List.of(motes), plan).isPresent() ? plan : new Spend(Map.of(), 0);
    }

    /**
     * Spends motes from several stores together to release at least {@code required} void energy at
     * {@code pos}, by the same rule as {@link #spend}, planned from all their motes as if they were
     * one store and taken from them in order. It is all or nothing: when they cannot afford it
     * together, nothing is taken from any. A store given twice, such as one network reached through
     * two of its Voidstone, counts its motes twice, so the stores are distinct.
     *
     * @return the motes taken from each store, in their order, grade to count and empty for one that
     *         gave none, or empty when the spend is refused
     */
    public static Optional<List<Map<Integer, Integer>>> spendFromAll(List<? extends ResourceHandler<MoteResource>> stores, ServerLevel level,
            BlockPos pos, long required) {
        var motes = new TreeMap<Integer, Integer>();
        for (var store : stores) {
            countByGrade(store).forEach((grade, count) -> motes.merge(grade, count, (a, b) -> Ints.saturatedCast((long) a + b)));
        }
        return VoidEnergy.plan(motes, VoidPressure.at(level, pos), required).flatMap(plan -> take(stores, plan));
    }

    /**
     * Takes what {@code plan} names from {@code stores}, in order, all or nothing; the plan was made
     * from what they held.
     *
     * @return what each store gave, grade to count, or empty when they could not give it all
     */
    private static Optional<List<Map<Integer, Integer>>> take(List<? extends ResourceHandler<MoteResource>> stores, Spend plan) {
        var left = new HashMap<>(plan.motes());
        var taken = new ArrayList<Map<Integer, Integer>>();
        // Closed without a commit, the transaction puts back whatever was taken.
        try (var transaction = Transaction.openRoot()) {
            for (var motes : stores) {
                var given = new TreeMap<Integer, Integer>();
                // Taken slot by slot: a store may keep the motes of one grade in several slots.
                for (int slot = 0; slot < motes.size(); slot++) {
                    var resource = motes.getResource(slot);
                    int wanted = resource.isEmpty() ? 0 : left.getOrDefault(resource.grade(), 0);
                    if (wanted > 0) {
                        int got = motes.extract(slot, resource, wanted, transaction);
                        left.put(resource.grade(), wanted - got);
                        if (got > 0) {
                            given.merge(resource.grade(), got, Integer::sum);
                        }
                    }
                }
                taken.add(given);
            }
            if (left.values().stream().anyMatch(count -> count > 0)) {
                return Optional.empty();
            }
            transaction.commit();
        }
        return Optional.of(taken);
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
