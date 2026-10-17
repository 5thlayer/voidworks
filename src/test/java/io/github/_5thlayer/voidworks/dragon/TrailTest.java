// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import org.junit.jupiter.api.Test;

/** The butterflies a Mote-line dragon's presence trails: one colour per grade it carries, its counts left out. */
class TrailTest {

    @Test
    void aMoteLoadTrailsTheColoursOfItsGrades() {
        var trail = MoteLoad.of(Map.of(3, 1_000L, 4, 5L)).trail();
        assertEquals(List.of(GradeColor.of(3), GradeColor.of(4)), trail.colours());
    }

    @Test
    void nothingCarriedTrailsNothing() {
        assertEquals(List.of(), MoteLoad.NONE.trail().colours());
    }

    @Test
    void matterTrailsNothing() {
        assertEquals(Trail.NONE, MatterLoad.NONE.trail());
    }

    @Test
    void itsGradesSurviveTheirSyncedBits() {
        var trail = MoteLoad.of(Map.of(2, 1L, 9, 1L, 16, 1L)).trail();
        assertEquals(Set.of(2, 9, 16), new Trail(trail.bits()).grades());
    }

    @Test
    void aGradeNoMoteHasTrailsNothing() {
        assertEquals(Trail.NONE, Trail.of(List.of(1, 17)));
    }

    @Test
    void countsDoNotChangeTheBits() {
        assertEquals(MoteLoad.of(Map.of(5, 1L)).trail(), MoteLoad.of(Map.of(5, 900L)).trail());
    }
}
