// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The Void Pressure at a position, the single place every rule that reads pressure asks. A
 * dimension's base and harvest kind come from the {@link #DATA_MAP}, keyed by dimension, which a
 * Consumer or a datapack fills or overrides; the open void adds a step where the End has nothing
 * beneath a position.
 *
 * <p>The map lives in the {@code dimension} registry, so an entry is the dimension's id, and the
 * file is {@code data/<namespace>/data_maps/dimension/void_pressure.json} in the datapack that
 * ships the entries, such as
 * {@code {"values": {"minecraft:the_end": {"base": 3, "harvest_kind": "end"}}}}. It is read on the
 * server only: nothing about pressure is shown on the client.
 */
public final class VoidPressure {

    /** The map's id, {@code voidworks:void_pressure}, which names its file in a datapack. */
    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_pressure");

    private static final Codec<HarvestKind> HARVEST_KIND_CODEC = StringRepresentable.fromEnum(HarvestKind::values);

    /** An entry as the map spells it: {@code {"base": 3, "harvest_kind": "end"}}. */
    public static final Codec<DimensionPressure> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("base").forGetter(DimensionPressure::base),
            HARVEST_KIND_CODEC.fieldOf("harvest_kind").forGetter(DimensionPressure::harvestKind)
    ).apply(instance, DimensionPressure::new));

    /** Each dimension's base pressure and harvest kind. Entries are keyed by dimension. */
    public static final DataMapType<LevelStem, DimensionPressure> DATA_MAP =
            DataMapType.builder(DATA_MAP_ID, Registries.LEVEL_STEM, ENTRY_CODEC).build();

    private VoidPressure() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(VoidPressure::registerDataMap);
    }

    private static void registerDataMap(RegisterDataMapTypesEvent event) {
        event.register(DATA_MAP);
    }

    /**
     * The Void Pressure at {@code pos}: its dimension's base, one step higher where the dimension's
     * harvest kind is End and nothing lies beneath the position down to the bottom of the world.
     */
    public static int at(ServerLevel level, BlockPos pos) {
        return dimension(level).pressure(() -> OpenVoid.isOpen(
                level.getMinY(), pos.getY(), y -> !level.getBlockState(pos.atY(y)).isAir()));
    }

    /** How the Void Siphon harvests in {@code level}'s dimension: none where the map does not name it. */
    public static HarvestKind harvestKind(ServerLevel level) {
        return dimension(level).harvestKind();
    }

    /** The entry that counts for {@code level}'s dimension, whether the map names it or not. */
    public static DimensionPressure dimension(ServerLevel level) {
        return dimension(level.registryAccess(), level.dimension());
    }

    /**
     * The entry that counts for a dimension of a server whose registries are {@code registries},
     * loaded or not. A dimension the map does not name takes the Overworld's pressure and no
     * harvest kind.
     */
    public static DimensionPressure dimension(RegistryAccess registries, ResourceKey<Level> dimension) {
        var entries = registries.lookupOrThrow(Registries.LEVEL_STEM).getDataMap(DATA_MAP);
        return DimensionPressure.resolve(
                Optional.ofNullable(entries.get(stem(dimension))),
                Optional.ofNullable(entries.get(LevelStem.OVERWORLD)));
    }

    private static ResourceKey<LevelStem> stem(ResourceKey<Level> dimension) {
        return ResourceKey.create(Registries.LEVEL_STEM, dimension.identifier());
    }
}
