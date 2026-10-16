// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.Optional;

import com.google.common.math.IntMath;
import com.google.common.math.LongMath;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What a Void Displacer's Zone upgrades are, datapack data on its block (ADR 0009): each level adds
 * {@code step} positions to its Zone, its first costs {@code firstPrice} void energy, and each level
 * it has bought multiplies that by {@code growth}, with no last level. A Displacer with no entry
 * buys none.
 */
public record ZoneUpgrade(int step, long firstPrice, int growth) {

    public static final Codec<ZoneUpgrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("step").forGetter(ZoneUpgrade::step),
            ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("first_price").forGetter(ZoneUpgrade::firstPrice),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("growth", 2).forGetter(ZoneUpgrade::growth)
    ).apply(instance, ZoneUpgrade::new));

    /** The map's id, {@code voidworks:zone_upgrade}, which names its file in a datapack. */
    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "zone_upgrade");

    /** Each Displacer's Zone upgrades, keyed by its block. */
    public static final DataMapType<Block, ZoneUpgrade> DATA_MAP = DataMapType.builder(DATA_MAP_ID, Registries.BLOCK, CODEC).build();

    /** @return the Zone upgrades of a Displacer of {@code state}'s block, or empty when it buys none */
    public static Optional<ZoneUpgrade> of(BlockState state) {
        return Optional.ofNullable(state.typeHolder().getData(DATA_MAP));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> event.register(DATA_MAP));
    }

    /** @return the price of the next level for a Displacer that has bought {@code levels}, and {@link Long#MAX_VALUE} once that would pass it */
    public long price(int levels) {
        return LongMath.saturatedMultiply(firstPrice, LongMath.saturatedPow(growth, levels));
    }

    /** @return {@code zone} raised by one level, and {@link Integer#MAX_VALUE} once that would pass it */
    public int raise(int zone) {
        return IntMath.saturatedAdd(zone, step);
    }
}
