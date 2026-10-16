// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The colour of a ridden dive's bar, by the destination's dimension type: a data map, so a pack
 * colours its own dimensions. One it does not name takes the void's purple.
 */
public final class DiveColour {

    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "dive_colour");

    /** An entry: an {@code "#rrggbb"} string, or a number. */
    public static final DataMapType<DimensionType, Integer> DATA_MAP =
            DataMapType.builder(DATA_MAP_ID, Registries.DIMENSION_TYPE, ExtraCodecs.STRING_RGB_COLOR).build();

    /** The dive overlay's deep purple, for a dimension the map does not name. */
    public static final int VOID = ARGB.color(255, 120, 40, 200);

    private DiveColour() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterDataMapTypesEvent event) -> event.register(DATA_MAP));
    }

    /** @return the bar's colour for a dive to {@code dimension} on {@code server}, opaque */
    public static int of(MinecraftServer server, Identifier dimension) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        var colour = level == null ? null : level.dimensionTypeRegistration().getData(DATA_MAP);
        return colour == null ? VOID : ARGB.opaque(colour);
    }
}
