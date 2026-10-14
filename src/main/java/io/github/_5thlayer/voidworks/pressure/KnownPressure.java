// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.util.OptionalInt;
import java.util.function.Supplier;

import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What a client knows of Void Pressure: the entry of its player's dimension, which the server sends
 * ({@link PressurePayload}) as the player joins, changes dimension or respawns, and after a reload.
 * It is for showing a player what motes are worth where they stand; every rule reads
 * {@link VoidPressure} on the server. On a server it knows nothing.
 */
public final class KnownPressure {

    private static volatile @Nullable PressurePayload known;
    private static volatile Supplier<@Nullable Player> viewer = () -> null;

    private KnownPressure() {
    }

    /** Keeps the entry the server sent. Client-only. */
    public static void receive(PressurePayload payload) {
        known = payload;
    }

    /** Forgets the entry, as the client leaves a server, so another server's is never shown. Client-only. */
    public static void forget() {
        known = null;
    }

    /** Sets who the client's player is, which {@link #here} reads. Client-only, at load. */
    public static void setViewer(Supplier<@Nullable Player> player) {
        viewer = player;
    }

    /** @return the client's player, whom a tooltip shows what motes are worth to, or null when there is none */
    public static @Nullable Player viewer() {
        return viewer.get();
    }

    /**
     * @return the Void Pressure at the client's player's feet, where a Void Siphon spends, or empty
     *         when there is no player or the server has sent no entry for the player's dimension
     */
    public static OptionalInt here() {
        var player = viewer.get();
        var entry = known;
        if (player == null || entry == null || !entry.dimension().equals(player.level().dimension())) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(entry.entry().pressure(VoidPressure.openToVoid(player.level(), player.blockPosition())));
    }
}
