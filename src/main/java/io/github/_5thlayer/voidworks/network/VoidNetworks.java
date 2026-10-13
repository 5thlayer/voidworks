// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.energy.MoteResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Every network of one dimension, as plain logic over positions. Voidstone, and a Void Well, that
 * touch face to face are one network, and one that comes to touch two networks merges them: pressure,
 * not topology, decides where motes go, so a merge is harmless.
 *
 * <p>Each position holds its own motes ({@link MoteStore}), which a spend there reaches. Motes
 * conduct ({@link #conduct}): each grade on its own, from a store of higher network pressure to a
 * touching one of lower, a step at a time and without loss.
 *
 * <p>{@link VoidstoneNetworks} keeps one per dimension, saved with the world.
 */
public final class VoidNetworks {

    /**
     * How many steps of conduction a store stays wanted after a spender last drew on it: twice the
     * steps between two draws of a Dormant machine or Chamber at the default intervals (20 ticks, a
     * step every 10), so a store drawn on regularly never stops being wanted between draws.
     */
    static final int WANTED_STEPS = 4;

    private final IntSupplier perVoidstone;
    private final IntSupplier perWell;
    private final Map<BlockPos, MoteStore> stores = new HashMap<>();
    private final Map<MoteStore, MoteNetwork> networkOf = new IdentityHashMap<>();
    /** The stores motes are wanted at, each with the last step of conduction it is wanted for; not saved. */
    private final Map<MoteStore, Long> wanted = new IdentityHashMap<>();
    /** Steps of conduction so far, counting from when the networks were loaded. */
    private long step;
    private Runnable changed = () -> {
    };

    /**
     * @param perVoidstone how many motes each Voidstone holds
     * @param perWell how many motes each Void Well holds
     */
    public VoidNetworks(IntSupplier perVoidstone, IntSupplier perWell) {
        this.perVoidstone = perVoidstone;
        this.perWell = perWell;
    }

    /** Runs {@code changed} whenever a network gains or loses a store or motes, to mark them for saving. */
    void onChange(Runnable changed) {
        this.changed = changed;
    }

    void changed() {
        changed.run();
    }

    long capacity(StoreKind kind) {
        return switch (kind) {
            case VOIDSTONE -> perVoidstone.getAsInt();
            case WELL -> perWell.getAsInt();
        };
    }

    /** @return a store in no network, holding nothing and with no room */
    MoteStore empty() {
        return new MoteStore(BlockPos.ZERO, StoreKind.VOIDSTONE, this);
    }

    /** @return the motes held at {@code pos}, or empty when nothing there is in a network */
    public Optional<MoteStore> store(BlockPos pos) {
        return Optional.ofNullable(stores.get(pos));
    }

    /** @return the network of {@code pos}, or empty when nothing there is in one */
    public Optional<MoteNetwork> at(BlockPos pos) {
        return store(pos).map(networkOf::get);
    }

    /** Puts a Voidstone at {@code pos} into a network, as {@link #join(BlockPos, StoreKind)}. */
    public void join(BlockPos pos) {
        join(pos, StoreKind.VOIDSTONE);
    }

    /**
     * Puts a store of {@code kind} at {@code pos}, holding nothing, into a network: the one it
     * touches, all of them merged when it touches several, or a new one of its own. A position
     * already in a network stays as it is.
     */
    public void join(BlockPos pos, StoreKind kind) {
        pos = pos.immutable();
        if (stores.containsKey(pos)) {
            return;
        }
        var store = new MoteStore(pos, kind, this);
        var touching = touching(pos);
        MoteNetwork network;
        if (touching.isEmpty()) {
            network = new MoteNetwork();
        } else {
            // The largest takes in the rest, so the fewest stores move.
            network = touching.stream().max(Comparator.comparingInt(MoteNetwork::size)).orElseThrow();
            for (var other : touching) {
                if (other != network) {
                    network.stores.addAll(other.stores);
                    other.stores.forEach(moved -> networkOf.put(moved, network));
                }
            }
        }
        network.stores.add(store);
        stores.put(pos, store);
        networkOf.put(store, network);
        changed.run();
    }

    /**
     * Takes the store at {@code pos} out of its network, and the motes it held are lost. A network it
     * splits becomes one for each part. Voidstone cannot be broken, so for Voidstone only a command
     * or a creative player does this; a Void Well is broken like any block.
     */
    public void leave(BlockPos pos) {
        var store = stores.remove(pos);
        if (store == null) {
            return;
        }
        var network = networkOf.remove(store);
        network.stores.remove(store);
        split(network);
        changed.run();
    }

    /** Parts {@code network} into a network for each set of its stores that still touch. */
    private void split(MoteNetwork network) {
        var unvisited = new LinkedHashSet<>(network.stores);
        boolean first = true;
        while (!unvisited.isEmpty()) {
            var part = component(unvisited.iterator().next());
            unvisited.removeAll(part);
            if (first) {
                // The first part keeps the network, so a whole one stays the same.
                network.stores.retainAll(part);
                first = false;
            } else {
                var other = new MoteNetwork();
                other.stores.addAll(part);
                part.forEach(moved -> networkOf.put(moved, other));
            }
        }
    }

    /** @return the stores reached from {@code start} through touching stores, nearest first */
    private List<MoteStore> component(MoteStore start) {
        var reached = new ArrayList<MoteStore>();
        var seen = new HashSet<MoteStore>();
        var queue = new ArrayDeque<MoteStore>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            var store = queue.poll();
            reached.add(store);
            for (var direction : Direction.values()) {
                var next = stores.get(store.pos().relative(direction));
                if (next != null && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return reached;
    }

    /**
     * @return how many more motes the network of a Voidstone placed at {@code pos} would take: its
     *         own capacity and what the networks it would join have spare, or that of the network
     *         already there
     */
    public long roomIfJoined(BlockPos pos) {
        var already = at(pos);
        if (already.isPresent()) {
            return already.get().room();
        }
        return perVoidstone.getAsInt() + touching(pos).stream().mapToLong(MoteNetwork::room).sum();
    }

    /**
     * Puts motes into the network at {@code pos} as they arrive there, such as a harvest: into the
     * store at {@code pos}, and what it has no room for into the nearest stores of its network that
     * have. From there they conduct as any other.
     *
     * @return how many went in, fewer than {@code amount} only when the network is full
     */
    public int deposit(BlockPos pos, MoteResource mote, int amount, TransactionContext transaction) {
        var start = stores.get(pos);
        if (start == null) {
            return 0;
        }
        int slot = MoteStore.slot(mote.grade());
        int left = amount;
        for (var store : component(start)) {
            if (left == 0) {
                break;
            }
            left -= store.insert(slot, mote, left, transaction);
        }
        return amount - left;
    }

    /**
     * One step of conduction: in each pair of touching stores, each grade moves from the one of higher
     * network pressure to the one of lower, at most {@code rate} motes a face, and never so many that
     * it turns the difference around. A mote moves one store a step: one that arrived this step waits
     * for the next.
     *
     * @return what moved, store to store
     */
    public List<Flow> conduct(int rate) {
        step++;
        var toWanted = distancesToWanted();
        var flows = new ArrayList<Flow>();
        var arrived = new IdentityHashMap<MoteStore, long[]>();
        for (var from : List.copyOf(stores.values())) {
            for (int slot = 0; slot < MoteStore.SLOTS; slot++) {
                for (var direction : Direction.values()) {
                    var to = stores.get(from.pos().relative(direction));
                    long waiting = arrived.containsKey(from) ? arrived.get(from)[slot] : 0;
                    long ready = from.motes[slot] - waiting;
                    if (to == null || ready <= 0) {
                        continue;
                    }
                    int moved = flow(from, to, slot, (int) Math.min(rate, ready), toWanted);
                    if (moved > 0) {
                        from.motes[slot] -= moved;
                        to.motes[slot] += moved;
                        arrived.computeIfAbsent(to, store -> new long[MoteStore.SLOTS])[slot] += moved;
                        flows.add(new Flow(from.pos(), to.pos(), MoteStore.grade(slot), moved));
                    }
                }
            }
        }
        if (!flows.isEmpty()) {
            changed.run();
        }
        return flows;
    }

    /**
     * @return the most motes of the grade in {@code slot}, up to {@code most}, that can move from
     *         {@code from} to {@code to} with {@code from}'s pressure still at least {@code to}'s; or
     *         one mote, when {@code to} is nearer where motes are wanted and the mote leaves
     *         {@code to} no higher than {@code from} was
     */
    private int flow(MoteStore from, MoteStore to, int slot, int most, Map<MoteStore, Integer> toWanted) {
        int even = evening(from, to, slot, most);
        if (even == 0 && most > 0 && to.room() > 0 && nearer(to, from, toWanted)
                && from.pressure(slot, from.motes[slot]) >= to.pressure(slot, to.motes[slot] + 1)) {
            // Evening out alone leaves steps of one mote between neighbours, and level ground where
            // motes are few, so the last of them would never reach a machine at the end of a line of
            // Voidstone. Where one is wanted, a mote steps down toward it; nearer each step, none
            // goes round in circles, and with nothing wanted, motes on level ground stay put.
            return 1;
        }
        return even;
    }

    /** @return whether {@code to} is strictly nearer a store motes are wanted at than {@code from} is */
    private static boolean nearer(MoteStore to, MoteStore from, Map<MoteStore, Integer> toWanted) {
        var distance = toWanted.get(to);
        return distance != null && distance < toWanted.getOrDefault(from, Integer.MAX_VALUE);
    }

    /**
     * Marks the store at {@code pos} as one motes are wanted at: a spender beside it is drawing on
     * it, so for the next {@link #WANTED_STEPS} steps of conduction motes too few to even out a
     * network still find their way to it.
     */
    public void want(BlockPos pos) {
        var store = stores.get(pos);
        if (store != null) {
            wanted.put(store, step + WANTED_STEPS);
        }
    }

    /** @return each store's distance, in stores, to the nearest one motes are wanted at; none for a network wanting none */
    private Map<MoteStore, Integer> distancesToWanted() {
        if (wanted.isEmpty()) {
            return Map.of();
        }
        wanted.values().removeIf(until -> until < step);
        wanted.keySet().removeIf(store -> stores.get(store.pos()) != store);
        var distances = new IdentityHashMap<MoteStore, Integer>();
        var queue = new ArrayDeque<MoteStore>();
        for (var store : wanted.keySet()) {
            distances.put(store, 0);
            queue.add(store);
        }
        while (!queue.isEmpty()) {
            var store = queue.poll();
            for (var direction : Direction.values()) {
                var next = stores.get(store.pos().relative(direction));
                if (next != null && !distances.containsKey(next)) {
                    distances.put(next, distances.get(store) + 1);
                    queue.add(next);
                }
            }
        }
        return distances;
    }

    /** @return the most motes, up to {@code most}, that leave {@code from}'s pressure at least {@code to}'s */
    private static int evening(MoteStore from, MoteStore to, int slot, int most) {
        int low = 0;
        int high = (int) Math.min(most, to.room());
        // The largest amount that leaves the pressures the right way round; they cross only once.
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (from.pressure(slot, from.motes[slot] - mid) >= to.pressure(slot, to.motes[slot] + mid)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Motes that conducted in one step.
     *
     * @param from the store they left
     * @param to the touching store they reached
     */
    public record Flow(BlockPos from, BlockPos to, int grade, int count) {
    }

    private List<MoteNetwork> touching(BlockPos pos) {
        var networks = new LinkedHashSet<MoteNetwork>();
        for (var direction : Direction.values()) {
            var store = stores.get(pos.relative(direction));
            if (store != null) {
                networks.add(networkOf.get(store));
            }
        }
        return List.copyOf(networks);
    }

    // -- saving ---------------------------------------------------------------------------------

    /** One grade's motes in a saved store. */
    private record SavedMotes(int grade, long count) {
        static final Codec<SavedMotes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MoteResource.CODEC.xmap(MoteResource::grade, MoteResource::of).fieldOf("grade").forGetter(SavedMotes::grade),
                Codec.LONG.fieldOf("count").forGetter(SavedMotes::count)
        ).apply(instance, SavedMotes::new));
    }

    /** One store as saved: where it is, what it is and its motes. */
    private record SavedStore(BlockPos pos, StoreKind kind, List<SavedMotes> motes) {
        static final Codec<SavedStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(SavedStore::pos),
                StoreKind.CODEC.optionalFieldOf("kind", StoreKind.VOIDSTONE).forGetter(SavedStore::kind),
                SavedMotes.CODEC.listOf().fieldOf("motes").forGetter(SavedStore::motes)
        ).apply(instance, SavedStore::new));
    }

    /** Every store as saved; the networks follow from which touch. */
    private record Saved(List<SavedStore> stores) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SavedStore.CODEC.listOf().fieldOf("stores").forGetter(Saved::stores)
        ).apply(instance, Saved::new));
    }

    /** A network as Voidworks 0.1 saved it, before conduction: its Voidstone and one pool of motes. */
    private record SavedPool(List<BlockPos> voidstone, List<SavedMotes> motes) {
        static final Codec<SavedPool> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.nonEmptyList(BlockPos.CODEC.listOf()).fieldOf("voidstone").forGetter(SavedPool::voidstone),
                SavedMotes.CODEC.listOf().fieldOf("motes").forGetter(SavedPool::motes)
        ).apply(instance, SavedPool::new));
    }

    /** The stores, each with where it is, what it is and its motes by grade; 0.1's pools read too. */
    public static Codec<VoidNetworks> codec(IntSupplier perVoidstone, IntSupplier perWell) {
        return Codec.either(Saved.CODEC, SavedPool.CODEC.listOf()).xmap(
                saved -> saved.map(stores -> read(perVoidstone, perWell, stores), pools -> readPools(perVoidstone, perWell, pools)),
                networks -> Either.left(networks.write()));
    }

    private static VoidNetworks read(IntSupplier perVoidstone, IntSupplier perWell, Saved saved) {
        var networks = new VoidNetworks(perVoidstone, perWell);
        for (var entry : saved.stores()) {
            networks.join(entry.pos(), entry.kind());
            var store = networks.stores.get(entry.pos());
            for (var motes : entry.motes()) {
                store.motes[MoteStore.slot(motes.grade())] += motes.count();
            }
        }
        return networks;
    }

    /** A 0.1 pool's motes go to its Voidstone, filling them in turn, and what none has room for is lost. */
    private static VoidNetworks readPools(IntSupplier perVoidstone, IntSupplier perWell, List<SavedPool> pools) {
        var networks = new VoidNetworks(perVoidstone, perWell);
        for (var pool : pools) {
            pool.voidstone().forEach(networks::join);
            try (var transaction = Transaction.openRoot()) {
                for (var motes : pool.motes()) {
                    networks.deposit(pool.voidstone().getFirst(), MoteResource.of(motes.grade()),
                            (int) Math.min(Integer.MAX_VALUE, motes.count()), transaction);
                }
                transaction.commit();
            }
        }
        return networks;
    }

    private Saved write() {
        var saved = new ArrayList<SavedStore>();
        for (var store : stores.values()) {
            var motes = new ArrayList<SavedMotes>();
            for (int slot = 0; slot < MoteStore.SLOTS; slot++) {
                if (store.motes[slot] > 0) {
                    motes.add(new SavedMotes(MoteStore.grade(slot), store.motes[slot]));
                }
            }
            saved.add(new SavedStore(store.pos(), store.kind(), motes));
        }
        return new Saved(saved);
    }
}
