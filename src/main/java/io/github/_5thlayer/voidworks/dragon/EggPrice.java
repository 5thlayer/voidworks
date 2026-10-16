// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What a dragon egg costs to hatch, datapack data on the soaking egg's block (ADR 0009): an owner's
 * first egg costs {@code firstPrice} void energy, and each dragon they have hatched or are soaking
 * multiplies that by {@code growth}, so a pack can steepen or flatten the curve.
 */
public record EggPrice(long firstPrice, int growth) {

    public static final Codec<EggPrice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("first_price").forGetter(EggPrice::firstPrice),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("growth", 2).forGetter(EggPrice::growth)
    ).apply(instance, EggPrice::new));

    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "egg_price");

    /** The egg price, keyed by the soaking egg's block. */
    public static final DataMapType<Block, EggPrice> DATA_MAP = DataMapType.builder(DATA_MAP_ID, Registries.BLOCK, CODEC).build();

    /** An egg with no entry is free, as a Dormant machine with no cost is. */
    public static final EggPrice FREE = new EggPrice(0, 1);

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> event.register(DATA_MAP));
    }

    /** @return the price of an egg whose owner has {@code dragons}, hatched or soaking, and {@link Long#MAX_VALUE} once that would pass it */
    public long of(int dragons) {
        long price = firstPrice;
        for (int i = 0; i < dragons && growth > 1; i++) {
            if (price > Long.MAX_VALUE / growth) {
                return Long.MAX_VALUE;
            }
            price *= growth;
        }
        return price;
    }
}
