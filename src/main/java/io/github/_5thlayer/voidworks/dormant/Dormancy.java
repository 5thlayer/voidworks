// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dormant;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.TouchingMotes;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;

/**
 * Dormant, the one piece every void machine whose recipe costs motes opts into: placed, it does
 * nothing until it has soaked its cost in motes where it stands, from every mote store touching it
 * (a Voidstone network, a Consumer's machine, anything providing {@code voidworks:motes}), by the
 * spend rule at its own block, and from a Void Siphon its holder empties into it. Paid, it wakes,
 * and stays awake for good. A payment in part is kept, across a save and reload too, until the rest
 * arrives.
 *
 * <p>A machine opts in in three places:
 * <ul>
 *   <li>its block entity is a {@link Holder}, holding a {@code Dormancy}, calls {@link #soak} on
 *       each server tick and works only once it returns true, and saves and loads it with
 *       {@link #save} and {@link #load};</li>
 *   <li>its block adds {@link #DORMANT} to its states, true by default, so its look can show it, and
 *       may show a player {@link #status};</li>
 *   <li>its cost, in void energy, is the {@link #DATA_MAP} entry for its block, which a datapack
 *       overrides, such as
 *       {@code {"values": {"voidworks:mutation_chamber": {"cost": 1600}}}} in
 *       {@code data/<namespace>/data_maps/block/dormant_cost.json}. A block with no entry costs
 *       nothing and wakes on its first tick. A machine may instead {@link #setPrice set a price of
 *       its own} when it is placed, which it soaks in place of its block's.</li>
 * </ul>
 */
public final class Dormancy {

    /** Whether the machine is Dormant, for its look; {@link #awake} is the truth, and the state follows it. */
    public static final BooleanProperty DORMANT = BooleanProperty.create("dormant");

    /** The map's id, {@code voidworks:dormant_cost}, which names its file in a datapack. */
    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "dormant_cost");

    /** An entry: the void energy a Dormant machine soaks before it wakes. */
    public record Cost(long cost) {

        public static final Codec<Cost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("cost").forGetter(Cost::cost)
        ).apply(instance, Cost::new));
    }

    /** A void machine's block entity that opts into Dormant, so a Void Siphon finds what it pays. */
    public interface Holder {

        /** @return its Dormancy, the same one each time */
        Dormancy dormancy();

        /**
         * @return why it takes no payment now, such as a Void Well whose well is not whole, which a
         *         player paying it with a Void Siphon is told; empty when it takes one, as most
         *         machines always do
         */
        default Optional<Component> refusesPayment() {
            return Optional.empty();
        }

        /**
         * @return the positions beyond its own block through which it touches the mote stores it soaks
         *         from, for a machine built of several blocks, such as a Void Well's casing and rim;
         *         none for most machines
         */
        default Set<BlockPos> parts() {
            return Set.of();
        }
    }

    /** Each Dormant machine's cost, keyed by its block. */
    public static final DataMapType<Block, Cost> DATA_MAP = DataMapType.builder(DATA_MAP_ID, Registries.BLOCK, Cost.CODEC).build();

    private static final String KEY = "dormancy";
    private static final String PAID_KEY = "paid";
    private static final String AWAKE_KEY = "awake";
    private static final String PRICE_KEY = "price";

    private final BlockEntity machine;
    /** The void energy soaked so far; it may pass the cost by part of a mote. */
    private long paid;
    private boolean awake;
    /** Its own price, in place of its block's {@link #DATA_MAP} entry; empty while it sets none. */
    private OptionalLong price = OptionalLong.empty();
    private int sinceSoak;

    public Dormancy(BlockEntity machine) {
        this.machine = machine;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Dormancy::registerDataMap);
    }

    private static void registerDataMap(RegisterDataMapTypesEvent event) {
        event.register(DATA_MAP);
    }

    /**
     * Soaks what it can of the cost from the mote stores touching the machine, once every
     * {@link VoidworksConfig#soakInterval()} ticks, and wakes once it is paid. Called on each server
     * tick; once awake, it takes nothing more.
     *
     * @return whether the machine is awake, so may work this tick
     */
    public boolean soak(ServerLevel level) {
        if (!awake) {
            if (++sinceSoak < VoidworksConfig.soakInterval() && paid < cost()) {
                return false;
            }
            sinceSoak = 0;
            soakFromNeighbours(level);
            if (paid < cost()) {
                return false;
            }
            awake = true;
            machine.setChanged();
        }
        showAwake(level);
        return true;
    }

    private void soakFromNeighbours(ServerLevel level) {
        var stores = TouchingMotes.at(level, machine.getBlockPos(), parts());
        TouchingMotes.want(level, machine.getBlockPos(), stores);
        for (var store : stores) {
            if (paid >= cost()) {
                return;
            }
            payFrom(level, store.motes(), store.pos().getCenter());
        }
    }

    /**
     * Pays what it can of the rest of the cost from {@code motes}, by the spend rule at the machine's
     * block, as a Void Siphon emptied into it does; it wakes on its next tick once paid. Awake, it
     * takes nothing.
     *
     * @param from where the motes come from, which their butterflies fly from
     * @return the void energy paid
     */
    public long payFrom(ServerLevel level, ResourceHandler<MoteResource> motes, Vec3 from) {
        long owed = cost() - paid;
        if (awake || owed <= 0) {
            return 0;
        }
        var pos = machine.getBlockPos();
        var spend = VoidSpender.spendUpTo(motes, level, pos, owed);
        if (spend.released() > 0) {
            paid += spend.released();
            machine.setChanged();
            spend.motes().forEach((grade, count) -> MoteFlight.toBlock(level, from, pos, grade, count));
        }
        return spend.released();
    }

    /** Pays the rest of the cost from nowhere, as an operator's command does; it wakes on its next tick. */
    public void payInFull() {
        if (paid < cost()) {
            paid = cost();
            machine.setChanged();
        }
    }

    /** Brings the machine's look in line once it is awake, a machine loaded awake included. */
    private void showAwake(ServerLevel level) {
        var state = machine.getBlockState();
        if (state.hasProperty(DORMANT) && state.getValue(DORMANT)) {
            level.setBlock(machine.getBlockPos(), state.setValue(DORMANT, false), Block.UPDATE_ALL);
        }
    }

    /** @return the machine's {@link Holder#parts}, the positions beyond its block it soaks through */
    private Set<BlockPos> parts() {
        return machine instanceof Holder holder ? holder.parts() : Set.of();
    }

    /** @return whether it has been paid and works; once true, it stays true */
    public boolean awake() {
        return awake;
    }

    /** @return the void energy soaked so far, at most the cost */
    public long paid() {
        return Math.min(paid, cost());
    }

    /**
     * Gives the machine a price of its own, in void energy, which it soaks before it wakes in place
     * of its block's {@link #DATA_MAP} entry, such as an egg whose price rests on who placed it. Set
     * when the machine is placed, before it soaks anything; it is saved with the payment.
     *
     * @throws IllegalArgumentException if {@code price} is negative
     */
    public void setPrice(long price) {
        if (price < 0) {
            throw new IllegalArgumentException("a Dormant price is never negative: " + price);
        }
        this.price = OptionalLong.of(price);
        machine.setChanged();
    }

    /** @return the void energy the machine soaks before it wakes: its own price, else its block's {@link #DATA_MAP} entry, 0 with none */
    public long cost() {
        return price.orElseGet(() -> cost(machine.getBlockState()));
    }

    /** @return the void energy a machine of {@code state}'s block soaks before it wakes; 0 with no entry */
    public static long cost(BlockState state) {
        var entry = state.typeHolder().getData(DATA_MAP);
        return entry == null ? 0 : entry.cost();
    }

    /** @return for a player: Dormant with how much of its cost is paid, or awake */
    public Component status() {
        return awake
                ? Component.translatable("voidworks.dormant.awake")
                : Component.translatable("voidworks.dormant.status", paid(), cost());
    }

    public void save(ValueOutput output) {
        var child = output.child(KEY);
        child.putLong(PAID_KEY, paid);
        child.putBoolean(AWAKE_KEY, awake);
        price.ifPresent(own -> child.putLong(PRICE_KEY, own));
    }

    public void load(ValueInput input) {
        var child = input.childOrEmpty(KEY);
        paid = child.getLongOr(PAID_KEY, 0);
        awake = child.getBooleanOr(AWAKE_KEY, false);
        price = child.getLong(PRICE_KEY).map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }
}
