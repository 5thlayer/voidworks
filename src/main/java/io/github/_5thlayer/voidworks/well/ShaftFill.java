// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a Void Well's shaft shows of its network: a void fluid as high as the network is full, in
 * sixteenths of its capacity rounded up, so any motes at all show, the top only when it is full, and butterflies in it of the
 * grades it holds, each grade's share in sixteenths of what it holds, at least one for any grade
 * held. Coarse on purpose: the server tells a watcher only when the shaft would look different, not
 * as each mote comes or goes. A look, not a count.
 *
 * @param step   how high the fluid stands, from 0, empty, to {@link #STEPS}, the top of the shaft
 * @param shares grade to its share of the butterflies; no grade the network holds none of
 */
public record ShaftFill(int step, SortedMap<Integer, Integer> shares) {

    /** The steps the fluid rises in, from empty to the top of the shaft. */
    public static final int STEPS = 16;
    /** The butterflies a full shaft shows. */
    public static final int MOST_BUTTERFLIES = 12;
    /** What a shaft shows of an empty network, and of a Well that is Dormant or not whole. */
    public static final ShaftFill EMPTY = new ShaftFill(0, new TreeMap<>());

    private static final Codec<SortedMap<Integer, Integer>> SHARES_CODEC = Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.INT)
            .xmap(TreeMap::new, Map::copyOf);

    public static final Codec<ShaftFill> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("step").forGetter(ShaftFill::step),
            SHARES_CODEC.fieldOf("shares").forGetter(ShaftFill::shares)).apply(instance, ShaftFill::new));

    public ShaftFill {
        shares = new TreeMap<>(shares);
    }

    /**
     * @param capacity how many motes the network holds at most
     * @param byGrade  the motes it holds, grade to count
     * @return what its Wells' shafts show
     */
    public static ShaftFill of(long capacity, SortedMap<Integer, Long> byGrade) {
        long total = byGrade.values().stream().mapToLong(Long::longValue).sum();
        if (total <= 0 || capacity <= 0) {
            return EMPTY;
        }
        // Only a full network reaches the top, so a full shaft means full.
        int step = total >= capacity ? STEPS : (int) Math.min(STEPS - 1, Math.ceilDiv(total * STEPS, capacity));
        var shares = new TreeMap<Integer, Integer>();
        byGrade.forEach((grade, count) -> {
            if (count > 0) {
                shares.put(grade, (int) Math.max(1, Math.round((double) count * STEPS / total)));
            }
        });
        return new ShaftFill(step, shares);
    }

    /** @return how high the fluid stands, as a share of the shaft's height, from 0 to 1 */
    public double level() {
        return (double) step / STEPS;
    }

    /** @return how many butterflies drift in it: more the fuller it is, at least one while it shows any */
    public int butterflies() {
        return Math.ceilDiv(step * MOST_BUTTERFLIES, STEPS);
    }

    /**
     * @param roll any number at or above 0, such as a random one
     * @return the grade of the butterfly {@code roll} picks, each grade as often as its share
     * @throws IllegalStateException when it shows no butterflies
     */
    public int grade(int roll) {
        int sum = shares.values().stream().mapToInt(Integer::intValue).sum();
        if (sum == 0) {
            throw new IllegalStateException("an empty shaft has no butterflies");
        }
        int left = roll % sum;
        for (var share : shares.entrySet()) {
            left -= share.getValue();
            if (left < 0) {
                return share.getKey();
            }
        }
        throw new AssertionError("a roll within the shares picks one");
    }
}
