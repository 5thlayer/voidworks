// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.taking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The void takes a block through 12 stages over 20 ticks: none reached as it starts, all 12 by its end. */
class TakingStagesTest {

    @Test
    void asItStartsTheVoidHasReachedNoStage() {
        assertEquals(0, TakingStages.reached(0));
    }

    @Test
    void halfwayItHasReachedHalfTheStages() {
        assertEquals(6, TakingStages.reached(10));
    }

    @Test
    void aStageIsReachedOnlyOnceItsShareOfTheTimeHasPassed() {
        // 20 ticks over 12 stages: the first is reached at 1⅔ ticks.
        assertEquals(0, TakingStages.reached(1.6F));
        assertEquals(1, TakingStages.reached(1.7F));
    }

    @Test
    void justBeforeItsEndElevenStagesAreReached() {
        assertEquals(11, TakingStages.reached(19.99F));
    }

    @Test
    void stagesAreNeverUndone() {
        int last = 0;
        for (float elapsed = 0; elapsed < 25; elapsed += 0.05F) {
            int reached = TakingStages.reached(elapsed);
            assertTrue(reached >= last, "at " + elapsed + " ticks it reached " + reached + ", after " + last);
            last = reached;
        }
    }

    @Test
    void afterItsEndEveryStageIsReached() {
        assertEquals(12, TakingStages.reached(20));
        assertEquals(12, TakingStages.reached(500));
    }

    /** The client's clock can trail the server's that stamped its start. */
    @Test
    void aTakingSeenBeforeItsStartHasReachedNoStage() {
        assertEquals(0, TakingStages.reached(-3));
    }

    @Test
    void itIsOverOnlyOnceTwentyTicksHavePassed() {
        assertFalse(TakingStages.over(19.99F));
        assertTrue(TakingStages.over(20));
    }
}
