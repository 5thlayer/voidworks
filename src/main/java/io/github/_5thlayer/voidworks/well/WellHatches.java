// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.dragon.Place;
import net.minecraft.core.NonNullList;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Every Well hatch on the server: where it stands, the Well it joined, whose it is, and what it
 * holds, so a Matter-line dragon loads and unloads it with no chunk loaded (ADR 0005), as
 * {@link VoidWells} keeps the Wells. A hatch puts itself here when it is placed, and takes itself out,
 * dropping what it holds, when it is broken. Its block's item faces are its contents here, not a copy.
 * {@link SavedWellHatches} saves it with the world.
 */
public final class WellHatches {

    public static final Codec<WellHatches> CODEC = Entry.CODEC.listOf().xmap(WellHatches::read,
            hatches -> hatches.hatches.values().stream().map(Entry::of).toList());

    private final Map<Place, Hatch> hatches = new LinkedHashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, what a hatch holds too, so the saved data that holds it is saved. */
    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Puts a hatch at {@code place}, holding nothing, in place of any that was there.
     *
     * @param well  the core of the Well it joined; empty for one that joined none
     * @param owner whose it is, its Well's owner; empty for anyone's
     * @param slots how many slots it holds
     * @return the hatch put there
     */
    public Hatch put(Place place, Optional<Place> well, Optional<NameAndId> owner, int slots) {
        var hatch = new Hatch(place, well, owner, NonNullList.withSize(slots, ItemStack.EMPTY), this);
        hatches.put(place, hatch);
        onChange.run();
        return hatch;
    }

    /** @return the hatch at {@code place}, taken out, so its block drops what it held; empty if there is none */
    public Optional<Hatch> remove(Place place) {
        var hatch = Optional.ofNullable(hatches.remove(place));
        hatch.ifPresent(removed -> onChange.run());
        return hatch;
    }

    /** @return the hatch at {@code place}, if there is one */
    public Optional<Hatch> at(Place place) {
        return Optional.ofNullable(hatches.get(place));
    }

    private static WellHatches read(List<Entry> entries) {
        var hatches = new WellHatches();
        entries.forEach(entry -> hatches.hatches.put(entry.place(), entry.hatch(hatches)));
        return hatches;
    }

    /**
     * One Well hatch: where it stands, the Well it joined and whose it is, which never change, and what it
     * holds. Its Well broken, it is a plain container until a new core there makes the Well whole again.
     */
    public static final class Hatch {

        private final Place place;
        private final Optional<Place> well;
        private final Optional<NameAndId> owner;
        private final Contents contents;

        private Hatch(Place place, Optional<Place> well, Optional<NameAndId> owner, NonNullList<ItemStack> stacks, WellHatches hatches) {
            this.place = place;
            this.well = well;
            this.owner = owner;
            this.contents = new Contents(stacks, hatches);
        }

        /** @return where it stands */
        public Place place() {
            return place;
        }

        /** @return the core of the Well it was placed against; empty for one that joined none */
        public Optional<Place> well() {
            return well;
        }

        /** @return whose it is, its Well's owner when it was placed; empty for anyone's */
        public Optional<NameAndId> owner() {
            return owner;
        }

        /** @return what it holds, which its block's every face fills and empties, and a dragon too */
        public ItemStacksResourceHandler contents() {
            return contents;
        }
    }

    /** A hatch's slots, which mark the hatches changed whenever what they hold does. */
    private static final class Contents extends ItemStacksResourceHandler {

        private final WellHatches hatches;

        Contents(NonNullList<ItemStack> stacks, WellHatches hatches) {
            super(stacks);
            this.hatches = hatches;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            hatches.onChange.run();
        }
    }

    /** A hatch as it is saved: its slots in order, the empty ones too, so it keeps as many as it was placed with. */
    private record Entry(Place place, Optional<Place> well, Optional<NameAndId> owner, List<ItemStack> stacks) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Place.CODEC.fieldOf("place").forGetter(Entry::place),
                Place.CODEC.optionalFieldOf("well").forGetter(Entry::well),
                NameAndId.CODEC.optionalFieldOf("owner").forGetter(Entry::owner),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stacks").forGetter(Entry::stacks)
        ).apply(instance, Entry::new));

        static Entry of(Hatch hatch) {
            return new Entry(hatch.place, hatch.well, hatch.owner, hatch.contents.copyToList());
        }

        Hatch hatch(WellHatches hatches) {
            var slots = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < stacks.size(); slot++) {
                slots.set(slot, stacks.get(slot));
            }
            return new Hatch(place, well, owner, slots, hatches);
        }
    }
}
