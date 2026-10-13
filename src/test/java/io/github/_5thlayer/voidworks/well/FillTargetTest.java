// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/** A Void Well's fill target: none, a quarter, a half, three quarters or full of its capacity, cycled in that order. */
class FillTargetTest {

    @Test
    void cyclingFromNoneGoesThroughEveryShareAndBackToNone() {
        var seen = new ArrayList<FillTarget>();
        var target = FillTarget.NONE;
        do {
            seen.add(target);
            target = target.next();
        } while (target != FillTarget.NONE);
        assertEquals(List.of(FillTarget.NONE, FillTarget.QUARTER, FillTarget.HALF, FillTarget.THREE_QUARTERS, FillTarget.FULL), seen);
    }

    @Test
    void eachShareIsThatMuchOfTheCapacityInMotesAndNoneIsNoTarget() {
        assertEquals(OptionalLong.empty(), FillTarget.NONE.motes(65_536));
        assertEquals(OptionalLong.of(16_384), FillTarget.QUARTER.motes(65_536));
        assertEquals(OptionalLong.of(32_768), FillTarget.HALF.motes(65_536));
        assertEquals(OptionalLong.of(49_152), FillTarget.THREE_QUARTERS.motes(65_536));
        assertEquals(OptionalLong.of(65_536), FillTarget.FULL.motes(65_536));
    }

    @Test
    void aShareOfACapacityItDoesNotDivideRoundsDown() {
        assertEquals(OptionalLong.of(2), FillTarget.QUARTER.motes(10));
        assertEquals(OptionalLong.of(7), FillTarget.THREE_QUARTERS.motes(10));
    }
}
