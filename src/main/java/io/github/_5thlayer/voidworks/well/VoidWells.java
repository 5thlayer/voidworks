// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.dragon.Place;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Every Void Well core on the server that has an owner: where it stands, whose it is, its fill
 * target and its name, so a Void Dragon finds its owner's Wells in every dimension with no chunk
 * loaded (ADR 0005), and its rider picks them by name. A core puts itself here when it is claimed,
 * when its target or name changes and when it first ticks, and takes itself out when it is broken.
 * What it holds is its network's, not this. Plain logic; {@link SavedVoidWells} saves it with the
 * world.
 */
public final class VoidWells {

    public static final Codec<VoidWells> CODEC = Entry.CODEC.listOf().xmap(VoidWells::read, wells -> List.copyOf(wells.wells.values()));

    private final Map<Place, Entry> wells = new LinkedHashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, so the saved data that holds it is saved. */
    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Puts {@code well} here, in place of what was at its place. */
    public void put(Entry well) {
        if (!well.equals(wells.put(well.place(), well))) {
            onChange.run();
        }
    }

    /** Takes the Well at {@code place} out, if there is one. */
    public void remove(Place place) {
        if (wells.remove(place) != null) {
            onChange.run();
        }
    }

    /** @return the Well at {@code place}, if there is one with an owner */
    public Optional<Entry> at(Place place) {
        return Optional.ofNullable(wells.get(place));
    }

    /** @return {@code owner}'s Wells, whole or not, in the order they were first put here */
    public List<Entry> owned(UUID owner) {
        return wells.values().stream().filter(well -> well.owner().equals(owner)).toList();
    }

    private static VoidWells read(List<Entry> entries) {
        var wells = new VoidWells();
        entries.forEach(well -> wells.wells.put(well.place(), well));
        return wells;
    }

    /** @return what a Well goes by: its {@code name}, or unnamed, its dimension and position */
    public static Component label(Optional<Component> name, String dimension, BlockPos pos) {
        return name.orElseGet(() -> Component.translatable("voidworks.well.unnamed", dimension, pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * One Void Well core: where it stands, whose it is, how full it asks to be kept, and what a name tag
     * named it.
     */
    public record Entry(Place place, UUID owner, FillTarget target, Optional<Component> name) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Place.CODEC.fieldOf("place").forGetter(Entry::place),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                FillTarget.CODEC.fieldOf("target").forGetter(Entry::target),
                ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Entry::name)
        ).apply(instance, Entry::new));

        /** An unnamed Well. */
        public Entry(Place place, UUID owner, FillTarget target) {
            this(place, owner, target, Optional.empty());
        }

        /** @return what it goes by: its name, or unnamed, its dimension and position */
        public Component label() {
            return VoidWells.label(name, place.dimension().toString(), place.pos());
        }
    }
}
