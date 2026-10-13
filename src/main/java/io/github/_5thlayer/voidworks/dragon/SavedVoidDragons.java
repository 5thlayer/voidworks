// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's {@link VoidDragons}, one for the whole server, saved with the Overworld, and the
 * presence each one shows while the server runs, which is never saved.
 */
public final class SavedVoidDragons extends SavedData {

    private static final SavedDataType<SavedVoidDragons> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_dragons"),
            () -> new SavedVoidDragons(new VoidDragons()),
            VoidDragons.CODEC.xmap(SavedVoidDragons::new, saved -> saved.dragons));

    private final VoidDragons dragons;
    /** Each dragon's presence, by its id, while it shows one. */
    final Map<UUID, VoidDragon> presences = new HashMap<>();

    private SavedVoidDragons(VoidDragons dragons) {
        this.dragons = dragons;
        dragons.onChange(this::setDirty);
    }

    static SavedVoidDragons saved(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** @return the server's Void Dragons */
    public static VoidDragons of(MinecraftServer server) {
        return saved(server).dragons;
    }
}
