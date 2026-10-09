// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.voidworks.Voidworks;

/**
 * One dimension's entry in the Void Pressure data map: its base pressure and its harvest kind. The
 * pressure is on the same integer scale as a mote's grade
 * ({@link io.github._5thlayer.voidworks.energy.VoidEnergy}). It reads no game state; the one thing
 * it reads is the data map file the Library ships, from the jar, for the defaults.
 *
 * @param base the Void Pressure of the dimension's ordinary positions
 * @param harvestKind how the Void Siphon harvests in the dimension
 */
public record DimensionPressure(int base, HarvestKind harvestKind) {

    /** The shipped data map, in the jar: the one place the default pressures are written. */
    private static final String SHIPPED_FILE = "/data/" + Voidworks.MOD_ID + "/data_maps/dimension/"
            + VoidPressure.DATA_MAP_ID.getPath() + ".json";

    private static final String OVERWORLD = "minecraft:overworld";

    private static Map<String, DimensionPressure> shipped;

    /**
     * The entries the Library ships in {@code data/voidworks/data_maps/dimension/void_pressure.json},
     * by dimension id: the provisional defaults of docs/spec/void-energy.md, and what a Consumer or a
     * datapack overrides. The file is the only copy of them; the code reads it, and only the
     * Overworld's is used, for a datapack that removes that entry. The first call reads the file and
     * throws {@link IllegalStateException} if it is missing, unreadable or lacks the Overworld;
     * {@code Voidworks} makes that call at mod load, so a broken jar fails there.
     *
     * <p>Internal: the game tests and the Library's own load use it, and it is not the Library's API.
     */
    public static synchronized Map<String, DimensionPressure> shippedEntries() {
        if (shipped == null) {
            var stream = DimensionPressure.class.getResourceAsStream(SHIPPED_FILE);
            if (stream == null) {
                throw new IllegalStateException("the shipped Void Pressure data map " + SHIPPED_FILE
                        + " is missing from the Voidworks jar");
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                shipped = parseShipped(reader, SHIPPED_FILE);
            } catch (IOException e) {
                throw new IllegalStateException("cannot read the shipped Void Pressure data map " + SHIPPED_FILE, e);
            }
        }
        return shipped;
    }

    /**
     * Parses a data map file's text into entries by dimension id.
     *
     * @param source the file's name, for the message of a refusal
     * @throws IllegalStateException if the text is not a map of entries, or has no Overworld entry
     */
    static Map<String, DimensionPressure> parseShipped(Reader reader, String source) {
        Map<String, DimensionPressure> entries;
        try {
            var values = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("values");
            var read = new LinkedHashMap<String, DimensionPressure>();
            for (var entry : values.entrySet()) {
                read.put(entry.getKey(),
                        VoidPressure.ENTRY_CODEC.parse(JsonOps.INSTANCE, entry.getValue()).getOrThrow());
            }
            entries = Map.copyOf(read);
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot parse the shipped Void Pressure data map " + source, e);
        }
        if (!entries.containsKey(OVERWORLD)) {
            throw new IllegalStateException("the shipped Void Pressure data map " + source + " has no "
                    + OVERWORLD + " entry, which a dimension the map does not name falls back to");
        }
        return entries;
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
