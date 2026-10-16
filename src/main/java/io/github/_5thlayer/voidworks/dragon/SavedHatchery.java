// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The world's {@link Hatchery}, one for the whole server, saved with the Overworld. */
public final class SavedHatchery extends SavedData {

    private static final SavedDataType<SavedHatchery> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "hatchery"),
            () -> new SavedHatchery(new Hatchery()),
            Hatchery.CODEC.xmap(SavedHatchery::new, saved -> saved.hatchery));

    private final Hatchery hatchery;

    private SavedHatchery(Hatchery hatchery) {
        this.hatchery = hatchery;
        hatchery.onChange(this::setDirty);
    }

    /** @return the server's hatchery */
    public static Hatchery of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE).hatchery;
    }
}
