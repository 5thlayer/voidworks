// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.mojang.serialization.Codec;

/**
 * The motes a Mote-line dragon carries.
 *
 * @param motes grade to count
 */
public record MoteLoad(SortedMap<Integer, Long> motes) implements Load {

    /** No motes. */
    public static final MoteLoad NONE = new MoteLoad(new TreeMap<>());

    /** Saved as it was before Lines: a map of grade, as a string, to count. */
    static final Codec<MoteLoad> CODEC = Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.LONG)
            .xmap(MoteLoad::of, MoteLoad::motes);

    public MoteLoad {
        motes = Collections.unmodifiableSortedMap(new TreeMap<>(motes));
    }

    /** @return a load of {@code motes}, grade to count */
    public static MoteLoad of(Map<Integer, Long> motes) {
        return new MoteLoad(new TreeMap<>(motes));
    }

    /** @return how many motes it is, of every grade together */
    @Override
    public long amount() {
        return motes.values().stream().mapToLong(Long::longValue).sum();
    }

    @Override
    public boolean isEmpty() {
        return motes.isEmpty();
    }

    /** @return a spark of each grade it holds motes of */
    @Override
    public Sparks sparks() {
        return Sparks.of(motes.entrySet().stream().filter(grade -> grade.getValue() > 0).map(Map.Entry::getKey).toList());
    }
}
