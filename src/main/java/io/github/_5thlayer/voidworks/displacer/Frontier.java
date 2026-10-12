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

/**
 * Where a Void Displacer harvests next, as plain logic over positions: it grows outward from
 * itself, reaching only positions that touch it or the Voidstone it has already left, so a cave or
 * an air pocket bounds it. Of those, it takes the nearest to itself first.
 */
public final class Frontier {

    private Frontier() {
    }

    /**
     * The space a Displacer that grows through one face reaches: everything past that face, so its
     * other faces, and all that is level with it or behind it, stay as they were.
     *
     * @param facing the way its front face looks
     * @return whether a position lies ahead of the Displacer at {@code displacer}
     */
    public static Predicate<BlockPos> ahead(BlockPos displacer, Direction facing) {
        var step = facing.getUnitVec3i();
        return pos -> (pos.getX() - displacer.getX()) * step.getX()
                + (pos.getY() - displacer.getY()) * step.getY()
                + (pos.getZ() - displacer.getZ()) * step.getZ() >= 1;
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
