// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The spend rule every spend in Voidworks follows, as plain logic with no Minecraft types. A mote's
 * grade and the Void Pressure where it is spent are small integers on one scale; the further the
 * grade exceeds the pressure, the more void energy the mote releases, doubling with each step.
 */
public final class VoidEnergy {

    /**
     * The void energy one mote releases one step above the Void Pressure. Provisional: the numbers
     * are still open (docs/spec/void-energy.md), and this is the one place that holds it.
     */
    public static final long BASE = 100;

    private VoidEnergy() {
    }

    /**
     * What a spend takes.
     *
     * @param motes the motes to take, grade to count, lowest grade first; no grade with none
     * @param released the void energy they release together, at least what the spend asked for
     */
    public record Spend(Map<Integer, Integer> motes, long released) {
    }

    /**
     * The void energy one mote of {@code grade} releases when spent at {@code pressure}.
     *
     * @return {@code BASE × 2^(grade − pressure − 1)}, or empty when the grade is not above the
     *         pressure: the spend is refused
     */
    public static OptionalLong release(int grade, int pressure) {
        if (grade <= pressure) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(BASE << (grade - pressure - 1));
    }

    /**
     * The grade a spend takes first from the motes on hand: the lowest grade still worth something
     * at {@code pressure}, so the higher grades are kept for where they are worth more.
     *
     * @param stacks the mote stacks on hand, grade to count; a grade with no motes is skipped
     * @return the lowest grade above the pressure with motes, or empty when there is none
     */
    static OptionalInt chooseGrade(Map<Integer, Integer> stacks, int pressure) {
        return stacks.entrySet().stream()
                .filter(stack -> stack.getValue() > 0 && stack.getKey() > pressure)
                .mapToInt(Map.Entry::getKey)
                .min();
    }

    /**
     * Plans a spend of at least {@code required} void energy at {@code pressure}: the lowest worthwhile
     * grade first, moving up a grade only when the lower ones are used up, and in each grade only as
     * many motes as are needed. A grade not above the pressure is never taken.
     *
     * @param stacks the mote stacks on hand, grade to count
     * @param required the void energy to release at least, not negative
     * @return what to take, or empty when the motes on hand cannot release {@code required}: the
     *         spend is refused, and a caller takes nothing
     */
    public static Optional<Spend> plan(Map<Integer, Integer> stacks, int pressure, long required) {
        if (required < 0) {
            throw new IllegalArgumentException("cannot release a negative amount: " + required);
        }
        var motes = new LinkedHashMap<Integer, Integer>();
        var remaining = new LinkedHashMap<>(stacks);
        long released = 0;
        while (released < required) {
            var grade = chooseGrade(remaining, pressure);
            if (grade.isEmpty()) {
                return Optional.empty();
            }
            int g = grade.getAsInt();
            long each = release(g, pressure).orElseThrow();
            long needed = (required - released + each - 1) / each;
            int taken = (int) Math.min(needed, remaining.get(g));
            motes.put(g, taken);
            released += taken * each;
            remaining.remove(g);
        }
        return Optional.of(new Spend(motes, released));
    }
}
