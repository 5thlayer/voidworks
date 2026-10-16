// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.math.LongMath;
import io.github._5thlayer.voidworks.VoidworksCapabilities;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.network.VoidNetworks;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;

/**
 * The mote stores touching a void machine, which it spends from: any loaded block on one of its six
 * faces that provides {@code voidworks:motes}: a Voidstone's or a Void Well's own motes, which its
 * network conducts to it, or a Consumer's machine. A machine built of several blocks, as a Void Well
 * is, touches through the faces of each of its parts too.
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
        return at(level, pos, Set.of());
    }

    /**
     * @return the mote stores touching a machine at {@code pos} or any of its {@code parts}, the
     *         positions beyond its block that it is built of, face by face; its own blocks never count
     */
    public static List<Store> at(ServerLevel level, BlockPos pos, Set<BlockPos> parts) {
        var stores = new ArrayList<Store>();
        touching(level, pos, pos, parts, stores);
        parts.forEach(part -> touching(level, part, pos, parts, stores));
        return stores;
    }

    private static void touching(ServerLevel level, BlockPos from, BlockPos pos, Set<BlockPos> parts, List<Store> stores) {
        for (var direction : Direction.values()) {
            var neighbour = from.relative(direction);
            if (neighbour.equals(pos) || parts.contains(neighbour) || !level.isLoaded(neighbour)) {
                continue;
            }
            var motes = level.getCapability(VoidworksCapabilities.MOTES, neighbour, direction.getOpposite());
            if (motes != null) {
                stores.add(new Store(neighbour, motes));
            }
        }
    }

    /**
     * @return what the stores touching {@code pos} are worth spent there together, in void energy,
     *         which is what a Dormant machine soaks from them and an upgrade can spend; a store
     *         reached on several faces counts once; empty when none touches it
     */
    public static Optional<Long> totalWorth(ServerLevel level, BlockPos pos) {
        return totalWorth(level, pos, Set.of());
    }

    /** @return what the stores touching a machine at {@code pos} or any of its {@code parts} are worth there together, as {@link #totalWorth(ServerLevel, BlockPos)} */
    public static Optional<Long> totalWorth(ServerLevel level, BlockPos pos, Set<BlockPos> parts) {
        int pressure = VoidPressure.at(level, pos);
        var stores = distinct(at(level, pos, parts));
        return stores.isEmpty()
                ? Optional.empty()
                : Optional.of(stores.stream().mapToLong(store -> worth(store.motes(), pressure)).reduce(0, LongMath::saturatedAdd));
    }

    /**
     * @return what the stores touching {@code pos} are worth spent there with every store of their
     *         networks, in void energy: what they can pay over time, as their networks' motes conduct
     *         to them, where {@link #totalWorth} is what they hold now; a network or store reached on
     *         several faces counts once
     */
    public static long networkWorth(ServerLevel level, BlockPos pos) {
        int pressure = VoidPressure.at(level, pos);
        var networks = VoidstoneNetworks.of(level);
        var counted = Collections.newSetFromMap(new IdentityHashMap<>());
        long worth = 0;
        for (var store : distinct(at(level, pos))) {
            var network = networks.at(store.pos());
            if (network.isPresent()) {
                if (counted.add(network.get())) {
                    worth = LongMath.saturatedAdd(worth, VoidEnergy.worth(network.get().countByGrade(), pressure));
                }
            } else {
                worth = LongMath.saturatedAdd(worth, worth(store.motes(), pressure));
            }
        }
        return worth;
    }

    /**
     * Soaks what it can, up to {@code most} void energy, from the stores touching a machine at
     * {@code pos} or any of its {@code parts}, store by store, by the spend rule at {@code pos}, each
     * store's motes flying in from it as butterflies, and marks them wanted so their networks' motes
     * come to it: what a Dormant machine, or a Displacer's Zone upgrade, soaks each time.
     *
     * @return the void energy soaked, which may pass {@code most} by part of a mote
     */
    public static long soak(ServerLevel level, BlockPos pos, Set<BlockPos> parts, long most) {
        var stores = at(level, pos, parts);
        want(level, pos, stores);
        long soaked = 0;
        for (var store : stores) {
            if (soaked >= most) {
                break;
            }
            soaked += payInto(level, store.motes(), store.pos().getCenter(), pos, most - soaked);
        }
        return soaked;
    }

    /**
     * Spends what it can of {@code motes}, up to {@code most} void energy, by the spend rule at
     * {@code pos} ({@link VoidSpender#spendUpTo}), the motes flying from {@code from} to it as
     * butterflies: one store's share of a soak, or a Void Siphon emptied into a machine.
     *
     * @return the void energy released, which may pass {@code most} by part of a mote
     */
    public static long payInto(ServerLevel level, ResourceHandler<MoteResource> motes, Vec3 from, BlockPos pos, long most) {
        var spend = VoidSpender.spendUpTo(motes, level, pos, most);
        spend.motes().forEach((grade, count) -> MoteFlight.toBlock(level, from, pos, grade, count));
        return spend.released();
    }

    /**
     * Spends at least {@code required} void energy from the stores touching {@code pos} together, by
     * the spend rule there ({@link VoidSpender#spendTogether}), each store's motes flying in from it
     * as butterflies. All or nothing; a cost of nothing is paid with no store touching it.
     *
     * @return whether it was paid
     */
    public static boolean spend(ServerLevel level, BlockPos pos, long required) {
        var stores = distinct(at(level, pos));
        want(level, pos, stores);
        var taken = VoidSpender.spendTogether(stores.stream().map(Store::motes).toList(), level, pos, required);
        if (taken.isEmpty()) {
            return false;
        }
        for (int i = 0; i < stores.size(); i++) {
            var from = stores.get(i).pos().getCenter();
            taken.get().get(i).forEach((grade, count) -> MoteFlight.toBlock(level, from, pos, grade, count));
        }
        return true;
    }

    /**
     * Marks each Voidstone or Void Well among {@code stores} as wanted ({@link VoidNetworks#want(BlockPos, int)})
     * by a machine at {@code pos}, so its network's motes find their way to the machine drawing on
     * them, however few, and a Void Well gives it those worth something at the machine's Void
     * Pressure. Only a real draw calls it, a soak or a spend; a look at what the stores are worth does not.
     */
    public static void want(ServerLevel level, BlockPos pos, List<Store> stores) {
        var networks = VoidstoneNetworks.of(level);
        int pressure = VoidPressure.at(level, pos);
        stores.forEach(store -> networks.want(store.pos(), pressure));
    }

    /**
     * Marks each Voidstone or Void Well among {@code stores} as wanted, as {@link #want(ServerLevel, BlockPos, List)}
     * does, by a machine that may spend motes of every grade.
     */
    public static void want(ServerLevel level, List<Store> stores) {
        var networks = VoidstoneNetworks.of(level);
        stores.forEach(store -> networks.want(store.pos()));
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
     * @return each store once, on the first face it touches, should one store, such as a Consumer's
     *         multiblock, be reached on several
     */
    private static List<Store> distinct(List<Store> stores) {
        var seen = Collections.<ResourceHandler<MoteResource>>newSetFromMap(new IdentityHashMap<>());
        var distinct = new ArrayList<Store>();
        for (var store : stores) {
            if (seen.add(store.motes())) {
                distinct.add(store);
            }
        }
        return distinct;
    }
}
