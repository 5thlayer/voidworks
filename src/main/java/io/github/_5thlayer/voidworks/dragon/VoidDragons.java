// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;

/**
 * Every grown Void Dragon on the server, by its id: records that live whether or not any chunk is
 * loaded (ADR 0005). Plain logic with no game running; {@link SavedVoidDragons} saves it with the
 * world.
 */
public final class VoidDragons {

    public static final Codec<VoidDragons> CODEC = VoidDragonRecord.CODEC.listOf().xmap(VoidDragons::read, dragons -> List.copyOf(dragons.all()));

    private final Map<UUID, VoidDragonRecord> dragons = new LinkedHashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, so the saved data that holds it is saved. */
    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * A Dragonling of {@code owner}'s has grown up at {@code rest}.
     *
     * @return the new Void Dragon, at full health and resting there
     */
    public VoidDragonRecord add(UUID owner, Place rest) {
        var dragon = VoidDragonRecord.grown(owner, rest);
        dragons.put(dragon.id(), dragon);
        onChange.run();
        return dragon;
    }

    /** Replaces the dragon with {@code dragon}'s id by {@code dragon}, as it is now; a dragon it does not have stays gone. */
    public void update(VoidDragonRecord dragon) {
        if (dragons.replace(dragon.id(), dragon) != null) {
            onChange.run();
        }
    }

    /**
     * Takes out every dragon that rests at a place {@code at} accepts, or is on a trip to or from one,
     * whoever owns it.
     *
     * @return the dragons taken out, in the order they grew up
     */
    public List<VoidDragonRecord> removeAt(Predicate<Place> at) {
        var removed = dragons.values().stream().filter(dragon -> at.test(dragon.rest())
                || dragon.trip().map(trip -> at.test(trip.destination()) || trip.source().filter(at).isPresent()).orElse(false)).toList();
        if (!removed.isEmpty()) {
            removed.forEach(dragon -> dragons.remove(dragon.id()));
            onChange.run();
        }
        return removed;
    }

    /** @return the dragon with {@code id}, if there is one */
    public Optional<VoidDragonRecord> get(UUID id) {
        return Optional.ofNullable(dragons.get(id));
    }

    /** @return every dragon, in the order they grew up */
    public Collection<VoidDragonRecord> all() {
        return Collections.unmodifiableCollection(dragons.values());
    }

    private static VoidDragons read(List<VoidDragonRecord> records) {
        var dragons = new VoidDragons();
        records.forEach(dragon -> dragons.dragons.put(dragon.id(), dragon));
        return dragons;
    }
}
