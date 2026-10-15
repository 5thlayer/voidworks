// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static io.github._5thlayer.voidworks.dragon.DragonReportTest.assertLine;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** What a Dragonling shows in Jade: how far it has grown, in whole percent, and its owner, or that it has none. */
class DragonlingReportTest {

    private static final int GROW_TICKS = 48_000;

    @Test
    void justHatchedItHasGrownNone() {
        assertEquals(0, DragonlingReport.of(GROW_TICKS, GROW_TICKS, true, Optional.of("Alex")).growth());
    }

    @Test
    void itHasGrownTheShareOfItsTimeGoneRoundedDown() {
        assertEquals(50, DragonlingReport.of(GROW_TICKS / 2, GROW_TICKS, true, Optional.empty()).growth());
        assertEquals(99, DragonlingReport.of(1, GROW_TICKS, true, Optional.empty()).growth());
        assertEquals(100, DragonlingReport.of(0, GROW_TICKS, true, Optional.empty()).growth());
    }

    @Test
    void moreTimeLeftThanTheConfigNowGivesIsNoneGrown() {
        assertEquals(0, DragonlingReport.of(GROW_TICKS * 2L, GROW_TICKS, true, Optional.empty()).growth());
    }

    @Test
    void itsLinesAreHowFarItHasGrownThenItsOwner() {
        var lines = DragonlingReport.of(GROW_TICKS / 4, GROW_TICKS, true, Optional.of("Alex")).lines();

        assertEquals(2, lines.size());
        assertLine(lines.get(0), "voidworks.jade.dragonling.grown", 75);
        assertLine(lines.get(1), "voidworks.jade.dragon.owner", "Alex");
    }

    @Test
    void oneWhoseOwnerTheServerHasNoNameForSaysSo() {
        assertLine(DragonlingReport.of(GROW_TICKS, GROW_TICKS, true, Optional.empty()).lines().getLast(), "voidworks.jade.dragon.unknown_owner");
    }

    @Test
    void oneNoPlayerOwnsSaysSo() {
        assertLine(DragonlingReport.of(GROW_TICKS, GROW_TICKS, false, Optional.empty()).lines().getLast(), "voidworks.jade.dragon.no_owner");
    }
}
