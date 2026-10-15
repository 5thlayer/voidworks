// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.registries.DataMapLoader;
import net.neoforged.neoforge.registries.datamaps.DataMapFile;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Void Pressure data map's entries by dimension id, read from its files by Voidworks itself
 * rather than from the {@code dimension} registry. A new world's datapacks are loaded before its
 * dimensions exist (the Overworld, the Nether and the End come from the world preset afterwards),
 * so NeoForge, resolving each entry against the registry of that moment, drops them all until the
 * next reload. Keyed by id, an entry counts whether or not its dimension was registered when the
 * files were read.
 *
 * <p>The files are the data map's own, read with NeoForge's codec, so conditions, {@code replace}
 * and {@code remove} mean what they mean in any data map.
 */
final class PressureEntries extends SimplePreparableReloadListener<List<DataMapFile<DimensionPressure, LevelStem>>> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier FILE = VoidPressure.DATA_MAP_ID.withPrefix(DataMapLoader.PATH + "/"
            + DataMapLoader.getFolderLocation(Registries.LEVEL_STEM.identifier()) + "/").withSuffix(".json");

    /**
     * The files the server last loaded, and the entries they give against the dimension registry
     * last asked with. A registry access holds one dimension registry for its life, and a reload
     * brings a new one, so the registry's identity is enough to tell when to resolve again. Every
     * caller is on the server thread; the lock only keeps a reload and a read from crossing.
     */
    private record Loaded(List<DataMapFile<DimensionPressure, LevelStem>> files,
                          HolderLookup.@Nullable RegistryLookup<LevelStem> stems,
                          Map<ResourceKey<LevelStem>, DimensionPressure> entries) {
    }

    /** The data map's own codec, so a file reads here as NeoForge reads it. */
    static final Codec<DataMapFile<DimensionPressure, LevelStem>> CODEC = DataMapFile.codec(Registries.LEVEL_STEM, VoidPressure.DATA_MAP);

    private static Loaded loaded = new Loaded(List.of(), null, Map.of());

    static void register() {
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                event.addListener(VoidPressure.DATA_MAP_ID, new PressureEntries()));
    }

    /**
     * @param stems the dimension registry, read only for the dimensions in a tag the files name
     * @return the entry the last loaded files give {@code stem}, or null where they name none
     */
    static synchronized @Nullable DimensionPressure get(HolderLookup.RegistryLookup<LevelStem> stems, ResourceKey<LevelStem> stem) {
        if (loaded.stems() != stems) {
            loaded = new Loaded(loaded.files(), stems, resolve(loaded.files(), tag -> stems.get(tag)
                    .map(set -> set.stream().flatMap(holder -> holder.unwrapKey().stream()).toList())
                    .orElse(List.of())));
        }
        return loaded.entries().get(stem);
    }

    private static synchronized void load(List<DataMapFile<DimensionPressure, LevelStem>> files) {
        loaded = new Loaded(files, null, Map.of());
    }

    /**
     * The entries the files give, by dimension, the files in the order the packs stack them: a later
     * pack's entry wins, a file that replaces drops the packs below it, and a removal takes an entry
     * away.
     *
     * @param tagged the dimensions in a tag
     */
    static Map<ResourceKey<LevelStem>, DimensionPressure> resolve(List<DataMapFile<DimensionPressure, LevelStem>> files,
                                                                 Function<TagKey<LevelStem>, List<ResourceKey<LevelStem>>> tagged) {
        var entries = new HashMap<ResourceKey<LevelStem>, DimensionPressure>();
        for (var file : files) {
            if (file.replace()) {
                entries.clear();
            }
            file.values().forEach((target, value) -> value.ifPresent(present ->
                    keys(target, tagged).forEach(key -> entries.put(key, present.carrier().value()))));
            file.removals().forEach(removal -> keys(removal.key(), tagged).forEach(entries::remove));
        }
        return Map.copyOf(entries);
    }

    private static List<ResourceKey<LevelStem>> keys(Either<TagKey<LevelStem>, ResourceKey<LevelStem>> target,
                                                     Function<TagKey<LevelStem>, List<ResourceKey<LevelStem>>> tagged) {
        return target.map(tagged, List::of);
    }

    @Override
    protected List<DataMapFile<DimensionPressure, LevelStem>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        var ops = makeConditionalOps();
        var files = new ArrayList<DataMapFile<DimensionPressure, LevelStem>>();
        for (var resource : manager.getResourceStack(FILE)) {
            try (Reader reader = resource.openAsReader()) {
                files.add(CODEC.parse(ops, JsonParser.parseReader(reader)).getOrThrow());
            } catch (Exception e) {
                // NeoForge's own load of the same file reports it too; the pack's other files still count.
                LOGGER.error("Could not read the Void Pressure data map {} in pack {}", FILE, resource.sourcePackId(), e);
            }
        }
        return files;
    }

    @Override
    protected void apply(List<DataMapFile<DimensionPressure, LevelStem>> files, ResourceManager manager, ProfilerFiller profiler) {
        load(files);
    }
}
