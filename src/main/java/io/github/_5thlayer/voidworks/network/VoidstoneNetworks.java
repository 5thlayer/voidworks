// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** A dimension's Voidstone networks, saved with the dimension. */
public final class VoidstoneNetworks extends SavedData {

    private static final SavedDataType<VoidstoneNetworks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "voidstone_networks"),
            () -> new VoidstoneNetworks(new VoidNetworks(VoidworksConfig::voidstoneCapacity)),
            VoidNetworks.codec(VoidworksConfig::voidstoneCapacity).xmap(VoidstoneNetworks::new, saved -> saved.networks));

    private final VoidNetworks networks;

    private VoidstoneNetworks(VoidNetworks networks) {
        this.networks = networks;
        networks.onChange(this::setDirty);
    }

    /** @return the networks of {@code level}'s dimension */
    public static VoidNetworks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE).networks;
    }
}
