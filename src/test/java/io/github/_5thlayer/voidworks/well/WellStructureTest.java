// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import io.github._5thlayer.voidworks.well.WellStructure.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The Void Well's structure: a 3×3 floor, a 5×5 ring of walls three courses high around it, a rim
 * on top, and the core in the middle of one wall's middle course, facing out.
 */
class WellStructureTest {

    private static final BlockPos CORE = new BlockPos(10, 66, 10);

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void aWholeWellFacingAnyWayIsWhole(Direction facing) {
        var world = build(CORE, facing);
        assertTrue(WellStructure.whole(CORE, facing, pos -> world.getOrDefault(pos, Part.OPEN)));
    }

    @Test
    void itsFluidFillsTheShaftFromItsFloorToTheTopOfItsWalls() {
        var centre = CORE.south(2);
        assertEquals(new AABB(centre.getX() - 1, centre.getY() - 1, centre.getZ() - 1, centre.getX() + 2, centre.getY() + 2, centre.getZ() + 2),
                WellStructure.fluid(CORE, Direction.NORTH));
    }

    @Test
    void itsLayoutIsAFloorUnderAnOpenShaftInsideWallsUnderARim() {
        var layout = WellStructure.layout(CORE, Direction.NORTH);
        var centre = CORE.south(2);
        assertEquals(Part.CASING, layout.get(centre.below(2)));
        assertEquals(Part.OPEN, layout.get(centre));
        assertEquals(Part.OPEN, layout.get(centre.above(2)));
        assertEquals(Part.CASING, layout.get(CORE.east().below()));
        assertEquals(Part.RIM, layout.get(CORE.above(2)));
        assertEquals(null, layout.get(CORE.below(2)), "under the walls is not the well's");
        assertEquals(null, layout.get(CORE));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void itsShaftIsTwoBlocksBehindItsCoreLevelWithIt(Direction facing) {
        var shaft = WellStructure.shaft(CORE, facing);
        assertEquals(CORE.relative(facing.getOpposite(), 2), shaft);
        assertEquals(Part.OPEN, WellStructure.layout(CORE, facing).get(shaft));
    }

    @Test
    void itTakesFiftySixCasingAndSixteenRim() {
        var world = build(CORE, Direction.NORTH);
        assertEquals(56, world.values().stream().filter(part -> part == Part.CASING).count());
        assertEquals(16, world.values().stream().filter(part -> part == Part.RIM).count());
    }

    @Test
    void aWellMissingAnyOneBlockIsNotWhole() {
        var world = build(CORE, Direction.NORTH);
        for (var pos : Map.copyOf(world).keySet()) {
            if (pos.equals(CORE)) {
                continue;
            }
            var part = world.remove(pos);
            assertFalse(WellStructure.whole(CORE, Direction.NORTH, at -> world.getOrDefault(at, Part.OPEN)), "whole without " + pos);
            world.put(pos, part);
        }
    }

    @Test
    void aWellWithItsShaftFilledIsNotWhole() {
        var world = build(CORE, Direction.NORTH);
        world.put(CORE.south(2), Part.CASING);
        assertFalse(WellStructure.whole(CORE, Direction.NORTH, pos -> world.getOrDefault(pos, Part.OPEN)));
    }

    @Test
    void rimWhereCasingGoesIsNotWhole() {
        var world = build(CORE, Direction.NORTH);
        world.put(CORE.south(2).below(2), Part.RIM);
        assertFalse(WellStructure.whole(CORE, Direction.NORTH, pos -> world.getOrDefault(pos, Part.OPEN)));
    }

    @Test
    void itsRimIsStoodOnByFeetOneOrTwoAboveARimPositionAndNowhereElse() {
        var layout = WellStructure.layout(CORE, Direction.WEST);
        var centre = WellStructure.shaft(CORE, Direction.WEST);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 6; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    var pos = centre.offset(dx, dy, dz);
                    boolean overRim = layout.get(pos.below()) == Part.RIM || layout.get(pos.below(2)) == Part.RIM;
                    assertEquals(overRim, WellStructure.onRim(CORE, Direction.WEST, pos), pos.toShortString());
                }
            }
        }
    }

    @Test
    void aCoreFacingIntoItsShaftIsNotWhole() {
        var world = build(CORE, Direction.NORTH);
        assertFalse(WellStructure.whole(CORE, Direction.SOUTH, pos -> world.getOrDefault(pos, Part.OPEN)));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void aHatchGoesOnlyAgainstTheOuterFaceOfAWallCasing(Direction facing) {
        var layout = WellStructure.layout(CORE, facing);
        var centre = WellStructure.shaft(CORE, facing);
        int hatches = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    var against = centre.offset(dx, dy, dz);
                    for (var face : Direction.values()) {
                        var hatch = against.relative(face).subtract(centre);
                        boolean wallCasing = layout.get(against) == Part.CASING && Math.abs(dy) <= 1;
                        boolean outside = face.getAxis().isHorizontal() && Math.max(Math.abs(hatch.getX()), Math.abs(hatch.getZ())) == 3;
                        boolean placed = WellStructure.hatchFace(CORE, facing, against, face);
                        assertEquals(wallCasing && outside, placed, against.toShortString() + " " + face);
                        hatches += placed ? 1 : 0;
                    }
                }
            }
        }
        assertEquals(3 * 4 * 5 - 1, hatches, "every wall casing's outer faces, the core's left out");
    }

    @Test
    void aHatchGoesAgainstNeitherTheCoreNorTheRimNorTheFloor() {
        assertFalse(WellStructure.hatchFace(CORE, Direction.NORTH, CORE, Direction.NORTH));
        assertFalse(WellStructure.hatchFace(CORE, Direction.NORTH, CORE.above(2), Direction.UP));
        assertFalse(WellStructure.hatchFace(CORE, Direction.NORTH, CORE.above(2), Direction.NORTH));
        assertFalse(WellStructure.hatchFace(CORE, Direction.NORTH, CORE.south(2).below(2), Direction.DOWN));
        assertFalse(WellStructure.hatchFace(CORE, Direction.NORTH, CORE.east().below(), Direction.SOUTH), "into the shaft");
        assertTrue(WellStructure.hatchFace(CORE, Direction.NORTH, CORE.east().below(), Direction.NORTH));
    }

    /** Builds the well around {@code core} as its layout says, the core itself and the open shaft left out. */
    private static Map<BlockPos, Part> build(BlockPos core, Direction facing) {
        var world = new HashMap<BlockPos, Part>();
        WellStructure.layout(core, facing).forEach((pos, part) -> {
            if (part != Part.OPEN) {
                world.put(pos, part);
            }
        });
        return world;
    }
}
