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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Conduction seen: {@code count} motes of {@code grade} walking as small butterflies along the faces
 * of their network, from {@code source} to {@code target}, a step of conduction between two touching
 * stores. It is cosmetic: the motes have moved already, and nothing walks on the server.
 */
public record MoteCrawl(Vec3 source, Vec3 target, int grade, int count) implements CustomPacketPayload {

    /** How far from the source a player still sees motes conduct, in blocks: nearer than a harvest. */
    public static final double VIEW_DISTANCE = 32;

    public static final Type<MoteCrawl> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "mote_crawl"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoteCrawl> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, MoteCrawl::source,
            Vec3.STREAM_CODEC, MoteCrawl::target,
            ByteBufCodecs.VAR_INT, MoteCrawl::grade,
            ByteBufCodecs.VAR_INT, MoteCrawl::count,
            MoteCrawl::new);

    /**
     * Shows {@code count} motes of {@code grade} walking from the store at {@code from} to the touching
     * one at {@code to}: across their tops when they sit side by side, up or down their north faces
     * when one is on the other.
     *
     * @throws IllegalArgumentException when no mote has {@code grade}
     */
    public static void between(ServerLevel level, BlockPos from, BlockPos to, int grade, int count) {
        var crawl = new MoteCrawl(face(from, to), face(to, from), VoidEnergy.requireGrade(grade), count);
        if (!Butterflies.sizes(count).isEmpty()) {
            PacketDistributor.sendToPlayersNear(level, null, crawl.source().x, crawl.source().y, crawl.source().z,
                    VIEW_DISTANCE, crawl);
        }
    }

    /** @return the point on {@code pos}'s outside a butterfly walks through on its way to or from {@code other} */
    private static Vec3 face(BlockPos pos, BlockPos other) {
        var center = pos.getCenter();
        // Just off the face, so the butterfly is not drawn inside the block.
        return pos.getY() == other.getY() ? center.add(0, 0.52, 0) : center.add(0, 0, -0.52);
    }

    @Override
    public Type<MoteCrawl> type() {
        return TYPE;
    }
}
