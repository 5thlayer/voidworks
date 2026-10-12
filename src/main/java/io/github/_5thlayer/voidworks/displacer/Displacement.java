// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The displacement table: what an Overworld Displacer's output gets for the terrain block it swaps
 * out, keyed by the terrain block and the fed block's Density, and by default the terrain block
 * itself. It is the {@code voidworks:displacement} data map on blocks, which another mod or a pack
 * fills to make displacing pay out its own items, in
 * {@code data/<namespace>/data_maps/block/displacement.json}, such as
 * {@code {"values": {"minecraft:iron_ore": {"4": {"id": "minecraft:raw_iron", "count": 3}}}}}. A
 * Density the entry does not name gives the terrain block. Voidworks ships no entries.
 */
public final class Displacement {

    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "displacement");

    private static final Codec<Integer> DENSITY_KEY = Codec.STRING.comapFlatMap(Displacement::parseDensity, String::valueOf);

    /** An entry: Density, as a string key, to what the output gets. */
    public static final Codec<Map<Integer, ItemStackTemplate>> ENTRY_CODEC = Codec.unboundedMap(DENSITY_KEY, ItemStackTemplate.CODEC);

    public static final DataMapType<Block, Map<Integer, ItemStackTemplate>> DATA_MAP =
            DataMapType.builder(DATA_MAP_ID, Registries.BLOCK, ENTRY_CODEC).build();

    private Displacement() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Displacement::registerDataMap);
    }

    private static void registerDataMap(RegisterDataMapTypesEvent event) {
        event.register(DATA_MAP);
    }

    /** @return what the output gets for displacing {@code terrain} with a fed block of {@code density}; empty for a block with no item */
    public static ItemStack result(BlockState terrain, int density) {
        var entry = terrain.typeHolder().getData(DATA_MAP);
        if (entry != null && entry.containsKey(density)) {
            return entry.get(density).create();
        }
        return new ItemStack(terrain.getBlock().asItem());
    }

    private static DataResult<Integer> parseDensity(String key) {
        try {
            int density = Integer.parseInt(key);
            if (density >= 1 && density <= VoidworksConfig.DENSITIES) {
                return DataResult.success(density);
            }
        } catch (NumberFormatException notANumber) {
            // Refused below.
        }
        return DataResult.error(() -> "a displacement is keyed by a Density from 1 to " + VoidworksConfig.DENSITIES + ", not " + key);
    }
}
