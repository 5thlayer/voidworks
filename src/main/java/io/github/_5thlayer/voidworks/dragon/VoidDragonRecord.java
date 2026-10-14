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
 * where it rests, the load it carries and the trip it has claimed. It rests where it grew up until it
 * has stopped at a Well, and then at the Well it last stopped at. The dragon a player sees is only its
 * presence ({@link DragonPresences}); {@link Routing} moves it.
 *
 * @param id the dragon's own, which its presence names
 * @param health its health, of {@link #FULL_HEALTH}
 * @param rest where it rests, or last stopped while it is on a trip
 * @param load the motes it carries, grade to count
 */
public record VoidDragonRecord(UUID id, UUID owner, int health, Place rest, SortedMap<Integer, Long> load, Optional<Trip> trip) {

    /** A Void Dragon's health while it is whole. Provisional, like every number. */
    public static final int FULL_HEALTH = 100;

    private static final Codec<Map<Integer, Long>> LOAD_CODEC = Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.LONG);

    public static final Codec<VoidDragonRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(VoidDragonRecord::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(VoidDragonRecord::owner),
            Codec.INT.fieldOf("health").forGetter(VoidDragonRecord::health),
            Place.CODEC.fieldOf("rest").forGetter(VoidDragonRecord::rest),
            LOAD_CODEC.optionalFieldOf("load", Map.of()).forGetter(VoidDragonRecord::load),
            Trip.CODEC.optionalFieldOf("trip").forGetter(VoidDragonRecord::trip)
    ).apply(instance, (id, owner, health, rest, load, trip) -> new VoidDragonRecord(id, owner, health, rest, new TreeMap<>(load), trip)));

    public VoidDragonRecord {
        load = Collections.unmodifiableSortedMap(new TreeMap<>(load));
    }

    /** @return a new dragon of {@code owner}'s, at full health, carrying nothing and resting at {@code rest} */
    static VoidDragonRecord grown(UUID owner, Place rest) {
        return new VoidDragonRecord(UUID.randomUUID(), owner, FULL_HEALTH, rest, new TreeMap<>(), Optional.empty());
    }

    /** @return how many motes it carries, of every grade together */
    public long carried() {
        return load.values().stream().mapToLong(Long::longValue).sum();
    }

    VoidDragonRecord resting(Place rest) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip);
    }

    VoidDragonRecord carrying(SortedMap<Integer, Long> load) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip);
    }

    VoidDragonRecord on(Optional<Trip> trip) {
        return new VoidDragonRecord(id, owner, health, rest, load, trip);
    }
}
