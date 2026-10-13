// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.jade;

import io.github._5thlayer.voidworks.network.NetworkReport;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

/**
 * What the server tells Jade about a Voidstone or an awake Void Well: its network's {@link NetworkReport}.
 * Voidstone has no block entity, and a Well's motes are its network's, so this reads the network at
 * the block's position, where libworks-runtime's
 * {@code MachineReport} reads a block entity. This half names no client type, so a dedicated server
 * never loads Jade's drawing; {@link Client} adds the lines.
 */
final class VoidstoneReport implements StreamServerDataProvider<BlockAccessor, NetworkReport> {

    static final VoidstoneReport INSTANCE = new VoidstoneReport();

    // Jade keys a player's on/off setting for this tooltip by it.
    private static final Identifier UID = VoidworksJadePlugin.id("voidstone");

    private VoidstoneReport() {
    }

    @Override
    public @Nullable NetworkReport streamData(BlockAccessor accessor) {
        return accessor.getLevel() instanceof ServerLevel level ? NetworkReport.at(level, accessor.getPosition()).orElse(null) : null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, NetworkReport> streamCodec() {
        return NetworkReport.STREAM_CODEC.cast();
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** Adds the network's lines, when the server sent any. Client-only. */
    static final class Client implements IBlockComponentProvider {

        static final Client INSTANCE = new Client();

        private Client() {
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            VoidstoneReport.INSTANCE.decodeFromData(accessor).ifPresent(report -> report.lines().forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
