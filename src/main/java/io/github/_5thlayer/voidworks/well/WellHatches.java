// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.dragon.Place;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Every Well hatch on the server: where it stands, the Well it joined, whose it is, whether it is a
 * provider or a requester and what it asks for, and what it holds, so a Matter-line dragon loads and
 * unloads it with no chunk loaded (ADR 0005), as {@link VoidWells} keeps the Wells. A hatch puts itself here when it is placed, and takes itself out,
 * dropping what it holds, when it is broken. Its block's item faces are its contents here, not a copy.
 * {@link SavedWellHatches} saves it with the world.
 */
public final class WellHatches {

    /**
     * What is heard as a hatch, placed or in hand, switches either way: a shulker box opening. Its
     * closing starts with a long silence, timed to the lid's animation.
     */
    public static final SoundEvent SWITCH_SOUND = SoundEvents.SHULKER_BOX_OPEN;

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
     * Puts a hatch at {@code place}, holding nothing and asking for nothing, in place of any that was there.
     *
     * @param well  the core of the Well it joined; empty for one that joined none
     * @param owner whose it is, its Well's owner; empty for anyone's
     * @param mode  whether it is a provider or a requester, as the item that placed it was
     * @param slots how many slots it holds
     * @return the hatch put there
     */
    public Hatch put(Place place, Optional<Place> well, Optional<NameAndId> owner, Mode mode, int slots) {
        var hatch = new Hatch(place, well, owner, mode, Optional.empty(), NonNullList.withSize(slots, ItemStack.EMPTY), this);
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

    /** @return the hatches that joined the Well whose core is at {@code well}, in the order they were placed */
    public List<Hatch> on(Place well) {
        return hatches.values().stream().filter(hatch -> hatch.well.equals(Optional.of(well))).toList();
    }

    private static WellHatches read(List<Entry> entries) {
        var hatches = new WellHatches();
        entries.forEach(entry -> hatches.hatches.put(entry.place(), entry.hatch(hatches)));
        return hatches;
    }

    /** What a hatch does with what it holds: a provider offers it to its owner's requesters, a requester asks for one item. */
    public enum Mode implements StringRepresentable {
        PROVIDER, REQUESTER;

        static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        /** @return the other mode, the one switching to it gives */
        public Mode other() {
            return this == PROVIDER ? REQUESTER : PROVIDER;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What a requester asks for: one item's kind, its components ignored, so many stacks of it.
     *
     * @param item   the kind of item it asks for
     * @param stacks how many stacks of it, each full by the item's stack size
     */
    public record Request(Item item, int stacks) {

        static final Codec<Request> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Request::item),
                Codec.INT.fieldOf("stacks").forGetter(Request::stacks)
        ).apply(instance, Request::new));

        /** @return the name of the item it asks for, with none of a stack's own components */
        public Component itemName() {
            return new ItemStack(item).getItemName();
        }
    }

    /**
     * One Well hatch: where it stands, the Well it joined and whose it is, which never change; whether
     * it is a provider or a requester, and a requester's request; and what it holds. Its Well broken, it
     * is a plain container until a new core there makes the Well whole again.
     */
    public static final class Hatch {

        private final Place place;
        private final Optional<Place> well;
        private final Optional<NameAndId> owner;
        private Mode mode;
        private Optional<Request> request;
        private final Contents contents;
        private final WellHatches hatches;

        private Hatch(Place place, Optional<Place> well, Optional<NameAndId> owner, Mode mode, Optional<Request> request, NonNullList<ItemStack> stacks,
                WellHatches hatches) {
            this.place = place;
            this.well = well;
            this.owner = owner;
            this.mode = mode;
            this.request = request;
            this.contents = new Contents(stacks, hatches);
            this.hatches = hatches;
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

        /** @return whether it is a provider or a requester; it is placed as the item that placed it was */
        public Mode mode() {
            return mode;
        }

        /** @return what it asks for; empty for a provider, which never asks, and a requester asking for nothing */
        public Optional<Request> request() {
            return request;
        }

        /**
         * Switches it between provider and requester, keeping what it holds: a requester made a provider
         * loses its request and offers what it holds, and a provider made a requester keeps it for its item
         * faces to drain, asking for nothing yet.
         *
         * @return what it is now
         */
        public Mode switchMode() {
            mode = mode.other();
            request = Optional.empty();
            hatches.onChange.run();
            return mode;
        }

        /**
         * A requester asks for a stack more of {@code item}'s kind, up to as many stacks as it has slots;
         * another item replaces its request, asking for one stack of it.
         *
         * @return what it asks for now
         * @throws IllegalStateException if it is a provider, which never asks
         */
        public Request ask(Item item) {
            if (mode != Mode.REQUESTER) {
                throw new IllegalStateException("a provider asks for nothing");
            }
            int stacks = request.filter(asked -> asked.item() == item).map(asked -> Math.min(asked.stacks() + 1, contents.size())).orElse(1);
            var asked = new Request(item, stacks);
            request = Optional.of(asked);
            hatches.onChange.run();
            return asked;
        }

        /** Asks for nothing. */
        public void clearRequest() {
            request = Optional.empty();
            hatches.onChange.run();
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

    /**
     * A hatch as it is saved: its slots in order, the empty ones too, so it keeps as many as it was placed
     * with. One saved before requesters is a provider.
     */
    private record Entry(Place place, Optional<Place> well, Optional<NameAndId> owner, Mode mode, Optional<Request> request, List<ItemStack> stacks) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Place.CODEC.fieldOf("place").forGetter(Entry::place),
                Place.CODEC.optionalFieldOf("well").forGetter(Entry::well),
                NameAndId.CODEC.optionalFieldOf("owner").forGetter(Entry::owner),
                Mode.CODEC.optionalFieldOf("mode", Mode.PROVIDER).forGetter(Entry::mode),
                Request.CODEC.optionalFieldOf("request").forGetter(Entry::request),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stacks").forGetter(Entry::stacks)
        ).apply(instance, Entry::new));

        static Entry of(Hatch hatch) {
            return new Entry(hatch.place, hatch.well, hatch.owner, hatch.mode, hatch.request, hatch.contents.copyToList());
        }

        Hatch hatch(WellHatches hatches) {
            var slots = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < stacks.size(); slot++) {
                slots.set(slot, stacks.get(slot));
            }
            return new Hatch(place, well, owner, mode, request, slots, hatches);
        }
    }
}
