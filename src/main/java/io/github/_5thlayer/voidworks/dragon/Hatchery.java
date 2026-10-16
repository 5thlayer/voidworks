// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

/**
 * Every owner's dragons, server-wide: the eggs soaking, by where they stand and who placed them, and
 * how many dragons each owner has hatched. It sets an egg's price when the egg starts soaking, by
 * the {@link EggPrice} for every dragon its owner has hatched or is soaking, so two eggs
 * placed together never both get the cheap price, and an egg given away starts its new owner cheap.
 * Plain logic with no game running; {@link SavedHatchery} saves it with the world.
 */
public final class Hatchery {

    private record Soaking(Place egg, UUID owner) {

        static final Codec<Soaking> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Place.CODEC.fieldOf("egg").forGetter(Soaking::egg),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Soaking::owner)
        ).apply(instance, Soaking::new));
    }

    private final Map<UUID, Integer> hatched = new HashMap<>();
    private final Map<Place, UUID> soaking = new HashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, so the saved data that holds it is saved. */
    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Starts the egg at {@code egg} soaking for {@code owner}, who placed it, at {@code price}.
     *
     * @return its price, fixed now, in void energy
     */
    public long startSoaking(Place egg, UUID owner, EggPrice price) {
        long fixed = price.of(dragons(owner));
        soaking.put(egg, owner);
        onChange.run();
        return fixed;
    }

    /** The egg at {@code egg} has hatched: its owner has one more dragon, and it soaks no longer. */
    public void hatch(Place egg) {
        var owner = soaking.remove(egg);
        if (owner != null) {
            hatched.merge(owner, 1, Integer::sum);
            onChange.run();
        }
    }

    /** The egg at {@code egg} is gone before it hatched, broken or otherwise, and no longer counts. */
    public void lose(Place egg) {
        if (soaking.remove(egg) != null) {
            onChange.run();
        }
    }

    /**
     * Forgets each of {@code owner}'s soaking eggs that {@code gone} says no longer stands, such as
     * one a world edit took away unseen, so it no longer doubles their next egg's price.
     */
    public void forgetGone(UUID owner, Predicate<Place> gone) {
        if (soaking.entrySet().removeIf(entry -> entry.getValue().equals(owner) && gone.test(entry.getKey()))) {
            onChange.run();
        }
    }

    /** @return how many dragons {@code owner} has hatched or is soaking, which doubles their next egg's price */
    public int dragons(UUID owner) {
        int soakingNow = (int) soaking.values().stream().filter(owner::equals).count();
        return hatched.getOrDefault(owner, 0) + soakingNow;
    }

    /** @return whether any player has hatched a dragon in the world; until one has, a dragon kill brings back the founder egg */
    public boolean anyHatched() {
        return !hatched.isEmpty();
    }

    /** The codec that saves it. */
    public static final Codec<Hatchery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("hatched").forGetter(hatchery -> hatchery.hatched),
            Soaking.CODEC.listOf().fieldOf("soaking").forGetter(hatchery -> hatchery.soaking.entrySet().stream()
                    .map(entry -> new Soaking(entry.getKey(), entry.getValue())).toList())
    ).apply(instance, Hatchery::read));

    private static Hatchery read(Map<UUID, Integer> hatched, List<Soaking> soaking) {
        var hatchery = new Hatchery();
        hatchery.hatched.putAll(hatched);
        soaking.forEach(egg -> hatchery.soaking.put(egg.egg(), egg.owner()));
        return hatchery;
    }
}
