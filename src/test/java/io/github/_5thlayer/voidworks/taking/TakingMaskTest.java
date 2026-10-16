// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.taking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github._5thlayer.voidworks.taking.TakingMask.Run;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * The 12 masks a face is taken through, read as what of the face the void has not yet reached: a
 * pixel a stage's mask paints is reached from that stage on, every pixel by the last, and each face
 * turns the masks by its position.
 */
class TakingMaskTest {

    private static final int SIZE = TakingMask.SIZE;

    @Test
    void withNoStageReachedTheWholeFaceIsLeftRowByRow() {
        var mask = TakingMask.of(masks(List.of()));
        var expected = new ArrayList<Run>();
        for (int y = 0; y < SIZE; y++) {
            expected.add(new Run(y, 0, SIZE));
        }
        assertEquals(expected, mask.left(0, 0));
    }

    @Test
    void aPixelAStagePaintsIsReachedFromThatStageOn() {
        var mask = TakingMask.of(masks(List.of(Set.of(pixel(3, 5)))));
        assertTrue(mask.left(0, 0).contains(new Run(5, 0, SIZE)));
        var left = mask.left(1, 0);
        assertTrue(left.contains(new Run(5, 0, 3)) && left.contains(new Run(5, 4, SIZE)), "row 5 left " + left);
        assertEquals(SIZE * SIZE - 1, area(left));
    }

    @Test
    void aReachedPixelStaysReachedThoughALaterMaskLeavesItOut() {
        var mask = TakingMask.of(masks(List.of(Set.of(pixel(3, 5)), Set.of(pixel(9, 9)))));
        var left = mask.left(2, 0);
        assertEquals(SIZE * SIZE - 2, area(left));
        assertFalse(covers(left, 3, 5));
        assertFalse(covers(left, 9, 9));
    }

    @Test
    void theLastStageReachesEveryPixelThoughItsMaskLeavesSomeOut() {
        var mask = TakingMask.of(masks(List.of(Set.of(pixel(3, 5)))));
        assertEquals(List.of(), mask.left(TakingStages.STAGES, 0));
    }

    @Test
    void theEightOrientationsTurnAPixelToEightPlaces() {
        var mask = TakingMask.of(masks(List.of(Set.of(pixel(3, 5)))));
        var places = new HashSet<Integer>();
        for (int orientation = 0; orientation < TakingMask.ORIENTATIONS; orientation++) {
            var left = mask.left(1, orientation);
            assertEquals(SIZE * SIZE - 1, area(left), "orientation " + orientation);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (!covers(left, x, y)) {
                        places.add(pixel(x, y));
                    }
                }
            }
        }
        assertEquals(TakingMask.ORIENTATIONS, places.size(), "the reached pixel landed on " + places);
    }

    @Test
    void theUnturnedOrientationLeavesTheMaskAsDrawn() {
        var mask = TakingMask.of(masks(List.of(Set.of(pixel(3, 5)))));
        assertFalse(covers(mask.left(1, 0), 3, 5));
    }

    @Test
    void aFaceIsTurnedTheSameWayEveryTime() {
        var pos = new BlockPos(12, -40, 7);
        assertEquals(TakingMask.orientation(pos, Direction.NORTH), TakingMask.orientation(pos, Direction.NORTH));
    }

    @Test
    void neighbouringFacesAreTurnedEveryWay() {
        var seen = new HashSet<Integer>();
        for (var pos : BlockPos.betweenClosed(0, 0, 0, 2, 2, 2)) {
            for (var face : Direction.values()) {
                int orientation = TakingMask.orientation(pos, face);
                assertTrue(orientation >= 0 && orientation < TakingMask.ORIENTATIONS, "orientation " + orientation);
                seen.add(orientation);
            }
        }
        assertEquals(TakingMask.ORIENTATIONS, seen.size(), "the faces of 27 blocks were turned only " + seen);
    }

    @Test
    void anOpaquePixelPaintsAndATransparentOneDoesNot() {
        assertTrue(TakingMask.paints(0xFF_2A_12_45));
        assertTrue(TakingMask.paints(0x80_FF_FF_FF));
        assertFalse(TakingMask.paints(0x7F_00_00_00));
        assertFalse(TakingMask.paints(0x00_FF_FF_FF));
    }

    @Test
    void anythingButTwelveMasksIsRefused() {
        var eleven = masks(List.of()).subList(0, TakingStages.STAGES - 1);
        assertThrows(IllegalArgumentException.class, () -> TakingMask.of(eleven));
    }

    @Test
    void aMaskOfTheWrongSizeIsRefused() {
        var masks = new ArrayList<>(masks(List.of()));
        masks.set(4, new boolean[SIZE * SIZE - 1]);
        assertThrows(IllegalArgumentException.class, () -> TakingMask.of(masks));
    }

    /** @return 12 masks, the first ones painting the pixels given and the rest nothing */
    private static List<boolean[]> masks(List<Set<Integer>> painted) {
        var masks = new ArrayList<boolean[]>();
        for (int stage = 0; stage < TakingStages.STAGES; stage++) {
            var mask = new boolean[SIZE * SIZE];
            if (stage < painted.size()) {
                painted.get(stage).forEach(index -> mask[index] = true);
            }
            masks.add(mask);
        }
        return masks;
    }

    private static int pixel(int x, int y) {
        return y * SIZE + x;
    }

    private static int area(List<Run> runs) {
        return runs.stream().mapToInt(run -> run.x1() - run.x0()).sum();
    }

    private static boolean covers(List<Run> runs, int x, int y) {
        return runs.stream().anyMatch(run -> run.y() == y && run.x0() <= x && x < run.x1());
    }
}
