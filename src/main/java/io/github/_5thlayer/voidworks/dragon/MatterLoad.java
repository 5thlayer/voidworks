// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * The items a Matter-line dragon carries, counted in stacks, each full by its item's stack size: a
 * filled container, such as a shulker box, stacks by one, so it takes a stack. Items alike are kept
 * together, in the order it first took them. It keeps the Toll it paid as it loaded ({@link Toll}),
 * which nothing it does with it afterwards changes.
 *
 * @param wares what it carries, no two alike and none of them nothing
 * @param toll the base void energy it paid as it loaded; none once it carries nothing
 */
public record MatterLoad(List<Ware> wares, long toll) implements Load {

    /** No items. */
    public static final MatterLoad NONE = new MatterLoad(List.of());

    private static final Codec<MatterLoad> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ware.CODEC.listOf().fieldOf("wares").forGetter(MatterLoad::wares),
            Codec.LONG.optionalFieldOf("toll", 0L).forGetter(MatterLoad::toll)
    ).apply(instance, MatterLoad::new));

    /** Saved with its Toll, and read too as it was saved before it had one, a list of its wares. */
    static final Codec<MatterLoad> CODEC = Codec.either(RECORD_CODEC, Ware.CODEC.listOf())
            .xmap(either -> either.map(load -> load, MatterLoad::new), Either::left);

    /** Items that paid no Toll. */
    public MatterLoad(List<Ware> wares) {
        this(wares, 0);
    }

    public MatterLoad {
        var merged = new ArrayList<Ware>();
        for (var ware : wares) {
            if (ware.count() == 0) {
                continue;
            }
            int alike = indexOfAlike(merged, ware);
            if (alike < 0) {
                merged.add(ware);
            } else {
                merged.set(alike, ware.withCount(merged.get(alike).count() + ware.count()));
            }
        }
        wares = List.copyOf(merged);
        toll = wares.isEmpty() ? 0 : toll;
    }

    /** @return how many stacks it fills, each item's part stack counting as one */
    @Override
    public long amount() {
        return wares.stream().mapToLong(Ware::stacks).sum();
    }

    @Override
    public boolean isEmpty() {
        return wares.isEmpty();
    }

    /** @return how many of each item's kind it carries, whatever their components */
    @Override
    public Map<Identifier, Long> items() {
        var items = new LinkedHashMap<Identifier, Long>();
        wares.forEach(ware -> items.merge(ware.item(), ware.count(), Long::sum));
        return items;
    }

    /** @return a stack of the items it took first, as many of them as a stack holds at most */
    @Override
    public Optional<Ware> firstStack() {
        return wares.stream().findFirst().map(first -> first.withCount(Math.min(first.count(), first.stackSize())));
    }

    /** @return this load with {@code ware} added to it */
    MatterLoad adding(Ware ware) {
        var added = new ArrayList<>(wares);
        added.add(ware);
        return new MatterLoad(added, toll);
    }

    /** @return this load with {@code ware} taken out of it, as much of it as it carries */
    MatterLoad less(Ware ware) {
        var left = new ArrayList<Ware>();
        for (var carried : wares) {
            left.add(carried.alike(ware) ? carried.withCount(Math.max(0, carried.count() - ware.count())) : carried);
        }
        return new MatterLoad(left, toll);
    }

    /** @return these items, having paid {@code toll} as they loaded */
    MatterLoad paid(long toll) {
        return new MatterLoad(wares, toll);
    }

    /** @return the items it took first, as many as fill {@code most} stacks: whole stacks of the one it cuts through */
    MatterLoad upTo(long most) {
        var kept = new ArrayList<Ware>();
        long left = most;
        for (var ware : wares) {
            long count = ware.within(left);
            kept.add(ware.withCount(count));
            left -= Ware.stacks(count, ware.stackSize());
        }
        return new MatterLoad(kept);
    }

    /** @return this load and {@code other} together */
    MatterLoad plus(MatterLoad other) {
        var both = new ArrayList<>(wares);
        both.addAll(other.wares);
        return new MatterLoad(both);
    }

    /** @return this load with {@code other} taken out of it, as much of each of its items as it carries */
    MatterLoad less(MatterLoad other) {
        var left = this;
        for (var ware : other.wares) {
            left = left.less(ware);
        }
        return left;
    }

    /** @return how many more of {@code ware} it takes without filling more than {@code most} stacks */
    long room(Ware ware, long most) {
        long others = wares.stream().filter(carried -> !carried.alike(ware)).mapToLong(Ware::stacks).sum();
        long alike = wares.stream().filter(ware::alike).mapToLong(Ware::count).sum();
        return Math.max(0, (most - others) * ware.stackSize() - alike);
    }

    private static int indexOfAlike(List<Ware> wares, Ware ware) {
        for (int index = 0; index < wares.size(); index++) {
            if (wares.get(index).alike(ware)) {
                return index;
            }
        }
        return -1;
    }
}
