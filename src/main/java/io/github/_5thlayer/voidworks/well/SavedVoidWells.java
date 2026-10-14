// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The world's {@link VoidWells}, one for the whole server, saved with the Overworld. */
public final class SavedVoidWells extends SavedData {

    private static final SavedDataType<SavedVoidWells> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_wells"),
            () -> new SavedVoidWells(new VoidWells()),
            VoidWells.CODEC.xmap(SavedVoidWells::new, saved -> saved.wells));

    private final VoidWells wells;

    private SavedVoidWells(VoidWells wells) {
        this.wells = wells;
        wells.onChange(this::setDirty);
    }

    /** @return the server's owned Void Wells */
    public static VoidWells of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE).wells;
    }
}
