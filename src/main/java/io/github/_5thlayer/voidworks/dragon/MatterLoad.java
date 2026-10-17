// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/**
 * The items a Matter-line dragon carries, counted in stacks, each full by its item's stack size: a
 * filled container, such as a shulker box, stacks by one, so it takes a stack. Items alike are kept
 * together, in the order it first took them.
 *
 * @param wares what it carries, no two alike and none of them nothing
 */
public record MatterLoad(List<Ware> wares) implements Load {

    /** No items. */
    public static final MatterLoad NONE = new MatterLoad(List.of());

    static final Codec<MatterLoad> CODEC = Ware.CODEC.listOf().xmap(MatterLoad::new, MatterLoad::wares);

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
        return new MatterLoad(added);
    }

    /** @return this load with {@code ware} taken out of it, as much of it as it carries */
    MatterLoad less(Ware ware) {
        var left = new ArrayList<Ware>();
        for (var carried : wares) {
            left.add(carried.alike(ware) ? carried.withCount(Math.max(0, carried.count() - ware.count())) : carried);
        }
        return new MatterLoad(left);
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
