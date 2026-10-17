// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import org.junit.jupiter.api.Test;

/** The sparks a Mote-line dragon's presence trails: one colour per grade it carries, its counts left out. */
class SparksTest {

    @Test
    void aMoteLoadSparksInTheColoursOfItsGrades() {
        var sparks = MoteLoad.of(Map.of(3, 1_000L, 4, 5L)).sparks();
        assertEquals(List.of(GradeColor.of(3), GradeColor.of(4)), sparks.colours());
    }

    @Test
    void nothingCarriedSparksNothing() {
        assertEquals(List.of(), MoteLoad.NONE.sparks().colours());
    }

    @Test
    void matterSparksNothing() {
        assertEquals(Sparks.NONE, MatterLoad.NONE.sparks());
    }

    @Test
    void itsGradesSurviveTheirSyncedBits() {
        var sparks = MoteLoad.of(Map.of(2, 1L, 9, 1L, 16, 1L)).sparks();
        assertEquals(Set.of(2, 9, 16), new Sparks(sparks.bits()).grades());
    }

    @Test
    void aGradeNoMoteHasSparksNothing() {
        assertEquals(Sparks.NONE, Sparks.of(List.of(1, 17)));
    }

    @Test
    void countsDoNotChangeTheBits() {
        assertEquals(MoteLoad.of(Map.of(5, 1L)).sparks(), MoteLoad.of(Map.of(5, 900L)).sparks());
    }
}
