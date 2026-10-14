// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * How a Void Dragon diving between Wells weakens by how far the Void Pressure at the worse of the
 * dive's two ends is below the End's, and how a hurt one flies more slowly, down to a quarter of its
 * speed.
 */
class HarmTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");
    private static final Identifier END = Identifier.withDefaultNamespace("the_end");

    /** The default pressures, the End's 3: the Overworld one step below it, the Nether two. */
    private static final Harm HARM = new Harm(dimension -> dimension.equals(NETHER) ? 2 : dimension.equals(OVERWORLD) ? 1 : 0, 200, 16);

    @Test
    void divingInTheNetherADragonWeakensTwiceAsFastAsInTheOverworldAndNotAtAllInTheEnd() {
        assertEquals(20, HARM.weakening(NETHER, NETHER, 0, 2_000));
        assertEquals(10, HARM.weakening(OVERWORLD, OVERWORLD, 0, 2_000));
        assertEquals(0, HARM.weakening(END, END, 0, 2_000));
    }

    @Test
    void aDiveBetweenDimensionsWeakensItAtTheWorseEndsRate() {
        assertEquals(20, HARM.weakening(NETHER, END, 0, 2_000));
        assertEquals(20, HARM.weakening(END, NETHER, 0, 2_000));
        assertEquals(20, HARM.weakening(OVERWORLD, NETHER, 0, 2_000));
        assertEquals(10, HARM.weakening(END, OVERWORLD, 0, 2_000));
    }

    @Test
    void itLosesHealthEachTimeTheClockPassesTheWeakeningTime() {
        // A stretch shorter than the time loses health only if the clock passes a whole multiple of it.
        assertEquals(0, HARM.weakening(NETHER, NETHER, 210, 310));
        assertEquals(2, HARM.weakening(NETHER, NETHER, 150, 250));
        assertEquals(2, HARM.weakening(NETHER, NETHER, 300, 400));
    }

    @Test
    void aHurtDragonFliesMoreSlowlyDownToAQuarterOfItsSpeed() {
        assertEquals(100, ofHealth(VoidDragonRecord.FULL_HEALTH).speed());
        assertEquals(50, ofHealth(50).speed());
        assertEquals(25, ofHealth(25).speed());
        assertEquals(25, ofHealth(1).speed());
    }

    private static VoidDragonRecord ofHealth(int health) {
        return VoidDragonRecord.grown(UUID.randomUUID(), new Place(OVERWORLD, BlockPos.ZERO)).weakened(VoidDragonRecord.FULL_HEALTH - health);
    }
}
