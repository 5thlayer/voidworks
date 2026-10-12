// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.energy.MoteResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;

/**
 * Every Voidstone network of one dimension, as plain logic over positions: which Voidstone is in
 * which network. Voidstone that touches face to face is one network, and a Voidstone that comes to
 * touch two networks merges them, motes and all: pressure, not topology, will decide where motes
 * go, so a merge is harmless.
 *
 * <p>{@link VoidstoneNetworks} keeps one per dimension, saved with the world.
 */
public final class VoidNetworks {

    private final IntSupplier perVoidstone;
    private final Map<BlockPos, MoteNetwork> byVoidstone = new HashMap<>();
    private Runnable changed = () -> {
    };

    /** @param perVoidstone how many motes each Voidstone adds to its network's capacity */
    public VoidNetworks(IntSupplier perVoidstone) {
        this.perVoidstone = perVoidstone;
    }

    /** Runs {@code changed} whenever a network gains or loses Voidstone or motes, to mark them for saving. */
    void onChange(Runnable changed) {
        this.changed = changed;
    }

    /** @return the network of the Voidstone at {@code pos}, or empty when no Voidstone there is in one */
    public Optional<MoteNetwork> at(BlockPos pos) {
        return Optional.ofNullable(byVoidstone.get(pos));
    }

    /**
     * Puts a Voidstone at {@code pos} into a network: the one it touches, all of them merged when it
     * touches several, or a new one of its own. A position already in a network stays where it is.
     */
    public void join(BlockPos pos) {
        pos = pos.immutable();
        if (byVoidstone.containsKey(pos)) {
            return;
        }
        var touching = touching(pos);
        MoteNetwork network;
        if (touching.isEmpty()) {
            network = new MoteNetwork(perVoidstone, () -> changed.run());
        } else {
            // The largest takes in the rest, so the fewest positions move.
            network = touching.stream().max(Comparator.comparingInt(MoteNetwork::voidstoneCount)).orElseThrow();
            for (var other : touching) {
                if (other != network) {
                    network.absorb(other);
                    other.voidstone.forEach(stone -> byVoidstone.put(stone, network));
                }
            }
        }
        network.voidstone.add(pos);
        byVoidstone.put(pos, network);
        changed.run();
    }

    /**
     * Takes the Voidstone at {@code pos} out of its network, which loses its share of the capacity
     * and the motes beyond what is left, the lowest grades first. Voidstone cannot be broken, so only
     * a command or a creative player does this; a network it splits stays one pool.
     */
    public void leave(BlockPos pos) {
        var network = byVoidstone.remove(pos);
        if (network == null) {
            return;
        }
        network.voidstone.remove(pos);
        network.trim();
        changed.run();
    }

    /**
     * @return how many more motes the network of a Voidstone placed at {@code pos} would take: its
     *         own capacity and what the networks it would join have spare, or that of the network
     *         already there
     */
    public long roomIfJoined(BlockPos pos) {
        var already = byVoidstone.get(pos);
        if (already != null) {
            return already.room();
        }
        return perVoidstone.getAsInt() + touching(pos).stream().mapToLong(MoteNetwork::room).sum();
    }

    private List<MoteNetwork> touching(BlockPos pos) {
        var networks = new LinkedHashSet<MoteNetwork>();
        for (var direction : Direction.values()) {
            var network = byVoidstone.get(pos.relative(direction));
            if (network != null) {
                networks.add(network);
            }
        }
        return List.copyOf(networks);
    }

    // -- saving ---------------------------------------------------------------------------------

    /** One grade's motes in a saved network. */
    private record SavedMotes(int grade, long count) {
        static final Codec<SavedMotes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MoteResource.CODEC.xmap(MoteResource::grade, MoteResource::of).fieldOf("grade").forGetter(SavedMotes::grade),
                Codec.LONG.fieldOf("count").forGetter(SavedMotes::count)
        ).apply(instance, SavedMotes::new));
    }

    /** One network as saved: its Voidstone and its motes. */
    private record SavedNetwork(List<BlockPos> voidstone, List<SavedMotes> motes) {
        static final Codec<SavedNetwork> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.nonEmptyList(BlockPos.CODEC.listOf()).fieldOf("voidstone").forGetter(SavedNetwork::voidstone),
                SavedMotes.CODEC.listOf().fieldOf("motes").forGetter(SavedNetwork::motes)
        ).apply(instance, SavedNetwork::new));
    }

    /** The networks as a list, each with its Voidstone and its motes by grade. */
    public static Codec<VoidNetworks> codec(IntSupplier perVoidstone) {
        return SavedNetwork.CODEC.listOf().xmap(saved -> read(perVoidstone, saved), VoidNetworks::write);
    }

    private static VoidNetworks read(IntSupplier perVoidstone, List<SavedNetwork> saved) {
        var networks = new VoidNetworks(perVoidstone);
        for (var entry : saved) {
            var network = new MoteNetwork(perVoidstone, () -> networks.changed.run());
            for (var pos : entry.voidstone()) {
                network.voidstone.add(pos);
                networks.byVoidstone.put(pos, network);
            }
            for (var motes : entry.motes()) {
                network.motes[MoteNetwork.slot(motes.grade())] += motes.count();
            }
        }
        return networks;
    }

    private List<SavedNetwork> write() {
        var saved = new ArrayList<SavedNetwork>();
        for (var network : new LinkedHashSet<>(byVoidstone.values())) {
            var motes = new ArrayList<SavedMotes>();
            for (int slot = 0; slot < network.size(); slot++) {
                if (network.motes[slot] > 0) {
                    motes.add(new SavedMotes(MoteNetwork.grade(slot), network.motes[slot]));
                }
            }
            saved.add(new SavedNetwork(List.copyOf(network.voidstone), motes));
        }
        return saved;
    }
}
