// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

/**
 * Where a Void Displacer harvests next, as plain logic over positions: it grows outward from
 * itself, reaching only positions that touch it or the Voidstone it has already left, so a cave or
 * an air pocket bounds it. Of those, it takes the nearest to itself first. A Displacer that grows
 * through one face reaches only the pyramid {@link #ahead} of it.
 */
public final class Frontier {

    private Frontier() {
    }

    /**
     * The space a Displacer that grows through one face reaches: a square pyramid opening out from
     * that face, one position a step ahead, a 3×3 two steps ahead and so on, never further aside than
     * it is ahead. Its other faces and all that is level with it or behind it stay as they were, as do
     * the positions beside the one a step ahead, so on the ground a player can still stand beside it.
     *
     * @param facing the way its front face looks
     * @return whether a position lies in the pyramid ahead of the Displacer at {@code displacer}
     */
    public static Predicate<BlockPos> ahead(BlockPos displacer, Direction facing) {
        var axis = facing.getAxis();
        return pos -> {
            var offset = pos.subtract(displacer);
            int reach = offset.get(axis) * facing.getAxisDirection().getStep();
            return reach >= 1 && aside(offset, axis) < reach;
        };
    }

    /** How far {@code offset} lies aside of {@code axis}: the larger of its two other coordinates, either way. */
    private static int aside(Vec3i offset, Direction.Axis axis) {
        int sideways = 0;
        for (var other : Direction.Axis.values()) {
            if (other != axis) {
                sideways = Math.max(sideways, Math.abs(offset.get(other)));
            }
        }
        return sideways;
    }

    /**
     * @param displacer the Displacer's position
     * @param grown the positions it has harvested, each Voidstone now
     * @param harvestable whether a position can be harvested, natural terrain for the Overworld's
     * @return the harvestable position face-touching the Displacer or its Voidstone nearest the
     *         Displacer, ties broken by position so the order is the same every time; empty when it
     *         can reach nothing
     */
    public static Optional<BlockPos> next(BlockPos displacer, Set<BlockPos> grown, Predicate<BlockPos> harvestable) {
        return Stream.concat(Stream.of(displacer), grown.stream())
                .flatMap(from -> Stream.of(Direction.values()).map(from::relative))
                .filter(pos -> !pos.equals(displacer) && !grown.contains(pos))
                .distinct()
                .filter(harvestable)
                .min(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(displacer)).thenComparing(Comparator.naturalOrder()));
    }
}
