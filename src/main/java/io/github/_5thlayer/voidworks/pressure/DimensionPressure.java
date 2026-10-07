// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

/**
 * One dimension's entry in the Void Pressure data map, as plain logic that reads no game state: its
 * base pressure and its harvest kind. The pressure is on the same integer scale as a mote's grade
 * ({@link io.github._5thlayer.voidworks.energy.VoidEnergy}).
 *
 * @param base the Void Pressure of the dimension's ordinary positions
 * @param harvestKind how the Void Siphon harvests in the dimension
 */
public record DimensionPressure(int base, HarvestKind harvestKind) {

    /** The shipped data map, in the jar: the one place the default pressures are written. */
    private static final String SHIPPED_FILE = "/data/voidworks/data_maps/dimension/void_pressure.json";

    private static final String OVERWORLD = "minecraft:overworld";

    /**
     * The entries the Library ships in {@code data/voidworks/data_maps/dimension/void_pressure.json},
     * by dimension id: the provisional defaults of docs/spec/void-energy.md, and what a Consumer or a
     * datapack overrides. The file is the only copy of them; the code reads it, and only the
     * Overworld's is used, for a datapack that removes that entry. None of this is the Library's API.
     */
    static Map<String, DimensionPressure> shippedEntries() {
        return Shipped.ENTRIES;
    }

    /** Reads the shipped file once, the first time a default is needed. */
    private static final class Shipped {
        static final Map<String, DimensionPressure> ENTRIES = read();

        private static Map<String, DimensionPressure> read() {
            try (var reader = new InputStreamReader(
                    DimensionPressure.class.getResourceAsStream(SHIPPED_FILE), StandardCharsets.UTF_8)) {
                var values = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("values");
                var entries = new LinkedHashMap<String, DimensionPressure>();
                for (var entry : values.entrySet()) {
                    entries.put(entry.getKey(),
                            VoidPressure.ENTRY_CODEC.parse(JsonOps.INSTANCE, entry.getValue()).getOrThrow());
                }
                return Map.copyOf(entries);
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("cannot read the shipped " + SHIPPED_FILE, e);
            }
        }
    }

    /**
     * The entry that counts for a dimension: its own, or for a dimension the map does not name, the
     * Overworld's pressure with no harvest kind.
     *
     * @param named the dimension's own entry, if the map names it
     * @param overworld the Overworld's entry, if the map names it; the shipped default stands in
     *        when a datapack has removed it
     */
    public static DimensionPressure resolve(Optional<DimensionPressure> named, Optional<DimensionPressure> overworld) {
        return named.orElseGet(() ->
                new DimensionPressure(overworld.orElseGet(() -> shippedEntries().get(OVERWORLD)).base(), HarvestKind.NONE));
    }

    /**
     * The Void Pressure at a position in this dimension.
     *
     * @param openToVoid whether the position is open to the void below it ({@link OpenVoid}); it is
     *        asked only where it counts, so a dimension that is not the End never reads a column
     * @return the base, one step higher where the harvest kind is End and the position is open
     */
    public int pressure(BooleanSupplier openToVoid) {
        if (harvestKind == HarvestKind.END && openToVoid.getAsBoolean()) {
            return base + OpenVoid.EXTRA_PRESSURE;
        }
        return base;
    }
}
