// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;

import com.google.common.math.LongMath;
import io.github._5thlayer.voidworks.VoidworksCapabilities;
import io.github._5thlayer.voidworks.network.VoidstoneMotes;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;

/**
 * The mote stores touching a void machine, which it spends from: any loaded block on one of its six
 * faces that provides {@code voidworks:motes}, a Voidstone network or a Consumer's machine. A network
 * touching it on several faces is found once per face.
 */
public final class TouchingMotes {

    /**
     * A mote store touching the machine.
     *
     * @param pos where the store stands, which its motes' butterflies fly from
     */
    public record Store(BlockPos pos, ResourceHandler<MoteResource> motes) {
    }

    private TouchingMotes() {
    }

    /** @return the mote stores touching {@code pos}, face by face */
    public static List<Store> at(ServerLevel level, BlockPos pos) {
        var stores = new ArrayList<Store>();
        for (var direction : Direction.values()) {
            var neighbour = pos.relative(direction);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            var motes = level.getCapability(VoidworksCapabilities.MOTES, neighbour, direction.getOpposite());
            if (motes != null) {
                stores.add(new Store(neighbour, motes));
            }
        }
        return stores;
    }

    /**
     * @return the most any one store touching {@code pos} is worth spent there, in void energy, which
     *         is what a spend from one store can release; empty when none touches it
     */
    public static Optional<Long> bestWorth(ServerLevel level, BlockPos pos) {
        int pressure = VoidPressure.at(level, pos);
        return distinct(at(level, pos)).stream().map(motes -> worth(motes, pressure)).max(Long::compare);
    }

    /**
     * @return what the stores touching {@code pos} are worth spent there together, in void energy,
     *         which is what a Dormant machine soaks from them; a network touching it on several faces
     *         counts once; empty when none touches it
     */
    public static Optional<Long> totalWorth(ServerLevel level, BlockPos pos) {
        int pressure = VoidPressure.at(level, pos);
        var stores = distinct(at(level, pos));
        return stores.isEmpty()
                ? Optional.empty()
                : Optional.of(stores.stream().mapToLong(motes -> worth(motes, pressure)).reduce(0, LongMath::saturatedAdd));
    }

    /** @return for a player: what the stores touching it are worth there, or that none touches it */
    public static Component describe(Optional<Long> worth) {
        return worth.<Component>map(energy -> Component.translatable("voidworks.motes.touching", energy))
                .orElseGet(() -> Component.translatable("voidworks.motes.none_touching"));
    }

    private static long worth(ResourceHandler<MoteResource> motes, int pressure) {
        return VoidEnergy.worth(VoidSpender.countByGrade(motes), pressure);
    }

    /** @return each store once: a Voidstone's is its network's, which the Voidstone on every face it touches shares */
    private static List<ResourceHandler<MoteResource>> distinct(List<Store> stores) {
        var seen = Collections.<ResourceHandler<MoteResource>>newSetFromMap(new IdentityHashMap<>());
        for (var store : stores) {
            seen.add(store.motes() instanceof VoidstoneMotes voidstone ? voidstone.network() : store.motes());
        }
        return List.copyOf(seen);
    }
}
