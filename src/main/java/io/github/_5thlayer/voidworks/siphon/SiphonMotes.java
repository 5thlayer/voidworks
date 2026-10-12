// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * What a Void Siphon holds, as plain logic: motes pooled by grade, grade to count, lowest grade
 * first, with no grade that has none. It is the Siphon's {@code voidworks:siphon_motes} data
 * component, so it goes wherever the Siphon goes; {@link SiphonMoteHandler} reaches it as a mote store.
 *
 * @param byGrade grade to count, every grade one a mote has and every count above 0
 */
public record SiphonMotes(SortedMap<Integer, Integer> byGrade) {

    /** A Siphon holding no motes. */
    public static final SiphonMotes EMPTY = new SiphonMotes(new TreeMap<>());

    /** One grade's motes as saved. */
    private record Saved(int grade, int count) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.validate(SiphonMotes::validGrade).fieldOf("grade").forGetter(Saved::grade),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Saved::count)
        ).apply(instance, Saved::new));
    }

    /** Saved as a list of grades with their counts, each grade once. */
    public static final Codec<SiphonMotes> CODEC = Saved.CODEC.listOf().comapFlatMap(SiphonMotes::read, SiphonMotes::write);

    public static final StreamCodec<RegistryFriendlyByteBuf, SiphonMotes> STREAM_CODEC = ByteBufCodecs.<RegistryFriendlyByteBuf, Integer, Integer, Map<Integer, Integer>>map(
            size -> new TreeMap<>(), ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_INT)
            .map(map -> {
                var motes = EMPTY;
                for (var entry : map.entrySet()) {
                    motes = motes.with(entry.getKey(), entry.getValue());
                }
                return motes;
            }, SiphonMotes::byGrade);

    public SiphonMotes {
        byGrade = Collections.unmodifiableSortedMap(new TreeMap<>(byGrade));
    }

    /**
     * How many motes a Void Siphon's harvest yields from a block of {@code density}: one Density step
     * below a Displacer's, never below 1, so 1, 1, 4, 16 by default.
     *
     * @param displacer how many motes a Displacer's harvest yields by Density, from 1
     * @throws IllegalArgumentException when {@code density} is not above 0: nothing harvests it
     */
    public static int handYield(int density, IntUnaryOperator displacer) {
        if (density < 1) {
            throw new IllegalArgumentException("nothing harvests a block of Density " + density);
        }
        return density == 1 ? 1 : Math.max(1, displacer.applyAsInt(density - 1));
    }

    /** @return how many motes of {@code grade} it holds */
    public int count(int grade) {
        return byGrade.getOrDefault(grade, 0);
    }

    /** @return how many motes it holds, of every grade together */
    public long total() {
        long total = 0;
        for (int count : byGrade.values()) {
            total += count;
        }
        return total;
    }

    /**
     * @return these motes with {@code count} of {@code grade}, the grade gone when the count is 0
     * @throws IllegalArgumentException when no mote has {@code grade} or the count is negative
     */
    public SiphonMotes with(int grade, int count) {
        VoidEnergy.requireGrade(grade);
        if (count < 0) {
            throw new IllegalArgumentException("a Siphon cannot hold " + count + " motes of a grade");
        }
        var changed = new TreeMap<>(byGrade);
        if (count == 0) {
            changed.remove(grade);
        } else {
            changed.put(grade, count);
        }
        return new SiphonMotes(changed);
    }

    private static DataResult<Integer> validGrade(int grade) {
        return VoidEnergy.isGrade(grade) ? DataResult.success(grade) : DataResult.error(() -> "no mote has grade " + grade);
    }

    private static DataResult<SiphonMotes> read(List<Saved> saved) {
        var grades = new HashSet<Integer>();
        var motes = EMPTY;
        for (var entry : saved) {
            if (!grades.add(entry.grade())) {
                return DataResult.error(() -> "a Siphon's motes name grade " + entry.grade() + " twice");
            }
            motes = motes.with(entry.grade(), entry.count());
        }
        return DataResult.success(motes);
    }

    private List<Saved> write() {
        return byGrade.entrySet().stream().map(entry -> new Saved(entry.getKey(), entry.getValue())).toList();
    }
}
