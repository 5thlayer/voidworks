// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * A Void Dragon's dive between two Wells: a time per block of distance in Overworld blocks, the
 * Nether's counting eight and the End's one, a fixed time more between dimensions, and never less
 * than 2 seconds. With the provisional numbers, a second per 64 blocks and 10 seconds more.
 */
class DiveTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");
    private static final Identifier END = Identifier.withDefaultNamespace("the_end");

    private final Dive dive = new Dive(200, 64, dimension -> dimension.equals(NETHER) ? 8.0 : 1.0);

    @Test
    void aDiveWithinADimensionTakesASecondPer64BlocksApart() {
        var from = new Place(OVERWORLD, new BlockPos(0, 64, 0));
        var to = new Place(OVERWORLD, new BlockPos(384, 64, 512));
        // 640 blocks apart: 10 seconds, and no fixed time.
        assertEquals(200, dive.ticks(from, to));
    }

    @Test
    void aDiveBetweenDimensionsTakesItsFixedTimeMore() {
        var from = new Place(OVERWORLD, new BlockPos(0, 64, 0));
        var to = new Place(END, new BlockPos(384, 64, 512));
        // 640 blocks apart: 10 seconds and 10 more.
        assertEquals(400, dive.ticks(from, to));
    }

    @Test
    void aNetherBlockCountsEightAndAnEndBlockOne() {
        var overworld = new Place(OVERWORLD, new BlockPos(0, 64, 0));
        // 80 Nether blocks out are 640 Overworld blocks.
        assertEquals(400, dive.ticks(overworld, new Place(NETHER, new BlockPos(80, 64, 0))));
        assertEquals(400, dive.ticks(new Place(NETHER, new BlockPos(0, 64, 80)), new Place(END, new BlockPos(0, 64, 0))));
    }

    @Test
    void heightCountsNothing() {
        assertEquals(200, dive.ticks(new Place(OVERWORLD, new BlockPos(0, 0, 0)), new Place(END, new BlockPos(0, 200, 0))));
    }

    @Test
    void partOfASecondRoundsUpToATick() {
        // 650 blocks are 203 and 1/8 ticks, rounded up to 204.
        assertEquals(204, dive.ticks(new Place(OVERWORLD, BlockPos.ZERO), new Place(OVERWORLD, new BlockPos(650, 0, 0))));
    }

    @Test
    void aDiveBetweenNearWellsTakesTwoSeconds() {
        // 1 block is 20/64 of a tick, and a dive shown going into one shaft and out of the other takes longer.
        assertEquals(40, dive.ticks(new Place(OVERWORLD, BlockPos.ZERO), new Place(OVERWORLD, new BlockPos(1, 0, 0))));
    }

    @Test
    void aDiveBetweenDimensionsWithNoFixedTimeTakesTwoSecondsToo() {
        var instant = new Dive(0, 64, dimension -> 1.0);
        assertEquals(40, instant.ticks(new Place(OVERWORLD, BlockPos.ZERO), new Place(END, BlockPos.ZERO)));
    }

    @Test
    void aWellToItselfIsNoDive() {
        var place = new Place(NETHER, new BlockPos(5, 64, 5));
        assertEquals(0, dive.ticks(place, place));
    }

    @Test
    void aRiddenDiveTakesNoMoreThanItsRiddenTimeNorLessThanTheLeast() {
        var ridden = new Dive(200, 64, 60, dimension -> 1.0);
        assertEquals(60, ridden.ridden(400));
        assertEquals(50, ridden.ridden(50), "a dive shorter than the ridden time was made longer");
        assertEquals(Dive.MINIMUM_TICKS, new Dive(200, 64, 0, dimension -> 1.0).ridden(400));
    }
}
