// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Tells each player's client the Void Pressure entry of the dimension it is in
 * ({@link PressurePayload}), whenever that may change: as it joins, on a reload, as it changes
 * dimension and as it respawns. The client keeps it in {@link KnownPressure}.
 */
final class PressureSync {

    private PressureSync() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(PressureSync::registerPayload);
        // Posted for a joining player, and for every player on a reload.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.getRelevantPlayers().forEach(PressureSync::sendTo));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> sendTo(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> sendTo(event.getEntity()));
    }

    private static void registerPayload(RegisterPayloadHandlersEvent event) {
        // Optional, so a client without Voidworks can still join; it shows nothing it was not sent.
        event.registrar("1").optional().playToClient(PressurePayload.TYPE, PressurePayload.STREAM_CODEC);
    }

    private static void sendTo(Player player) {
        if (player instanceof ServerPlayer server) {
            var level = server.level();
            PacketDistributor.sendToPlayer(server, new PressurePayload(level.dimension(), VoidPressure.dimension(level)));
        }
    }
}
