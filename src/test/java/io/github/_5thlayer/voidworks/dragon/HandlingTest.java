// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * How a Matter-line dragon is seen loading or unloading at a stop: once it is up, the first item of each
 * hatch it takes from or puts into moves between that hatch and its paws, their starts spread so the
 * last, or a lone one, lands half a second before the stop ends; loading, it hangs its first stack once the first lands,
 * and unloading, the stack it hung until the last leaves its paws.
 */
class HandlingTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier DIRT = Identifier.withDefaultNamespace("dirt");
    private static final int STOP = 100;
    private static final Optional<Ware> COBBLE_STACK = Optional.of(Ware.of(COBBLESTONE, 64, 64));
    private static final Optional<Ware> DIRT_STACK = Optional.of(Ware.of(DIRT, 64, 64));

    @Test
    void aLonePassingLandsHalfASecondBeforeTheStopEnds() {
        var handling = Handling.loading(passings(1));
        assertEquals(70, handling.start(0, STOP));
        assertEquals(90, handling.landed(STOP));
    }

    @Test
    void passingsAreSpreadSoTheLastLandsHalfASecondBeforeTheStopEnds() {
        var handling = Handling.loading(passings(3));
        assertEquals(List.of(40L, 55L, 70L), List.of(handling.start(0, STOP), handling.start(1, STOP), handling.start(2, STOP)));
        assertEquals(90, handling.landed(STOP));
    }

    @Test
    void aStopTooShortToSpreadThemStartsThemAllOnceTheDragonIsUp() {
        var handling = Handling.loading(passings(3));
        assertEquals(List.of(40L, 40L, 40L), List.of(handling.start(0, 50), handling.start(1, 50), handling.start(2, 50)));
    }

    @Test
    void loadingItHangsNothingUntilTheFirstItemReachesItsPaws() {
        var handling = Handling.loading(passings(3));
        assertEquals(Optional.empty(), handling.hangs(59, COBBLE_STACK, STOP));
        assertEquals(COBBLE_STACK, handling.hangs(60, COBBLE_STACK, STOP));
        var alone = Handling.loading(passings(1));
        assertEquals(Optional.empty(), alone.hangs(89, COBBLE_STACK, STOP));
        assertEquals(COBBLE_STACK, alone.hangs(90, COBBLE_STACK, STOP));
    }

    @Test
    void loadingFromNoHatchOnTheWellItStoppedAtItHangsItsStackOnceItIsUp() {
        var handling = Handling.loading(List.of());
        assertEquals(Optional.empty(), handling.hangs(39, COBBLE_STACK, STOP));
        assertEquals(COBBLE_STACK, handling.hangs(40, COBBLE_STACK, STOP));
    }

    @Test
    void unloadingItHangsWhatItCarriedUntilTheLastItemLeavesItsPawsThenWhatItStillCarries() {
        var handling = Handling.unloading(passings(3), COBBLE_STACK);
        assertEquals(COBBLE_STACK, handling.hangs(69, DIRT_STACK, STOP));
        assertEquals(DIRT_STACK, handling.hangs(70, DIRT_STACK, STOP));
        assertEquals(Optional.empty(), handling.hangs(70, Optional.empty(), STOP));
    }

    @Test
    void itHoldsStillOnlyWhileItemsMove() {
        var handling = Handling.loading(passings(3));
        assertFalse(handling.holdsStill(39, STOP));
        assertTrue(handling.holdsStill(40, STOP));
        assertTrue(handling.holdsStill(89, STOP));
        assertFalse(handling.holdsStill(90, STOP));
        assertFalse(Handling.loading(List.of()).holdsStill(50, STOP));
    }

    /** @return {@code count} passings, each from its own hatch */
    private static List<Passing> passings(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Passing(new Place(OVERWORLD, new BlockPos(3, index, 0)), Ware.of(COBBLESTONE, 64, 64)))
                .toList();
    }
}
