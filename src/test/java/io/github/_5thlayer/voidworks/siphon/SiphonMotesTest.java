// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** What a Void Siphon holds: motes pooled by grade, and what a hand harvest yields. */
class SiphonMotesTest {

    /** The Displacer's yields by Density at the default config: 1, 4, 16, 64. */
    private static int displacer(int density) {
        return List.of(1, 4, 16, 64).get(density - 1);
    }

    @Test
    void aHandHarvestYieldsOneDensityStepBelowADisplacerNeverBelowOne() {
        assertEquals(1, SiphonMotes.handYield(1, SiphonMotesTest::displacer));
        assertEquals(1, SiphonMotes.handYield(2, SiphonMotesTest::displacer));
        assertEquals(4, SiphonMotes.handYield(3, SiphonMotesTest::displacer));
        assertEquals(16, SiphonMotes.handYield(4, SiphonMotesTest::displacer));
    }

    @Test
    void aHandHarvestOfDensity0IsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SiphonMotes.handYield(0, SiphonMotesTest::displacer));
    }

    @Test
    void motesOfOneGradePoolAndOfDifferentGradesNever() {
        var motes = SiphonMotes.EMPTY.with(3, 5).with(4, 2).with(3, 7);
        assertEquals(Map.of(3, 7, 4, 2), motes.byGrade());
        assertEquals(9, motes.total());
        assertEquals(7, motes.count(3));
        assertEquals(0, motes.count(5));
    }

    @Test
    void aGradeSetToNoMotesIsGone() {
        var motes = SiphonMotes.EMPTY.with(3, 5).with(3, 0);
        assertEquals(SiphonMotes.EMPTY, motes);
        assertTrue(motes.byGrade().isEmpty());
    }

    @Test
    void aGradeNoMoteHasIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SiphonMotes.EMPTY.with(1, 5));
        assertThrows(IllegalArgumentException.class, () -> SiphonMotes.EMPTY.with(17, 5));
        assertThrows(IllegalArgumentException.class, () -> SiphonMotes.EMPTY.with(3, -1));
    }

    @Test
    void itSavesAndLoadsByGrade() {
        var motes = SiphonMotes.EMPTY.with(3, 5).with(5, 1);
        var saved = SiphonMotes.CODEC.encodeStart(JsonOps.INSTANCE, motes).getOrThrow();
        assertEquals(motes, SiphonMotes.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
    }

    @Test
    void itRefusesToLoadAGradeNoMoteHasOrAnEmptyCount() {
        assertTrue(SiphonMotes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[{\"grade\": 1, \"count\": 5}]")).isError());
        assertTrue(SiphonMotes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[{\"grade\": 3, \"count\": 0}]")).isError());
        assertTrue(SiphonMotes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[{\"grade\": 3, \"count\": 1}, {\"grade\": 3, \"count\": 2}]")).isError());
    }
}
