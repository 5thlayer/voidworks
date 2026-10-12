// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.butterfly.VoidworksButterflies;
import io.github._5thlayer.voidworks.command.VoidworksCommands;
import io.github._5thlayer.voidworks.displacer.Displacement;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.gametest.VoidworksGameTests;
import io.github._5thlayer.voidworks.mutation.Families;
import io.github._5thlayer.voidworks.pressure.DimensionPressure;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * The Module's entry point. It registers the Void Pressure data map (and checks the defaults it
 * ships), Voidstone and the Void Displacers with their displacement table and server
 * config, the Dormant machines' cost map, the Families, the Void Siphon, the {@code /voidworks}
 * commands and the game tests, which exist only when game tests are enabled.
 */
@Mod(Voidworks.MOD_ID)
public final class Voidworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "voidworks";

    public Voidworks(IEventBus modBus, ModContainer container) {
        // Fail at load, with a clear message, if the jar's shipped Void Pressure defaults are broken.
        DimensionPressure.shippedEntries();
        VoidPressure.register(modBus);
        VoidworksButterflies.register(modBus);
        VoidworksBlocks.register(modBus);
        VoidworksItems.register(modBus);
        Displacement.register(modBus);
        Dormancy.register(modBus);
        Families.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, VoidworksConfig.SPEC);
        VoidworksCommands.register();
        VoidworksGameTests.register(modBus);
    }
}
