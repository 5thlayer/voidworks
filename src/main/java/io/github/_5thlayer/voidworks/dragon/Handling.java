// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How a Matter-line dragon is seen loading or unloading at a stop, its hatches already emptied or filled
 * as the stop began: once it is up, {@link Showing#EMERGE_TICKS} into the stop, each {@link Passing}
 * moves between its hatch and the dragon's paws in {@link #PASS_TICKS}, their starts spread so the last,
 * or a lone one, lands {@link #LANDS_BEFORE_END} before the stop ends, and it holds still over the shaft while they
 * move. Loading, it hangs its first stack once the first has reached its paws; unloading, it hangs what
 * it hung as the stop began until the last has landed, then the first stack of what it still carries.
 * With nothing passing, what it hangs changes once it is up. Plain logic, tested with no game running.
 *
 * @param loading whether the items move from the hatches to its paws, else from its paws into them
 * @param passing the items seen moving, in the order they start
 * @param hung what it hung as the stop began
 */
public record Handling(boolean loading, List<Passing> passing, Optional<Ware> hung) {

    /** How long, in ticks, an item takes to move between a hatch and the paws. */
    public static final int PASS_TICKS = 20;

    /** How long, in ticks, before its stop ends the last item has landed. */
    static final int LANDS_BEFORE_END = 10;

    static final Codec<Handling> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("loading").forGetter(Handling::loading),
            Passing.CODEC.listOf().fieldOf("passing").forGetter(Handling::passing),
            Ware.CODEC.optionalFieldOf("hung").forGetter(Handling::hung)
    ).apply(instance, Handling::new));

    public Handling {
        passing = List.copyOf(passing);
    }

    /** @return a load seen taken out of the hatches of {@code passing}, by a dragon that carried nothing */
    static Handling loading(List<Passing> passing) {
        return new Handling(true, passing, Optional.empty());
    }

    /** @return a load seen put into the hatches of {@code passing}, by a dragon that hung {@code hung} */
    static Handling unloading(List<Passing> passing, Optional<Ware> hung) {
        return new Handling(false, passing, hung);
    }

    /** @return how many ticks into a stop of {@code stopTicks} the {@code index}th item starts to move */
    public long start(int index, int stopTicks) {
        long first = Showing.EMERGE_TICKS;
        long last = Math.max(first, stopTicks - LANDS_BEFORE_END - PASS_TICKS);
        return passing.size() == 1 ? last : first + index * (last - first) / (passing.size() - 1);
    }

    /** @return how many ticks into a stop of {@code stopTicks} the last item has landed; once it is up, with none */
    public long landed(int stopTicks) {
        return passing.isEmpty() ? Showing.EMERGE_TICKS : start(passing.size() - 1, stopTicks) + PASS_TICKS;
    }

    /**
     * @param into how many ticks into its stop it is
     * @param after the first stack of what it carries now, its hatches emptied or filled
     * @return what it hangs then
     */
    public Optional<Ware> hangs(long into, Optional<Ware> after, int stopTicks) {
        long changes = loading && !passing.isEmpty() ? start(0, stopTicks) + PASS_TICKS : landed(stopTicks);
        return into < changes ? hung : after;
    }

    /** @return whether it holds still over the shaft {@code into} ticks into its stop, while items move */
    public boolean holdsStill(long into, int stopTicks) {
        return !passing.isEmpty() && into >= Showing.EMERGE_TICKS && into < landed(stopTicks);
    }
}
