// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.Map;

import net.minecraft.world.item.Item;

/**
 * What a client knows of breakdowns: every one the server has, which it sends
 * ({@link BreakdownsPayload}) as the player joins and after a reload. It is for a recipe viewer; the
 * Unmaking Chamber reads {@link UnmakingRecipes} on the server. On a server it knows nothing.
 */
public final class KnownBreakdowns {

    private static volatile Map<Item, Breakdown<Item>> known = Map.of();

    private KnownBreakdowns() {
    }

    /** Keeps the breakdowns the server sent. Client-only. */
    public static void receive(BreakdownsPayload payload) {
        known = payload.breakdowns();
    }

    /** Forgets them, as the client leaves a server, so another server's are never shown. Client-only. */
    public static void forget() {
        known = Map.of();
    }

    /** @return every item with a breakdown on the server the client is on, and its breakdown */
    public static Map<Item, Breakdown<Item>> all() {
        return known;
    }
}
