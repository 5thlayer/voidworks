// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.butterfly.VoidworksButterflies;
import io.github._5thlayer.voidworks.command.VoidworksCommands;
import io.github._5thlayer.voidworks.displacer.DensityTooltip;
import io.github._5thlayer.voidworks.displacer.Displacement;
import io.github._5thlayer.voidworks.displacer.ZoneUpgrade;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.dragon.DiveColour;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.DragonRiding;
import io.github._5thlayer.voidworks.dragon.DragonRouting;
import io.github._5thlayer.voidworks.dragon.EggPrice;
import io.github._5thlayer.voidworks.dragon.Hatching;
import io.github._5thlayer.voidworks.mutation.Families;
import io.github._5thlayer.voidworks.mutation.UnmakingRecipes;
import io.github._5thlayer.voidworks.network.Conduction;
import io.github._5thlayer.voidworks.pressure.DimensionPressure;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import io.github._5thlayer.voidworks.taking.VoidTaking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Module's entry point. It registers the Void Pressure data map (and checks the defaults it
 * ships), Voidstone and the Void Displacers with their displacement table and server config, the
 * Density line in a Density item's tooltip, the Dormant machines' cost map, conduction through
 * Voidstone networks, the Families, the Unmaking Chamber's breakdown map, the Void Siphon, the
 * dragon eggs, the Dragonling they hatch and the Void Dragon it grows into, which carries motes
 * between Void Wells, and the {@code /voidworks} commands. The game tests hook themselves in from
 * the {@code gametest} source set, which never ships (libworks ADR 0011).
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
        VoidworksEntities.register(modBus);
        modBus.addListener(WorksTabContents::add);
        Displacement.register(modBus);
        VoidTaking.register(modBus);
        DensityTooltip.register();
        Dormancy.register(modBus);
        DiveColour.register(modBus);
        EggPrice.register(modBus);
        ZoneUpgrade.register(modBus);
        Families.register(modBus);
        UnmakingRecipes.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, VoidworksConfig.SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, VoidworksConfig::onLoad);
        modBus.addListener(ModConfigEvent.Reloading.class, VoidworksConfig::onLoad);
        NeoForge.EVENT_BUS.addListener(Conduction::onLevelTick);
        Hatching.register();
        DragonPresences.register();
        DragonRouting.register(modBus);
        DragonRiding.register();
        VoidworksCommands.register();
    }
}
