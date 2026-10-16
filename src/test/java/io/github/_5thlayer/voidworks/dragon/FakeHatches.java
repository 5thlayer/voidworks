// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

/** Well hatches as plain data: each against a Well, its slots each holding up to a stack of one ware. */
final class FakeHatches implements Hatches {

    /** How many slots each hatch holds. */
    static final int SLOTS = 27;

    private record Entry(Place well, boolean requester, Optional<Request> request, Ware[] slots) {
    }

    private final Map<Place, Entry> hatches = new LinkedHashMap<>();

    /** Adds a provider at {@code place} against the Well at {@code well}, holding {@code wares}, a stack to a slot. */
    void provider(Place place, Place well, Ware... wares) {
        add(place, well, false, Optional.empty(), wares);
    }

    /** Adds a requester at {@code place} against the Well at {@code well}, asking for {@code stacks} of {@code item}, holding {@code wares}. */
    void requester(Place place, Place well, Identifier item, int stackSize, int stacks, Ware... wares) {
        add(place, well, true, Optional.of(new Request(item, stackSize, stacks)), wares);
    }

    private void add(Place place, Place well, boolean requester, Optional<Request> request, Ware... wares) {
        hatches.put(place, new Entry(well, requester, request, new Ware[SLOTS]));
        for (var ware : wares) {
            if (put(place, ware) != ware.count()) {
                throw new IllegalArgumentException("a hatch has no room for " + ware);
            }
        }
    }

    /** @return how many of {@code item}'s kind the hatch at {@code place} holds */
    long holds(Place place, Identifier item) {
        return Arrays.stream(hatches.get(place).slots()).filter(ware -> ware != null && ware.item().equals(item)).mapToLong(Ware::count).sum();
    }

    @Override
    public List<Hatch> on(Place well) {
        var on = new ArrayList<Hatch>();
        hatches.forEach((place, entry) -> {
            if (entry.well().equals(well)) {
                var held = Arrays.stream(entry.slots()).filter(ware -> ware != null).toList();
                on.add(new Hatch(place, entry.requester(), entry.request(), new MatterLoad(held).wares(), SLOTS - held.size()));
            }
        });
        return on;
    }

    @Override
    public long take(Place hatch, Ware ware) {
        var slots = hatches.get(hatch).slots();
        long taken = 0;
        for (int slot = 0; slot < slots.length && taken < ware.count(); slot++) {
            if (slots[slot] != null && slots[slot].alike(ware)) {
                long took = Math.min(ware.count() - taken, slots[slot].count());
                slots[slot] = took == slots[slot].count() ? null : slots[slot].withCount(slots[slot].count() - took);
                taken += took;
            }
        }
        return taken;
    }

    /** Onto stacks alike first, then into empty slots. */
    @Override
    public long put(Place hatch, Ware ware) {
        var slots = hatches.get(hatch).slots();
        long put = 0;
        for (int slot = 0; slot < slots.length && put < ware.count(); slot++) {
            if (slots[slot] != null && slots[slot].alike(ware)) {
                long more = Math.min(ware.count() - put, ware.stackSize() - slots[slot].count());
                slots[slot] = slots[slot].withCount(slots[slot].count() + more);
                put += more;
            }
        }
        for (int slot = 0; slot < slots.length && put < ware.count(); slot++) {
            if (slots[slot] == null) {
                long more = Math.min(ware.count() - put, ware.stackSize());
                slots[slot] = ware.withCount(more);
                put += more;
            }
        }
        return put;
    }
}
