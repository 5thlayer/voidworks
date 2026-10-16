// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

/**
 * A grown Void Dragon, as server state rather than an entity (ADR 0005): who owns it, its health,
 * where it rests, the load it carries, the trip it has claimed and the motes it has carried toward
 * its next egg and over its life. It rests where it grew up until it has stopped at a Well, and then at the Well it
 * last stopped at. The dragon a player sees is only its presence ({@link DragonPresences});
 * {@link Routing} moves it.
 *
 * @param id the dragon's own, which its presence names
 * @param health its health, of {@link #FULL_HEALTH}
 * @param rest where it rests, or last stopped while it is on a trip
 * @param load the motes it carries, grade to count
 * @param towardEgg the motes it has carried toward its next egg, counted as it unloads them as an adult,
 *        less those of every egg it has laid
 * @param cargo the motes it has unloaded over its life, by which it ages ({@link LifeStages})
 */
public record VoidDragonRecord(UUID id, UUID owner, int health, Place rest, SortedMap<Integer, Long> load, Optional<Trip> trip, long towardEgg, long cargo) {

    /** A Void Dragon's health while it is whole. Provisional, like every number. */
    public static final int FULL_HEALTH = 100;

    /** A whole dragon's speed: speeds count in hundredths of it. */
    public static final int WHOLE_SPEED = 100;

    private static final Codec<Map<Integer, Long>> LOAD_CODEC = Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.LONG);

    public static final Codec<VoidDragonRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(VoidDragonRecord::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(VoidDragonRecord::owner),
            Codec.INT.fieldOf("health").forGetter(VoidDragonRecord::health),
            Place.CODEC.fieldOf("rest").forGetter(VoidDragonRecord::rest),
            LOAD_CODEC.optionalFieldOf("load", Map.of()).forGetter(VoidDragonRecord::load),
            Trip.CODEC.optionalFieldOf("trip").forGetter(VoidDragonRecord::trip),
            // A count saved before life stages, as "since_egg", is dropped: such a dragon starts young.
            Codec.LONG.optionalFieldOf("toward_egg", 0L).forGetter(VoidDragonRecord::towardEgg),
            Codec.LONG.optionalFieldOf("cargo", 0L).forGetter(VoidDragonRecord::cargo)
    ).apply(instance, (id, owner, health, rest, load, trip, towardEgg, cargo) -> new VoidDragonRecord(id, owner, health, rest, new TreeMap<>(load), trip,
            towardEgg, cargo)));

    public VoidDragonRecord {
        load = Collections.unmodifiableSortedMap(new TreeMap<>(load));
    }

    /** @return a new dragon of {@code owner}'s, at full health, carrying nothing and resting at {@code rest} */
    static VoidDragonRecord grown(UUID owner, Place rest) {
        return new VoidDragonRecord(UUID.randomUUID(), owner, FULL_HEALTH, rest, new TreeMap<>(), Optional.empty(), 0, 0);
    }

    /** @return how many motes it carries, of every grade together */
    public long carried() {
        return load.values().stream().mapToLong(Long::longValue).sum();
    }

    /** @return this dragon {@code points} of health weaker, though never below 1: it cannot die */
    public VoidDragonRecord weakened(int points) {
        return new VoidDragonRecord(id, owner, Math.max(1, health - points), rest, load, trip, towardEgg, cargo);
    }

    /** @return this dragon {@code points} of health stronger, though never above {@link #FULL_HEALTH} */
    VoidDragonRecord healed(int points) {
        return new VoidDragonRecord(id, owner, Math.min(FULL_HEALTH, health + points), rest, load, trip, towardEgg, cargo);
    }

    VoidDragonRecord resting(Place rest) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip, towardEgg, cargo);
    }

    VoidDragonRecord carrying(SortedMap<Integer, Long> load) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip, towardEgg, cargo);
    }

    VoidDragonRecord on(Optional<Trip> trip) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip, towardEgg, cargo);
    }

    /**
     * @return this dragon having unloaded {@code motes} more, which it has carried over its life, of
     *         which {@code towardEggs}, those it unloaded as an adult, count toward its next egg
     */
    VoidDragonRecord unloaded(long motes, long towardEggs) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip, towardEgg + towardEggs, cargo + motes);
    }

    /** @return this dragon as if it had unloaded {@code cargo} in its life, owing no egg: for setting its age by command */
    public VoidDragonRecord aged(long cargo) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip, 0, cargo);
    }

    /** @return this dragon having laid eggs for {@code motes} of its count, what it carried beyond them counting on */
    VoidDragonRecord laid(long motes) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip, towardEgg - motes, cargo);
    }
}
