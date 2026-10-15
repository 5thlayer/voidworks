// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;
import java.util.UUID;

/**
 * What the owners of Void Dragons are doing, as their dragons' {@link Routing} sees it: standing in
 * one of their Wells' shafts, which calls a dragon there, and riding a dragon, which holds it at its
 * stop. {@code DragonRouting} reads them from the server's players; a test fakes them.
 */
public interface Owners {

    /** @return the place of {@code owner}'s Well whose shaft they stand in, calling a dragon of theirs there; empty while they stand in none */
    Optional<Place> calling(UUID owner);

    /** @return whether the owner of the dragon with id {@code dragonId} rides it, hovering at its stop */
    boolean riding(UUID dragonId);
}
