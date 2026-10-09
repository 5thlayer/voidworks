// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.butterfly;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A harvest seen: {@code count} motes of {@code grade} flying as butterflies from {@code source} to
 * {@code target}, or to the entity {@code targetEntity} when it is not {@link #NO_ENTITY}, following
 * it. It is cosmetic: the motes are credited at once, and nothing flies on the server.
 *
 * <p>A harvest calls {@link #toBlock} or {@link #toEntity} on the server, which sends it to the
 * players near the source.
 */
public record MoteFlight(Vec3 source, Vec3 target, int targetEntity, int grade, int count) implements CustomPacketPayload {

    /** The {@link #targetEntity} of a flight to a block. */
    public static final int NO_ENTITY = -1;

    /** How far from the source a player still sees a harvest, in blocks. */
    public static final double VIEW_DISTANCE = 64;

    public static final Type<MoteFlight> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "mote_flight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoteFlight> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, MoteFlight::source,
            Vec3.STREAM_CODEC, MoteFlight::target,
            ByteBufCodecs.VAR_INT, MoteFlight::targetEntity,
            ByteBufCodecs.VAR_INT, MoteFlight::grade,
            ByteBufCodecs.VAR_INT, MoteFlight::count,
            MoteFlight::new);

    /**
     * Shows {@code count} motes of {@code grade} flying from {@code source} into the block at {@code target}.
     *
     * @return how many butterflies the motes are seen as
     * @throws IllegalArgumentException when no mote has {@code grade} or {@code count} is negative
     */
    public static int toBlock(ServerLevel level, Vec3 source, BlockPos target, int grade, int count) {
        return send(level, new MoteFlight(source, target.getCenter(), NO_ENTITY, VoidEnergy.requireGrade(grade), count));
    }

    /**
     * Shows {@code count} motes of {@code grade} flying from {@code source} to {@code target}, such as
     * the player whose Void Siphon takes them, following it as it moves.
     *
     * @return how many butterflies the motes are seen as
     * @throws IllegalArgumentException when no mote has {@code grade} or {@code count} is negative
     */
    public static int toEntity(ServerLevel level, Vec3 source, Entity target, int grade, int count) {
        return send(level, new MoteFlight(source, aimAt(target), target.getId(), VoidEnergy.requireGrade(grade), count));
    }

    /** @return where butterflies flying to {@code entity} aim: the middle of its body */
    public static Vec3 aimAt(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2, 0);
    }

    private static int send(ServerLevel level, MoteFlight flight) {
        int butterflies = Butterflies.sizes(flight.count()).size();
        if (butterflies > 0) {
            PacketDistributor.sendToPlayersNear(level, null, flight.source().x, flight.source().y, flight.source().z,
                    VIEW_DISTANCE, flight);
        }
        return butterflies;
    }

    @Override
    public Type<MoteFlight> type() {
        return TYPE;
    }
}
