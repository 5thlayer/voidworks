// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksEntities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * What a player sees of the Void Dragons: each shows one presence, a {@link VoidDragon} that holds
 * no state of its own, where it rests while that place is loaded and ticking (ADR 0005). A presence
 * is never saved, so a chunk unloading takes it away and loses nothing, and it comes back once the
 * place is loaded again; one dragon never shows two.
 */
public final class DragonPresences {

    /** Ticks between looks for a dragon whose presence should show. */
    private static final int INTERVAL = 20;

    private DragonPresences() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % INTERVAL == 0) {
                show(event.getServer());
            }
        });
    }

    /** Shows each dragon whose resting place is loaded and ticking, and which shows none yet, its presence there. */
    public static void show(MinecraftServer server) {
        var saved = SavedVoidDragons.saved(server);
        for (var dragon : SavedVoidDragons.of(server).all()) {
            if (presence(server, dragon.id()).isPresent()) {
                continue;
            }
            var level = dragon.rest().level(server);
            var pos = dragon.rest().pos();
            if (level == null || !level.isPositionEntityTicking(pos)) {
                continue;
            }
            var presence = VoidworksEntities.VOID_DRAGON.get().create(level, EntitySpawnReason.TRIGGERED);
            if (presence != null) {
                presence.showPresenceOf(dragon);
                presence.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(presence);
                saved.presences.put(dragon.id(), presence);
            }
        }
    }

    /** @return the presence the dragon with {@code id} shows now, if it shows one */
    public static Optional<VoidDragon> presence(MinecraftServer server, UUID id) {
        var presence = SavedVoidDragons.saved(server).presences.get(id);
        return presence == null || presence.isRemoved() ? Optional.empty() : Optional.of(presence);
    }
}
