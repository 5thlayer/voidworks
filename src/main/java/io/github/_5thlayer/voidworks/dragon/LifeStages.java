// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.List;

/**
 * How a grown Void Dragon ages, as plain logic: it is {@link LifeStage#YOUNG young}, adult, senior and
 * old by the cargo it has unloaded over its life, each stage lasting {@code length} of it, and has
 * outlived the last once that too runs out. Each stage multiplies its speed, stacked on its health's
 * share floored at a quarter, and its load. Only what it unloads as an adult counts toward its eggs.
 *
 * @param length the cargo each stage lasts
 * @param speeds each stage's speed factor, young first
 * @param loads each stage's load factor, young first
 */
public record LifeStages(long length, List<Double> speeds, List<Double> loads) {

    /** Provisional: a young dragon a quick courier, an old one a slow freighter. */
    public static final List<Double> DEFAULT_SPEEDS = List.of(1.0, 0.6, 0.35, 0.2);
    public static final List<Double> DEFAULT_LOADS = List.of(1.0, 2.0, 3.5, 6.0);

    /** The slowest health's share is of a whole dragon's speed: a quarter. */
    private static final int SLOWEST = VoidDragonRecord.WHOLE_SPEED / 4;

    public LifeStages {
        if (length < 1) {
            throw new IllegalArgumentException("a stage lasts at least a mote: " + length);
        }
        speeds = stageFactors(speeds, DEFAULT_SPEEDS);
        loads = stageFactors(loads, DEFAULT_LOADS);
    }

    /** @return the stage of a dragon that has unloaded {@code cargo}; old beyond the old stage's end */
    public LifeStage of(long cargo) {
        var stages = LifeStage.values();
        return stages[(int) Math.min(stages.length - 1, cargo / length)];
    }

    /** @return whether a dragon that has unloaded {@code cargo} has outlived its old stage, and dies */
    public boolean outlived(long cargo) {
        return cargo / length >= LifeStage.values().length;
    }

    /** @return the most a dragon that has unloaded {@code cargo} carries, of {@code base} for a young one */
    public long load(long base, long cargo) {
        return Math.max(1, (long) (base * loads.get(of(cargo).ordinal())));
    }

    /** @return {@code dragon}'s speed, of {@link VoidDragonRecord#WHOLE_SPEED}: its stage's factor times its health's share, that down to a quarter */
    public int speed(VoidDragonRecord dragon) {
        int share = Math.max(SLOWEST, VoidDragonRecord.WHOLE_SPEED * dragon.health() / VoidDragonRecord.FULL_HEALTH);
        return Math.max(1, (int) Math.round(share * speeds.get(of(dragon.cargo()).ordinal())));
    }

    /** @return how much of {@code motes} unloaded by a dragon that had unloaded {@code cargo} before counts toward its eggs: what falls in its adulthood */
    public long towardEggs(long cargo, long motes) {
        long start = length * LifeStage.ADULT.ordinal();
        long end = start + length;
        return Math.max(0, Math.min(end, cargo + motes) - Math.max(start, cargo));
    }

    private static List<Double> stageFactors(List<Double> factors, List<Double> defaults) {
        return factors.size() == LifeStage.values().length && factors.stream().allMatch(factor -> factor > 0) ? List.copyOf(factors) : defaults;
    }
}
