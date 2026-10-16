// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.taking;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The void seen taking a block: {@code pos}, Voidstone since game time {@code start}, held
 * {@code taken}, which each client draws over the Voidstone and eats away through
 * {@link TakingStages}. It is only a look, like the butterflies: the position is Voidstone for
 * everything from the start.
 *
 * <p>Whatever leaves Voidstone where it harvested calls {@link #show} on the server once it is there.
 */
public record VoidTaking(BlockPos pos, BlockState taken, long start) implements CustomPacketPayload {

    public static final Type<VoidTaking> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_taking"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoidTaking> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VoidTaking::pos,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), VoidTaking::taken,
            ByteBufCodecs.VAR_LONG, VoidTaking::start,
            VoidTaking::new);

    public static void register(IEventBus modBus) {
        // Optional, so a client without Voidworks can still join; it sees the Voidstone at once.
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC));
    }

    /**
     * Shows the void taking {@code pos}, Voidstone now, which held {@code taken}: it posts a
     * {@link VoidTakesBlockEvent}, then sends the taking to the players tracking the position.
     */
    public static void show(ServerLevel level, BlockPos pos, BlockState taken) {
        var event = NeoForge.EVENT_BUS.post(new VoidTakesBlockEvent(level, pos, taken));
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(event.pos()),
                new VoidTaking(event.pos(), taken, level.getGameTime()));
    }

    @Override
    public Type<VoidTaking> type() {
        return TYPE;
    }
}
