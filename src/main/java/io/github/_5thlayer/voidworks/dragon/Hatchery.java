// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/**
 * Every owner's dragons, server-wide: the eggs soaking, by where they stand and who placed them, and
 * how many dragons each owner has hatched. It sets an egg's price when the egg starts soaking: the
 * first egg's price, doubled for every dragon its owner has hatched or is soaking, so two eggs
 * placed together never both get the cheap price, and an egg given away starts its new owner cheap.
 * Plain logic with no game running; {@link SavedHatchery} saves it with the world.
 */
public final class Hatchery {

    /**
     * Where an egg stands: its dimension and position.
     *
     * @param dimension the dimension's id, such as {@code minecraft:overworld}
     */
    public record Egg(Identifier dimension, BlockPos pos) {

        static final Codec<Egg> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(Egg::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Egg::pos)
        ).apply(instance, Egg::new));
    }

    private record Soaking(Egg egg, UUID owner) {

        static final Codec<Soaking> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Egg.CODEC.fieldOf("egg").forGetter(Soaking::egg),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Soaking::owner)
        ).apply(instance, Soaking::new));
    }

    private final LongSupplier firstPrice;
    private final Map<UUID, Integer> hatched = new HashMap<>();
    private final Map<Egg, UUID> soaking = new HashMap<>();
    private Runnable onChange = () -> {
    };

    /** @param firstPrice what an owner's first egg costs, in void energy */
    public Hatchery(LongSupplier firstPrice) {
        this.firstPrice = firstPrice;
    }

    /** Runs {@code onChange} after each change, so the saved data that holds it is saved. */
    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * An egg's price: {@code first} doubled for each of the owner's {@code dragons}, hatched or
     * soaking, and {@link Long#MAX_VALUE} once that would pass it.
     */
    public static long price(long first, int dragons) {
        if (dragons >= Long.SIZE - 1 || first > Long.MAX_VALUE >> dragons) {
            return Long.MAX_VALUE;
        }
        return first << dragons;
    }

    /**
     * Starts the egg at {@code egg} soaking for {@code owner}, who placed it.
     *
     * @return its price, fixed now, in void energy
     */
    public long startSoaking(Egg egg, UUID owner) {
        long price = price(firstPrice.getAsLong(), dragons(owner));
        soaking.put(egg, owner);
        onChange.run();
        return price;
    }

    /** The egg at {@code egg} has hatched: its owner has one more dragon, and it soaks no longer. */
    public void hatch(Egg egg) {
        var owner = soaking.remove(egg);
        if (owner != null) {
            hatched.merge(owner, 1, Integer::sum);
            onChange.run();
        }
    }

    /** The egg at {@code egg} is gone before it hatched, broken or otherwise, and no longer counts. */
    public void lose(Egg egg) {
        if (soaking.remove(egg) != null) {
            onChange.run();
        }
    }

    /**
     * Forgets each of {@code owner}'s soaking eggs that {@code gone} says no longer stands, such as
     * one a world edit took away unseen, so it no longer doubles their next egg's price.
     */
    public void forgetGone(UUID owner, Predicate<Egg> gone) {
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

    /** @return the codec that saves it, reading back with {@code firstPrice} */
    public static Codec<Hatchery> codec(LongSupplier firstPrice) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("hatched").forGetter(hatchery -> hatchery.hatched),
                Soaking.CODEC.listOf().fieldOf("soaking").forGetter(hatchery -> hatchery.soaking.entrySet().stream()
                        .map(entry -> new Soaking(entry.getKey(), entry.getValue())).toList())
        ).apply(instance, (hatched, soaking) -> read(firstPrice, hatched, soaking)));
    }

    private static Hatchery read(LongSupplier firstPrice, Map<UUID, Integer> hatched, List<Soaking> soaking) {
        var hatchery = new Hatchery(firstPrice);
        hatchery.hatched.putAll(hatched);
        soaking.forEach(egg -> hatchery.soaking.put(egg.egg(), egg.owner()));
        return hatchery;
    }
}
