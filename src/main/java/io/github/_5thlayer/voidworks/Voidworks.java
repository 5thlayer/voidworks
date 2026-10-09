// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.butterfly.VoidworksButterflies;
import io.github._5thlayer.voidworks.command.VoidworksCommands;
import io.github._5thlayer.voidworks.gametest.VoidworksGameTests;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The Library's entry point. It registers the Void Pressure data map,
 * the {@code /voidworks} commands and the game tests, which exist only when game tests are enabled.
 */
@Mod(Voidworks.MOD_ID)
public final class Voidworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "voidworks";

    public Voidworks(IEventBus modBus) {
        VoidPressure.register(modBus);
        VoidworksButterflies.register(modBus);
        VoidworksCommands.register();
        VoidworksGameTests.register(modBus);
    }
}
