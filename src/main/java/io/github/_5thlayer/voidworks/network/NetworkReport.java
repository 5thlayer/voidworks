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
 * What a network shows a player looking at its Voidstone or Void Well: the motes it holds by grade,
 * lowest first, their total against its capacity, what the Voidstone looked at holds of them, which is
 * what a spend beside it reaches now, and what the network's motes are worth in void energy there,
 * which a player compares with a cost. Read on the server, where networks and Void Pressure live,
 * and sent to the client, which words it with {@link #lines}.
 *
 * @param motes each grade it holds motes of, lowest first, with how many
 * @param here what the Voidstone looked at holds; none for a Void Well, whose motes are most of its
 *        network's total, so a line of their own only confuses
 * @param worth the void energy the network's motes release together at the block looked at
 */
public record NetworkReport(Map<Integer, Long> motes, long capacity, Optional<Held> here, long worth) {

    /** What one store holds, of every grade together, against what it holds at most. */
    public record Held(long count, long capacity) {

        static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Held::count,
                ByteBufCodecs.VAR_LONG, Held::capacity,
                Held::new);
    }

    public static final StreamCodec<ByteBuf, NetworkReport> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(size -> new TreeMap<Integer, Long>(), ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_LONG), NetworkReport::motes,
            ByteBufCodecs.VAR_LONG, NetworkReport::capacity,
            ByteBufCodecs.optional(Held.STREAM_CODEC), NetworkReport::here,
            ByteBufCodecs.VAR_LONG, NetworkReport::worth,
            NetworkReport::new);

    public NetworkReport {
        motes = Collections.unmodifiableMap(new TreeMap<>(motes));
    }

    /**
     * @return what the network of {@code store} holds, what {@code store} does, and the network's worth
     *         where the Void Pressure is {@code pressure}
     */
    public static NetworkReport of(MoteNetwork network, MoteStore store, int pressure) {
        var motes = network.countByGrade();
        var here = store.kind() == StoreKind.VOIDSTONE ? Optional.of(new Held(store.total(), store.capacity())) : Optional.<Held>empty();
        return new NetworkReport(motes, network.capacity(), here, VoidEnergy.worth(motes, pressure));
    }

    /** @return what the network of the Voidstone or Void Well at {@code pos} holds, or empty when there is none */
    public static Optional<NetworkReport> at(ServerLevel level, BlockPos pos) {
        var networks = VoidstoneNetworks.of(level);
        return networks.store(pos).map(store -> of(networks.at(pos).orElseThrow(), store, VoidPressure.at(level, pos)));
    }

    /** @return how many motes it holds, of every grade together */
    public long total() {
        return motes.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * @return a line per grade it holds, tinted like that grade's butterflies, then its total against
     *         its capacity, then what the Voidstone looked at holds, then its worth, unless it holds nothing
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        motes.forEach((grade, count) -> lines.add(
                Component.translatable("voidworks.jade.network.grade", grade, count).withColor(GradeColor.of(grade))));
        lines.add(Component.translatable("voidworks.jade.network.held", total(), capacity));
        here.ifPresent(held -> lines.add(Component.translatable("voidworks.jade.network.here", held.count(), held.capacity())));
        if (!motes.isEmpty()) {
            lines.add(Component.translatable("voidworks.jade.network.worth", worth));
        }
        return lines;
    }
}
