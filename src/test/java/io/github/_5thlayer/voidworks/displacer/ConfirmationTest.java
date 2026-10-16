// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A quote is confirmed by the same player, for the same thing, within {@link Confirmation#TICKS}
 * ticks of it; anything else lets it lapse.
 */
class ConfirmationTest {

    private static final UUID ALEX = new UUID(0, 1);
    private static final UUID STEVE = new UUID(0, 2);

    @Test
    void theSamePlayerConfirmsWithinTheWindow() {
        var confirmation = new Confirmation();
        confirmation.arm(ALEX, Confirmation.Offer.BUY, 1_000);
        assertTrue(confirmation.confirms(ALEX, Confirmation.Offer.BUY, 1_000 + Confirmation.TICKS));
    }

    @Test
    void nothingIsArmedAtFirst() {
        assertFalse(new Confirmation().confirms(ALEX, Confirmation.Offer.BUY, 0));
    }

    @Test
    void aQuoteLapsesAfterTheWindow() {
        var confirmation = new Confirmation();
        confirmation.arm(ALEX, Confirmation.Offer.BUY, 1_000);
        assertFalse(confirmation.confirms(ALEX, Confirmation.Offer.BUY, 1_001 + Confirmation.TICKS));
    }

    @Test
    void anotherPlayerConfirmsNothingAndTheQuoteLapses() {
        var confirmation = new Confirmation();
        confirmation.arm(ALEX, Confirmation.Offer.BUY, 1_000);
        assertFalse(confirmation.confirms(STEVE, Confirmation.Offer.BUY, 1_001));
        assertFalse(confirmation.confirms(ALEX, Confirmation.Offer.BUY, 1_002));
    }

    @Test
    void aQuoteToBuyIsNoConfirmationToCancel() {
        var confirmation = new Confirmation();
        confirmation.arm(ALEX, Confirmation.Offer.BUY, 1_000);
        assertFalse(confirmation.confirms(ALEX, Confirmation.Offer.CANCEL, 1_001));
    }

    @Test
    void aConfirmationIsUsedOnce() {
        var confirmation = new Confirmation();
        confirmation.arm(ALEX, Confirmation.Offer.BUY, 1_000);
        assertTrue(confirmation.confirms(ALEX, Confirmation.Offer.BUY, 1_001));
        assertFalse(confirmation.confirms(ALEX, Confirmation.Offer.BUY, 1_002));
    }

    @Test
    void lapsingDropsTheQuote() {
        var confirmation = new Confirmation();
        confirmation.arm(ALEX, Confirmation.Offer.BUY, 1_000);
        confirmation.lapse();
        assertFalse(confirmation.confirms(ALEX, Confirmation.Offer.BUY, 1_001));
    }
}
