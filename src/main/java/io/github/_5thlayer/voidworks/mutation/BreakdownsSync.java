// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Tells each player's client every breakdown on the server ({@link BreakdownsPayload}) as it joins
 * and on a reload. It is sent from the datapack sync, which the server posts before it sends the
 * recipes that make a recipe viewer reload, so the viewer finds it in place. The client keeps it in
 * {@link KnownBreakdowns}.
 */
final class BreakdownsSync {

    private BreakdownsSync() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(BreakdownsSync::registerPayload);
        // Posted for a joining player, and for every player on a reload.
        NeoForge.EVENT_BUS.addListener(BreakdownsSync::sendToClients);
    }

    private static void registerPayload(RegisterPayloadHandlersEvent event) {
        // Optional, so a client without Voidworks can still join; it shows nothing it was not sent.
        event.registrar("1").optional().playToClient(BreakdownsPayload.TYPE, BreakdownsPayload.STREAM_CODEC);
    }

    private static void sendToClients(OnDatapackSyncEvent event) {
        var payload = new BreakdownsPayload(UnmakingRecipes.all());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }
}
