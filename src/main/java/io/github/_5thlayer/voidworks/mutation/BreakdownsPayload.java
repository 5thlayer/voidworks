// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Every breakdown on the server, sent to each client as it joins and to every player on a reload:
 * the recipes they come from and the data map that decides over them are the server's, and the
 * client needs them only to show the Unmaking Chamber's breakdowns in a recipe viewer
 * ({@link KnownBreakdowns}). {@link BreakdownsSync} sends it.
 *
 * @param breakdowns every item with a breakdown, and its breakdown
 */
public record BreakdownsPayload(Map<Item, Breakdown<Item>> breakdowns) implements CustomPacketPayload {

    public static final Type<BreakdownsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "breakdowns"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM_CODEC = ByteBufCodecs.registry(Registries.ITEM);

    private static final StreamCodec<RegistryFriendlyByteBuf, Breakdown<Item>> BREAKDOWN_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Breakdown::count,
            ByteBufCodecs.<RegistryFriendlyByteBuf, Item, Integer, Map<Item, Integer>>map(LinkedHashMap::new, ITEM_CODEC, ByteBufCodecs.VAR_INT),
            Breakdown::returns,
            Breakdown::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, BreakdownsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<RegistryFriendlyByteBuf, Item, Breakdown<Item>, Map<Item, Breakdown<Item>>>map(LinkedHashMap::new, ITEM_CODEC, BREAKDOWN_CODEC),
            BreakdownsPayload::breakdowns,
            BreakdownsPayload::new);

    @Override
    public Type<BreakdownsPayload> type() {
        return TYPE;
    }
}
