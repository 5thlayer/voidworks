// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The butterflies following a Mote-line dragon's presence: one grade each of what it carries, its counts left out. */
class FollowersTest {

    @Test
    void aMoteLoadIsFollowedByItsGrades() {
        assertEquals(Set.of(3, 4), MoteLoad.of(Map.of(3, 1_000L, 4, 5L)).followers().grades());
    }

    @Test
    void nothingCarriedHasNoFollowers() {
        assertEquals(Followers.NONE, MoteLoad.NONE.followers());
    }

    @Test
    void aGradeOfNoMotesHasNoFollowers() {
        assertEquals(Set.of(3), MoteLoad.of(Map.of(3, 1L, 4, 0L)).followers().grades());
    }

    @Test
    void matterHasNoFollowers() {
        assertEquals(Followers.NONE, MatterLoad.NONE.followers());
    }

    @Test
    void itsGradesSurviveTheirSyncedBits() {
        var followers = MoteLoad.of(Map.of(2, 1L, 9, 1L, 16, 1L)).followers();
        assertEquals(Set.of(2, 9, 16), new Followers(followers.bits()).grades());
    }

    @Test
    void aNumberThatIsNoGradeHasNoFollowers() {
        assertEquals(Followers.NONE, Followers.of(List.of(1, 17)));
    }

    @Test
    void bitsThatAreNoGradeAreDropped() {
        assertEquals(Followers.NONE, new Followers(1 << 1 | 1 << 17 | 1));
    }

    @Test
    void countsDoNotChangeTheBits() {
        assertEquals(MoteLoad.of(Map.of(5, 1L)).followers(), MoteLoad.of(Map.of(5, 900L)).followers());
    }
}
