// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.registries.datamaps.DataMapFile;
import org.junit.jupiter.api.Test;

/**
 * The entries the Void Pressure data map's files give by dimension, as packs stack them: each file
 * is one pack's, lowest first.
 */
class PressureEntriesTest {

    private static final ResourceKey<LevelStem> MINING = stem("example:mining");
    private static final ResourceKey<LevelStem> END = stem("minecraft:the_end");

    @Test
    void aDimensionNoRegistryHoldsYetKeepsItsEntry() {
        assertEquals(Map.of(MINING, new DimensionPressure(4, HarvestKind.OVERWORLD)),
                resolve("{\"values\": {\"example:mining\": {\"base\": 4, \"harvest_kind\": \"overworld\"}}}"));
    }

    @Test
    void aLaterPacksEntryWins() {
        assertEquals(Map.of(END, new DimensionPressure(9, HarvestKind.END)),
                resolve("{\"values\": {\"minecraft:the_end\": {\"base\": 3, \"harvest_kind\": \"end\"}}}",
                        "{\"values\": {\"minecraft:the_end\": {\"base\": 9, \"harvest_kind\": \"end\"}}}"));
    }

    @Test
    void aReplacingFileDropsTheEntriesOfThePacksBelowIt() {
        assertEquals(Map.of(MINING, new DimensionPressure(4, HarvestKind.NONE)),
                resolve("{\"values\": {\"minecraft:the_end\": {\"base\": 3, \"harvest_kind\": \"end\"}}}",
                        "{\"replace\": true, \"values\": {\"example:mining\": {\"base\": 4, \"harvest_kind\": \"none\"}}}"));
    }

    @Test
    void aRemovalTakesAnEntryOfAPackBelowAway() {
        assertEquals(Map.of(),
                resolve("{\"values\": {\"minecraft:the_end\": {\"base\": 3, \"harvest_kind\": \"end\"}}}",
                        "{\"values\": {}, \"remove\": [\"minecraft:the_end\"]}"));
    }

    @Test
    void aTaggedEntryCountsForEachDimensionInTheTag() {
        var deep = stem("example:deep_mining");
        var mines = TagKey.create(Registries.LEVEL_STEM, Identifier.parse("example:mines"));
        var entry = new DimensionPressure(5, HarvestKind.NONE);
        assertEquals(Map.of(MINING, entry, deep, entry),
                PressureEntries.resolve(parse("{\"values\": {\"#example:mines\": {\"base\": 5, \"harvest_kind\": \"none\"}}}"),
                        tag -> tag.equals(mines) ? List.of(MINING, deep) : List.of()));
    }

    /** Resolves files that name no tag. */
    private static Map<ResourceKey<LevelStem>, DimensionPressure> resolve(String... files) {
        return PressureEntries.resolve(parse(files), tag -> List.of());
    }

    private static List<DataMapFile<DimensionPressure, LevelStem>> parse(String... files) {
        return Arrays.stream(files)
                .map(file -> PressureEntries.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(file)).getOrThrow())
                .toList();
    }

    private static ResourceKey<LevelStem> stem(String id) {
        return ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse(id));
    }
}
