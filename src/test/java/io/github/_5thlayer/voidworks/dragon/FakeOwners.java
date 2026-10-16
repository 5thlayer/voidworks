// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Owners as plain data: where each stands calling a dragon, and which dragons their owners ride. */
final class FakeOwners implements Owners {

    final Map<UUID, Place> calling = new HashMap<>();
    final Set<UUID> riding = new HashSet<>();

    @Override
    public Optional<Place> calling(UUID owner) {
        return Optional.ofNullable(calling.get(owner));
    }

    @Override
    public boolean riding(UUID dragonId) {
        return riding.contains(dragonId);
    }
}
