// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How a grown Void Dragon ages, as plain logic: it is {@link LifeStage#YOUNG young}, adult, senior and
 * old by the cargo it has unloaded over its life, each stage lasting {@code length} of it, and has
 * outlived the last once that too runs out. Each stage multiplies its speed, stacked on its health's
 * share floored at a quarter, and its load. Only what it unloads as an adult counts toward its eggs.
 *
 * @param length the cargo each stage lasts
 * @param factors each stage's speed and load factors, for every stage
 */
public record LifeStages(long length, Map<LifeStage, Factors> factors) {

    /** Provisional: a young dragon a quick courier, an old one a slow freighter. */
    public static final Map<LifeStage, Factors> DEFAULTS = Collections.unmodifiableMap(new EnumMap<>(Map.of(
            LifeStage.YOUNG, new Factors(1.0, 1.0),
            LifeStage.ADULT, new Factors(0.6, 2.0),
            LifeStage.SENIOR, new Factors(0.35, 3.5),
            LifeStage.OLD, new Factors(0.2, 6.0))));

    /** The default speed factors, young first, as the server config lists them. */
    public static final List<Double> DEFAULT_SPEEDS = Arrays.stream(LifeStage.values()).map(stage -> DEFAULTS.get(stage).speed()).toList();
    /** The default load factors, young first, as the server config lists them. */
    public static final List<Double> DEFAULT_LOADS = Arrays.stream(LifeStage.values()).map(stage -> DEFAULTS.get(stage).load()).toList();

    /** The slowest health's share is of a whole dragon's speed: a quarter. */
    private static final int SLOWEST = VoidDragonRecord.WHOLE_SPEED / 4;

    /**
     * What a life stage multiplies a dragon's speed and load by.
     *
     * @param speed its speed factor, above 0
     * @param load its load factor, above 0
     */
    public record Factors(double speed, double load) {

        public Factors {
            if (!(speed > 0) || !(load > 0)) {
                throw new IllegalArgumentException("a stage's factors are positive: " + speed + ", " + load);
            }
        }
    }

    public LifeStages {
        if (length < 1) {
            throw new IllegalArgumentException("a stage lasts at least a mote: " + length);
        }
        if (!factors.keySet().containsAll(Arrays.asList(LifeStage.values()))) {
            throw new IllegalArgumentException("every stage has its factors: " + factors);
        }
        factors = Collections.unmodifiableMap(new EnumMap<>(factors));
    }

    /**
     * @param speeds each stage's speed factor, young first
     * @param loads each stage's load factor, young first
     * @return stages of {@code length} at those factors; at the {@link #DEFAULTS} unless each list is four positive factors
     */
    public static LifeStages of(long length, List<? extends Double> speeds, List<? extends Double> loads) {
        var stages = LifeStage.values();
        if (speeds.size() != stages.length || loads.size() != stages.length
                || !speeds.stream().allMatch(factor -> factor > 0) || !loads.stream().allMatch(factor -> factor > 0)) {
            return new LifeStages(length, DEFAULTS);
        }
        var factors = new EnumMap<LifeStage, Factors>(LifeStage.class);
        for (var stage : stages) {
            factors.put(stage, new Factors(speeds.get(stage.ordinal()), loads.get(stage.ordinal())));
        }
        return new LifeStages(length, factors);
    }

    /** @return the stage of a dragon that has unloaded {@code cargo}; old beyond the old stage's end */
    public LifeStage of(long cargo) {
        var stages = LifeStage.values();
        return stages[(int) Math.min(stages.length - 1, cargo / length)];
    }

    /** @return the cargo a dragon has unloaded as it enters {@code stage} */
    public long start(LifeStage stage) {
        return stage.ordinal() * length;
    }

    /** @return whether a dragon that has unloaded {@code cargo} has outlived its old stage, and dies */
    public boolean outlived(long cargo) {
        return cargo / length >= LifeStage.values().length;
    }

    /** @return the most a dragon that has unloaded {@code cargo} carries, of {@code base} for a young one */
    public long load(long base, long cargo) {
        return Math.max(1, (long) (base * factors.get(of(cargo)).load()));
    }

    /** @return {@code dragon}'s speed, of {@link VoidDragonRecord#WHOLE_SPEED}: its stage's factor times its health's share, that down to a quarter */
    public int speed(VoidDragonRecord dragon) {
        int share = Math.max(SLOWEST, VoidDragonRecord.WHOLE_SPEED * dragon.health() / VoidDragonRecord.FULL_HEALTH);
        return Math.max(1, (int) Math.round(share * factors.get(of(dragon.cargo())).speed()));
    }

    /** @return how much of {@code motes} unloaded by a dragon that had unloaded {@code cargo} before counts toward its eggs: what falls in its adulthood */
    public long towardEggs(long cargo, long motes) {
        long start = length * LifeStage.ADULT.ordinal();
        long end = start + length;
        return Math.max(0, Math.min(end, cargo + motes) - Math.max(start, cargo));
    }
}
