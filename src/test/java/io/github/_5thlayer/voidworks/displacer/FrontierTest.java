// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /** Facing east: what lies past its east face, however far aside once far enough ahead, and nothing level with it or behind. */
    @Test
    void aheadIsOnlyBeyondItsFrontFace() {
        var ahead = Frontier.ahead(DISPLACER, Direction.EAST);
        assertTrue(ahead.test(DISPLACER.east()));
        assertTrue(ahead.test(DISPLACER.east(8).above(5).north(7)));
        for (var level : new BlockPos[] {DISPLACER, DISPLACER.north(), DISPLACER.south(), DISPLACER.above(), DISPLACER.below(), DISPLACER.west()}) {
            assertFalse(ahead.test(level), level + " is not ahead of a Displacer facing east");
        }
    }

    /**
     * For every facing, a square pyramid opening out from its front face: one position a step ahead,
     * a 3×3 two steps ahead, and nothing further sideways than it is ahead.
     */
    @Test
    void aheadWidensAsAPyramid() {
        for (var facing : Direction.values()) {
            var ahead = Frontier.ahead(DISPLACER, facing);
            var oneAhead = DISPLACER.relative(facing);
            var twoAhead = DISPLACER.relative(facing, 2);
            var across = Direction.Plane.HORIZONTAL.test(facing) ? facing.getClockWise() : Direction.NORTH;
            var along = facing.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
            for (int dAcross = -1; dAcross <= 1; dAcross++) {
                for (int dAlong = -1; dAlong <= 1; dAlong++) {
                    var beside = oneAhead.relative(across, dAcross).relative(along, dAlong);
                    assertEquals(dAcross == 0 && dAlong == 0, ahead.test(beside), facing + ": a step ahead, " + dAcross + " and " + dAlong + " aside");
                    var pos = twoAhead.relative(across, dAcross).relative(along, dAlong);
                    assertTrue(ahead.test(pos), facing + ": two steps ahead, within one aside, " + pos);
                }
            }
            assertFalse(ahead.test(twoAhead.relative(across, 2)), facing + ": two steps ahead and two aside");
            assertFalse(ahead.test(twoAhead.relative(along, -2).relative(across, 1)), facing + ": two steps ahead and two aside the other way");
        }
    }

    /** On the ground, facing up, for a Zone of 64: the ring around it stays clear for a player to stand at its sides. */
    @Test
    void facingUpOnTheGroundLeavesTheRingAroundItClear() {
        var ahead = Frontier.ahead(DISPLACER, Direction.UP);
        var grown = new HashSet<BlockPos>();
        for (int i = 0; i < 64; i++) {
            var next = Frontier.next(DISPLACER, grown, ahead);
            assertTrue(next.isPresent());
            if (i == 0) {
                assertEquals(DISPLACER.above(), next.get());
            }
            var offset = next.get().subtract(DISPLACER);
            assertTrue(Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())) < offset.getY(), next.get() + " lies outside the pyramid");
            grown.add(next.get());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 0; dy <= 1; dy++) {
                    var ring = DISPLACER.offset(dx, dy, dz);
                    assertFalse(grown.contains(ring), "it filled " + ring + " beside it");
                }
            }
        }
    }

    @Test
    void facingDownAheadIsBelowIt() {
        var ahead = Frontier.ahead(DISPLACER, Direction.DOWN);
        assertTrue(ahead.test(DISPLACER.below()));
        assertFalse(ahead.test(DISPLACER.east()));
        assertFalse(ahead.test(DISPLACER.above()));
    }

    /** In open air, growing only ahead of its east face: its first harvest is that face's, and it never walls in another. */
    @Test
    void growingOnlyAheadLeavesItsOtherFacesOpen() {
        var ahead = Frontier.ahead(DISPLACER, Direction.EAST);
        var grown = new HashSet<BlockPos>();
        for (int i = 0; i < 60; i++) {
            var next = Frontier.next(DISPLACER, grown, ahead);
            assertTrue(next.isPresent());
            if (i == 0) {
                assertEquals(DISPLACER.east(), next.get());
            }
            grown.add(next.get());
        }
        for (var side : new BlockPos[] {DISPLACER.north(), DISPLACER.south(), DISPLACER.above(), DISPLACER.below(), DISPLACER.west()}) {
            assertFalse(grown.contains(side), "it walled in its face at " + side);
        }
    }
}
