// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import io.github._5thlayer.voidworks.Voidworks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The Void Pressure entry of the dimension a player is in, sent to that player's client: the map is
 * the server's, in a registry clients never receive, and the client needs it only to show what a
 * Void Siphon's motes are worth where its holder stands ({@link KnownPressure}).
 *
 * @param dimension the dimension the entry counts for
 */
public record PressurePayload(ResourceKey<Level> dimension, DimensionPressure entry) implements CustomPacketPayload {

    public static final Type<PressurePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "pressure"));

    private static final StreamCodec<ByteBuf, HarvestKind> KIND_CODEC =
            ByteBufCodecs.idMapper(ordinal -> HarvestKind.values()[ordinal], HarvestKind::ordinal);

    private static final StreamCodec<ByteBuf, DimensionPressure> ENTRY_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DimensionPressure::base,
            KIND_CODEC, DimensionPressure::harvestKind,
            DimensionPressure::new);

    public static final StreamCodec<ByteBuf, PressurePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), PressurePayload::dimension,
            ENTRY_CODEC, PressurePayload::entry,
            PressurePayload::new);

    @Override
    public Type<PressurePayload> type() {
        return TYPE;
    }
}
