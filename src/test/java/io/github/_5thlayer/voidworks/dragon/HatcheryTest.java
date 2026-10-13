// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The price of an egg: fixed when it starts soaking, at the first egg's price doubled for every
 * dragon its owner has hatched or is soaking, so two eggs placed together never both get the cheap
 * price, and an egg given away starts its new owner cheap.
 */
class HatcheryTest {

    private static final long FIRST = 25_600;
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

    private final Hatchery hatchery = new Hatchery(() -> FIRST);

    @Test
    void anOwnersFirstEggCostsTheFirstPrice() {
        assertEquals(FIRST, hatchery.startSoaking(egg(0), ALICE));
    }

    @Test
    void twoEggsPlacedTogetherDoNotBothGetTheCheapPrice() {
        assertEquals(FIRST, hatchery.startSoaking(egg(0), ALICE));
        assertEquals(2 * FIRST, hatchery.startSoaking(egg(1), ALICE));
        assertEquals(4 * FIRST, hatchery.startSoaking(egg(2), ALICE));
    }

    @Test
    void aHatchedDragonStillDoublesItsOwnersNextEgg() {
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.hatch(egg(0));
        assertEquals(1, hatchery.dragons(ALICE));
        assertEquals(2 * FIRST, hatchery.startSoaking(egg(1), ALICE));
    }

    @Test
    void anEggLostWhileSoakingNoLongerCounts() {
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.lose(egg(0));
        assertEquals(0, hatchery.dragons(ALICE));
        assertEquals(FIRST, hatchery.startSoaking(egg(1), ALICE));
    }

    @Test
    void anEggThatIsNoLongerThereIsForgottenAndNoLongerCounts() {
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.startSoaking(egg(1), ALICE);
        hatchery.startSoaking(egg(2), BOB);
        hatchery.forgetGone(ALICE, egg -> egg.equals(egg(0)));
        assertEquals(1, hatchery.dragons(ALICE));
        hatchery.hatch(egg(0));
        assertEquals(1, hatchery.dragons(ALICE), "a forgotten egg hatched");
        hatchery.forgetGone(ALICE, egg -> true);
        assertEquals(0, hatchery.dragons(ALICE));
        assertEquals(1, hatchery.dragons(BOB), "another owner's egg was forgotten");
    }

    @Test
    void anotherOwnerStartsCheap() {
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.hatch(egg(0));
        hatchery.startSoaking(egg(1), ALICE);
        assertEquals(FIRST, hatchery.startSoaking(egg(2), BOB));
    }

    @Test
    void anEggLostOrHatchedTwiceCountsOnce() {
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.hatch(egg(0));
        hatchery.hatch(egg(0));
        hatchery.lose(egg(0));
        assertEquals(1, hatchery.dragons(ALICE));
    }

    @Test
    void anEggInAnotherDimensionAtTheSamePositionIsAnotherEgg() {
        var end = new Hatchery.Egg(Identifier.withDefaultNamespace("the_end"), egg(0).pos());
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.startSoaking(end, ALICE);
        hatchery.lose(egg(0));
        assertEquals(1, hatchery.dragons(ALICE));
    }

    @Test
    void untilAnyoneHatchesADragonNoneHasBeenHatched() {
        assertFalse(hatchery.anyHatched());
        hatchery.startSoaking(egg(0), ALICE);
        assertFalse(hatchery.anyHatched(), "a soaking egg is not a hatched dragon");
        hatchery.hatch(egg(0));
        assertTrue(hatchery.anyHatched());
    }

    @Test
    void thePriceDoublesPerDragonAndStopsAtTheLargestPrice() {
        assertEquals(FIRST, Hatchery.price(FIRST, 0));
        assertEquals(8 * FIRST, Hatchery.price(FIRST, 3));
        assertEquals(1L << 62, Hatchery.price(1, 62));
        assertEquals(Long.MAX_VALUE, Hatchery.price(1, 63));
        assertEquals(Long.MAX_VALUE, Hatchery.price(FIRST, 60));
        assertEquals(Long.MAX_VALUE, Hatchery.price(FIRST, Integer.MAX_VALUE));
    }

    @Test
    void aSaveAndReloadKeepsTheHatchedAndTheSoaking() {
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.hatch(egg(0));
        hatchery.startSoaking(egg(1), ALICE);
        hatchery.startSoaking(egg(2), BOB);
        var codec = Hatchery.codec(() -> FIRST);
        var read = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, hatchery).getOrThrow()).getOrThrow();
        assertEquals(2, read.dragons(ALICE));
        assertEquals(1, read.dragons(BOB));
        assertTrue(read.anyHatched());
        read.hatch(egg(2));
        assertEquals(1, read.dragons(BOB));
        assertEquals(2 * FIRST, read.startSoaking(egg(3), BOB));
    }

    @Test
    void aChangeIsReported() {
        int[] changes = {0};
        hatchery.onChange(() -> changes[0]++);
        hatchery.startSoaking(egg(0), ALICE);
        hatchery.hatch(egg(0));
        hatchery.startSoaking(egg(1), ALICE);
        hatchery.lose(egg(1));
        hatchery.lose(egg(1));
        assertEquals(4, changes[0]);
    }

    private static Hatchery.Egg egg(int x) {
        return new Hatchery.Egg(Identifier.withDefaultNamespace("overworld"), new BlockPos(x, 64, 0));
    }
}
