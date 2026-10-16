// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

/**
 * The Well hatches as a Matter-line dragon's {@link Routing} sees them, whether or not their chunks are
 * loaded: what each holds and asks for, and moving items in and out of it. {@code DragonRouting} reads
 * them from the server's saved data; a test fakes them.
 */
public interface Hatches {

    /** No hatches anywhere: for a routing that carries motes alone. */
    Hatches NONE = new Hatches() {

        @Override
        public List<Hatch> on(Place well) {
            return List.of();
        }

        @Override
        public long take(Place hatch, Ware ware) {
            return 0;
        }

        @Override
        public long put(Place hatch, Ware ware) {
            return 0;
        }
    };

    /** @return the hatches against the Well at {@code well} that are its owner's, the only ones its owner's dragons touch */
    List<Hatch> on(Place well);

    /** Takes up to {@code ware}'s count of it, items alike, out of the hatch at {@code hatch}. @return how many it took */
    long take(Place hatch, Ware ware);

    /** Puts up to {@code ware}'s count of it into the hatch at {@code hatch}, as far as it has room. @return how many went in */
    long put(Place hatch, Ware ware);

    /**
     * A Well hatch as it is now.
     *
     * @param requester whether it is a requester, which asks and never gives; else a provider, which offers what it holds and never asks
     * @param request what a requester asks for; empty for a provider, and a requester asking for nothing
     * @param contents what it holds, no two alike
     * @param emptySlots how many of its slots hold nothing
     */
    record Hatch(Place place, boolean requester, Optional<Request> request, List<Ware> contents, int emptySlots) {

        public Hatch {
            contents = List.copyOf(contents);
        }

        /** @return whether it is a provider, which offers what it holds */
        boolean provider() {
            return !requester;
        }

        /** @return how many of {@code item}'s kind it holds, whatever their components */
        long holds(Identifier item) {
            return contents.stream().filter(ware -> ware.item().equals(item)).mapToLong(Ware::count).sum();
        }

        /** @return how many of the item it asks for it still lacks, before what is on its way to it */
        long lacks() {
            return request.map(asked -> Math.max(0, asked.count() - holds(asked.item()))).orElse(0L);
        }
    }

    /**
     * What a requester asks for: an item's kind, its components ignored, so many stacks of it.
     *
     * @param item the item's id
     * @param stackSize how many of it make a stack
     * @param stacks how many stacks of it
     */
    record Request(Identifier item, int stackSize, int stacks) {

        /** @return how many items it asks for */
        public long count() {
            return (long) stacks * stackSize;
        }
    }
}
