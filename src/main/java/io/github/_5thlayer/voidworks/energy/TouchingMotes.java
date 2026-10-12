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
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
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
     * @return what the stores touching {@code pos} are worth spent there together, in void energy,
     *         which is what a Dormant machine soaks from them and an upgrade can spend; a network
     *         touching it on several faces counts once; empty when none touches it
     */
    public static Optional<Long> totalWorth(ServerLevel level, BlockPos pos) {
        int pressure = VoidPressure.at(level, pos);
        var stores = distinct(at(level, pos));
        return stores.isEmpty()
                ? Optional.empty()
                : Optional.of(stores.stream().mapToLong(store -> worth(store.motes(), pressure)).reduce(0, LongMath::saturatedAdd));
    }

    /**
     * Spends at least {@code required} void energy from the stores touching {@code pos} together, by
     * the spend rule there ({@link VoidSpender#spendFromAll}), each store's motes flying in from it
     * as butterflies. All or nothing.
     *
     * @return whether it was paid
     */
    public static boolean spend(ServerLevel level, BlockPos pos, long required) {
        var stores = distinct(at(level, pos));
        var taken = VoidSpender.spendFromAll(stores.stream().map(Store::motes).toList(), level, pos, required);
        if (taken.isEmpty()) {
            return false;
        }
        for (int i = 0; i < stores.size(); i++) {
            var from = stores.get(i).pos().getCenter();
            taken.get().get(i).forEach((grade, count) -> MoteFlight.toBlock(level, from, pos, grade, count));
        }
        return true;
    }

    /** @return for a player: what the stores touching it are worth there, or that none touches it */
    public static Component describe(Optional<Long> worth) {
        return worth.<Component>map(energy -> Component.translatable("voidworks.motes.touching", energy))
                .orElseGet(() -> Component.translatable("voidworks.motes.none_touching"));
    }

    private static long worth(ResourceHandler<MoteResource> motes, int pressure) {
        return VoidEnergy.worth(VoidSpender.countByGrade(motes), pressure);
    }

    /**
     * @return each store once, on the first face it touches: a Voidstone's is its network's, which
     *         the Voidstone on every face it touches shares
     */
    private static List<Store> distinct(List<Store> stores) {
        var seen = Collections.<ResourceHandler<MoteResource>>newSetFromMap(new IdentityHashMap<>());
        var distinct = new ArrayList<Store>();
        for (var store : stores) {
            if (seen.add(store.motes() instanceof VoidstoneMotes voidstone ? voidstone.network() : store.motes())) {
                distinct.add(store);
            }
        }
        return distinct;
    }
}
