// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;

/**
 * What a Voidstone network shows a player looking at its Voidstone: the motes it holds by grade,
 * lowest first, and their total against its capacity. Read on the server, where networks live, and
 * sent to the client, which words it with {@link #lines}.
 *
 * @param motes each grade it holds motes of, lowest first, with how many
 */
public record NetworkReport(Map<Integer, Long> motes, long capacity) {

    public static final StreamCodec<ByteBuf, NetworkReport> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(size -> new TreeMap<Integer, Long>(), ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_LONG), NetworkReport::motes,
            ByteBufCodecs.VAR_LONG, NetworkReport::capacity,
            NetworkReport::new);

    public NetworkReport {
        motes = Collections.unmodifiableMap(new TreeMap<>(motes));
    }

    /** @return what {@code network} holds */
    public static NetworkReport of(MoteNetwork network) {
        var motes = new TreeMap<Integer, Long>();
        for (int slot = 0; slot < network.size(); slot++) {
            if (network.getAmountAsLong(slot) > 0) {
                motes.put(MoteNetwork.grade(slot), network.getAmountAsLong(slot));
            }
        }
        return new NetworkReport(motes, network.capacity());
    }

    /** @return what the network of the Voidstone at {@code pos} holds, or empty when there is none */
    public static Optional<NetworkReport> at(ServerLevel level, BlockPos pos) {
        return VoidstoneNetworks.of(level).at(pos).map(NetworkReport::of);
    }

    /** @return how many motes it holds, of every grade together */
    public long total() {
        return motes.values().stream().mapToLong(Long::longValue).sum();
    }

    /** @return a line per grade it holds, tinted like that grade's butterflies, then its total against its capacity */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        motes.forEach((grade, count) -> lines.add(
                Component.translatable("voidworks.jade.network.grade", grade, count).withColor(GradeColor.of(grade))));
        lines.add(Component.translatable("voidworks.jade.network.held", total(), capacity));
        return lines;
    }
}
