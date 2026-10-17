// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What a Matter-line load pays as it loads, from the departure Well's network, by the spend rule at
 * that Well's core (spec § Moving matter, Toll). It is counted in stack-equivalents: a part stack pays
 * its share, its items over the item's stack size, and a filled container pays for what it holds,
 * counted the same way, plus one for itself. A stack-equivalent costs {@code stackPrice} base void
 * energy, doubled for each step the destination's Void Pressure stands above the departure's; a level
 * or downhill trip pays the price alone. A base void energy is what one mote releases one step above
 * the pressure where it is spent ({@link VoidEnergy#BASE}), so the price reads in the motes that pay it.
 *
 * <p>One price for every item, datapack data on the Well hatch's block (ADR 0009), so a pack can
 * raise or lower it.
 *
 * @param stackPrice the base void energy a stack-equivalent costs on a level trip
 */
public record Toll(long stackPrice) {

    public static final Codec<Toll> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("stack_price").forGetter(Toll::stackPrice)
    ).apply(instance, Toll::new));

    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "toll");

    /** The Toll, keyed by the Well hatch's block, where a load is lifted. */
    public static final DataMapType<Block, Toll> DATA_MAP = DataMapType.builder(DATA_MAP_ID, Registries.BLOCK, CODEC).build();

    /** No Toll, with no entry, as an egg with none is free. */
    public static final Toll FREE = new Toll(0);

    public Toll {
        if (stackPrice < 0) {
            throw new IllegalArgumentException("a Toll is never below nothing: " + stackPrice);
        }
    }

    /** @return the Toll for a load lifted from a hatch of {@code hatch}'s block; {@link #FREE} with no entry */
    public static Toll of(BlockState hatch) {
        var entry = hatch.typeHolder().getData(DATA_MAP);
        return entry == null ? FREE : entry;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> event.register(DATA_MAP));
    }

    /** @return the base void energy a stack-equivalent costs lifted from Void Pressure {@code from} to {@code to} */
    public long perStack(int from, int to) {
        long price = stackPrice;
        for (int step = from; step < to; step++) {
            price = LongMath.saturatedMultiply(price, 2);
        }
        return price;
    }

    /** @return the base void energy {@code ware} pays lifted from Void Pressure {@code from} to {@code to}, each part stack its share, rounded up */
    public long of(Ware ware, int from, int to) {
        long perStack = perStack(from, to);
        long held = 0;
        for (var inside : ware.held().entrySet()) {
            held = LongMath.saturatedAdd(held, share(perStack, inside.getValue(), inside.getKey()));
        }
        return LongMath.saturatedAdd(share(perStack, ware.count(), ware.stackSize()), LongMath.saturatedMultiply(held, ware.count()));
    }

    /** @return the base void energy {@code wares} pay together, lifted from Void Pressure {@code from} to {@code to} */
    public long of(Iterable<Ware> wares, int from, int to) {
        long toll = 0;
        for (var ware : wares) {
            toll = LongMath.saturatedAdd(toll, of(ware, from, to));
        }
        return toll;
    }

    /** @return how many of {@code ware}, up to its count, {@code budget} base void energy pays for, lifted from Void Pressure {@code from} to {@code to} */
    public long affordable(Ware ware, int from, int to, long budget) {
        long low = 0;
        long high = ware.count();
        // The most whose Toll fits the budget: the Toll grows with the count.
        while (low < high) {
            long mid = low + (high - low + 1) / 2;
            if (of(ware.withCount(mid), from, to) <= budget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /** @return the base void energy {@code motes}, grade to count, are worth spent at Void Pressure {@code pressure}: what they pay a Toll with */
    public static long budget(Map<Integer, Long> motes, int pressure) {
        return VoidEnergy.worth(motes, pressure) / VoidEnergy.BASE;
    }

    /**
     * Plans paying {@code toll} base void energy out of {@code motes} at Void Pressure {@code pressure},
     * by the spend rule: the lowest grade worth something there first.
     *
     * @return what to take, or empty when they are not worth it
     */
    public static Optional<VoidEnergy.Spend> spend(Map<Integer, Long> motes, int pressure, long toll) {
        var counts = new TreeMap<Integer, Integer>();
        motes.forEach((grade, count) -> counts.put(grade, Ints.saturatedCast(count)));
        return VoidEnergy.plan(counts, pressure, LongMath.saturatedMultiply(toll, VoidEnergy.BASE));
    }

    /** @return {@code count} items, {@code stackSize} to a stack, as a share of {@code perStack}, rounded up */
    private static long share(long perStack, long count, int stackSize) {
        return LongMath.divide(LongMath.saturatedMultiply(perStack, count), stackSize, RoundingMode.CEILING);
    }
}
