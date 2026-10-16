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
 * how many dragons each owner has hatched and how many of those are gone, died or taken out. It sets an
 * egg's price when the egg starts soaking, by the {@link EggPrice} for every dragon its owner has
 * living, Dragonling or Void Dragon, or is soaking, so two eggs
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
    private final Map<UUID, Integer> gone = new HashMap<>();
    private final Map<Place, UUID> soaking = new HashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, so the saved data that holds it is saved. */
    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Starts the egg at {@code egg} soaking for {@code owner}, who placed it, at {@code eggPrice}.
     *
     * @return its price, fixed now, in void energy
     */
    public long startSoaking(Place egg, UUID owner, EggPrice eggPrice) {
        long price = eggPrice.of(dragons(owner));
        soaking.put(egg, owner);
        onChange.run();
        return price;
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
     * A dragon of {@code owner}'s is gone, died of old age or taken out by a command: they have one
     * fewer living, though never fewer than none.
     */
    public void gone(UUID owner) {
        if (living(owner) > 0) {
            gone.merge(owner, 1, Integer::sum);
            onChange.run();
        }
    }

    /** @return how many of the dragons {@code owner} has hatched still live */
    private int living(UUID owner) {
        return hatched.getOrDefault(owner, 0) - gone.getOrDefault(owner, 0);
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

    /** @return how many dragons {@code owner} has living or is soaking, which doubles their next egg's price */
    public int dragons(UUID owner) {
        int soakingNow = (int) soaking.values().stream().filter(owner::equals).count();
        return living(owner) + soakingNow;
    }

    /** @return whether any player has hatched a dragon in the world; until one has, a dragon kill brings back the founder egg */
    public boolean anyHatched() {
        return !hatched.isEmpty();
    }

    /** The codec that saves it. */
    public static final Codec<Hatchery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("hatched").forGetter(hatchery -> hatchery.hatched),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("gone", Map.of()).forGetter(hatchery -> hatchery.gone),
            Soaking.CODEC.listOf().fieldOf("soaking").forGetter(hatchery -> hatchery.soaking.entrySet().stream()
                    .map(entry -> new Soaking(entry.getKey(), entry.getValue())).toList())
    ).apply(instance, Hatchery::read));

    private static Hatchery read(Map<UUID, Integer> hatched, Map<UUID, Integer> gone, List<Soaking> soaking) {
        var hatchery = new Hatchery();
        hatchery.hatched.putAll(hatched);
        hatchery.gone.putAll(gone);
        soaking.forEach(egg -> hatchery.soaking.put(egg.egg(), egg.owner()));
        return hatchery;
    }
}
