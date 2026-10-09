// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.energy.MoteResource;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import org.jspecify.annotations.Nullable;

/** The Library's capabilities, public so a Consumer's blocks can provide them. */
public final class VoidworksCapabilities {

    /**
     * {@code voidworks:motes}: a block's mote store, which Voidstone and void machines provide, a
     * Consumer's own too, and {@link io.github._5thlayer.voidworks.energy.VoidSpender#spend} spends
     * from. There is no item capability: no block may charge or empty a Void Siphon (ADR 0002). A
     * multiblock forwards it itself, since Groundworks' footprint does not.
     */
    public static final BlockCapability<ResourceHandler<MoteResource>, @Nullable Direction> MOTES =
            BlockCapability.createSided(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "motes"), ResourceHandler.asClass());

    private VoidworksCapabilities() {
    }
}
