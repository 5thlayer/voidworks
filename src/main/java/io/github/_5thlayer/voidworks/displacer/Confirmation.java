// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A Displacer's quote waiting on its confirmation: the player sneak-uses it once to be quoted, and
 * again within {@link #TICKS} ticks for the same thing to confirm it. Anything else, another player,
 * a quote for something else, or the time running out, lets it lapse. Held only while the game runs.
 */
final class Confirmation {

    /** How long a quote waits on its confirmation: 5 seconds. */
    static final long TICKS = 100;

    /** What a quote offers. */
    enum Offer {
        /** The next Zone upgrade. */
        BUY,
        /** Cancelling the Zone upgrade it is soaking. */
        CANCEL
    }

    private @Nullable UUID player;
    private @Nullable Offer offer;
    private long quotedAt;

    /** Quotes {@code offer} to {@code player} at game time {@code now}, in place of any quote before it. */
    void arm(UUID player, Offer offer, long now) {
        this.player = player;
        this.offer = offer;
        this.quotedAt = now;
    }

    /**
     * @return whether {@code player} confirms {@code offer} at game time {@code now}: the quote was
     *         theirs, for it, and is not yet lapsed. The quote is used up either way.
     */
    boolean confirms(UUID player, Offer offer, long now) {
        boolean confirms = player.equals(this.player) && offer == this.offer && now - quotedAt <= TICKS;
        lapse();
        return confirms;
    }

    /** Drops the quote. */
    void lapse() {
        player = null;
        offer = null;
    }
}
