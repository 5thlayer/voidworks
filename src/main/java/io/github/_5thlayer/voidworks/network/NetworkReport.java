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
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;

/**
 * What a Voidstone network shows a player looking at its Voidstone: the motes it holds by grade,
 * lowest first, their total against its capacity, and what they are worth in void energy where the
 * Voidstone stands, which a player compares with a cost. Read on the server, where networks and Void
 * Pressure live, and sent to the client, which words it with {@link #lines}.
 *
 * @param motes each grade it holds motes of, lowest first, with how many
 * @param worth the void energy they release together at the Voidstone looked at
 */
public record NetworkReport(Map<Integer, Long> motes, long capacity, long worth) {

    public static final StreamCodec<ByteBuf, NetworkReport> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(size -> new TreeMap<Integer, Long>(), ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_LONG), NetworkReport::motes,
            ByteBufCodecs.VAR_LONG, NetworkReport::capacity,
            ByteBufCodecs.VAR_LONG, NetworkReport::worth,
            NetworkReport::new);

    public NetworkReport {
        motes = Collections.unmodifiableMap(new TreeMap<>(motes));
    }

    /** @return what {@code network} holds, and its worth where the Void Pressure is {@code pressure} */
    public static NetworkReport of(MoteNetwork network, int pressure) {
        var motes = new TreeMap<Integer, Long>();
        for (int slot = 0; slot < network.size(); slot++) {
            if (network.getAmountAsLong(slot) > 0) {
                motes.put(MoteNetwork.grade(slot), network.getAmountAsLong(slot));
            }
        }
        return new NetworkReport(motes, network.capacity(), VoidEnergy.worth(motes, pressure));
    }

    /** @return what the network of the Voidstone at {@code pos} holds, or empty when there is none */
    public static Optional<NetworkReport> at(ServerLevel level, BlockPos pos) {
        return VoidstoneNetworks.of(level).at(pos).map(network -> of(network, VoidPressure.at(level, pos)));
    }

    /** @return how many motes it holds, of every grade together */
    public long total() {
        return motes.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * @return a line per grade it holds, tinted like that grade's butterflies, then its total against
     *         its capacity, then its worth, unless it holds nothing
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        motes.forEach((grade, count) -> lines.add(
                Component.translatable("voidworks.jade.network.grade", grade, count).withColor(GradeColor.of(grade))));
        lines.add(Component.translatable("voidworks.jade.network.held", total(), capacity));
        if (!motes.isEmpty()) {
            lines.add(Component.translatable("voidworks.jade.network.worth", worth));
        }
        return lines;
    }
}
