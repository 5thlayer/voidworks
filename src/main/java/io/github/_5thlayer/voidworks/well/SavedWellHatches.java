// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The world's {@link WellHatches}, one for the whole server, saved with the Overworld. */
public final class SavedWellHatches extends SavedData {

    private static final SavedDataType<SavedWellHatches> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "well_hatches"),
            () -> new SavedWellHatches(new WellHatches()),
            WellHatches.CODEC.xmap(SavedWellHatches::new, saved -> saved.hatches));

    private final WellHatches hatches;

    private SavedWellHatches(WellHatches hatches) {
        this.hatches = hatches;
        hatches.onChange(this::setDirty);
    }

    /** @return the server's Well hatches */
    public static WellHatches of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE).hatches;
    }
}
