// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Where a Displacer harvests next: the nearest harvestable position touching it or the Voidstone it
 * has left, so it grows outward from itself and a gap bounds it.
 */
class FrontierTest {

    private static final BlockPos DISPLACER = new BlockPos(10, 64, 10);

    @Test
    void theFirstHarvestIsAPositionBesideIt() {
        var below = DISPLACER.below();
        assertEquals(Optional.of(below), Frontier.next(DISPLACER, Set.of(), below::equals));
    }

    @Test
    void nothingHarvestableTouchingItLeavesNothingToHarvest() {
        assertEquals(Optional.empty(), Frontier.next(DISPLACER, Set.of(), pos -> false));
    }

    /** Under a Displacer on a floor one block thick: the floor around its Voidstone is nearer than below it. */
    @Test
    void ofThePositionsTouchingItsVoidstoneTheNearestToItComesFirst() {
        var voidstone = DISPLACER.below();
        var floor = Set.of(voidstone.north(), voidstone.below());
        assertEquals(Optional.of(voidstone.north()), Frontier.next(DISPLACER, Set.of(voidstone), floor::contains));
    }

    @Test
    void aPositionItHasHarvestedIsNotHarvestedAgain() {
        var voidstone = DISPLACER.below();
        assertEquals(Optional.empty(), Frontier.next(DISPLACER, Set.of(voidstone), voidstone::equals));
    }

    /** Terrain beyond an air gap touches neither the Displacer nor its Voidstone. */
    @Test
    void terrainBeyondAGapIsOutOfReach() {
        var beyondTheGap = DISPLACER.below(2);
        assertEquals(Optional.empty(), Frontier.next(DISPLACER, Set.of(), beyondTheGap::equals));
    }

    @Test
    void itGrowsOnFromItsFurthestVoidstone() {
        var grown = Set.of(DISPLACER.below(), DISPLACER.below(2));
        var deeper = DISPLACER.below(3);
        assertEquals(Optional.of(deeper), Frontier.next(DISPLACER, grown, deeper::equals));
    }
}
