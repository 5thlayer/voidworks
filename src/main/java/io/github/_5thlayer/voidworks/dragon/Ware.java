// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Items alike, as a Matter-line dragon's routing sees them: one item with the same components, so
 * many of them, however many stacks that makes. A requester asks for an item's kind, its components
 * ignored; a dragon carries the items themselves, components and all. It names no item type, so it is
 * tested with no game running; {@code StackWares} turns an item stack into one and back.
 *
 * @param item the item's id, which a request asks for
 * @param components the item's components where they differ from its own, as saved; empty for none
 * @param stackSize how many of it make a stack, which a load counts in
 * @param count how many there are
 */
public record Ware(Identifier item, Optional<CompoundTag> components, int stackSize, long count) {

    public static final Codec<Ware> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(Ware::item),
            CompoundTag.CODEC.optionalFieldOf("components").forGetter(Ware::components),
            Codec.INT.fieldOf("stack_size").forGetter(Ware::stackSize),
            Codec.LONG.fieldOf("count").forGetter(Ware::count)
    ).apply(instance, Ware::new));

    public Ware {
        if (stackSize < 1 || count < 0) {
            throw new IllegalArgumentException("a ware stacks by at least one, and is never fewer than none: " + stackSize + ", " + count);
        }
        components = components.map(CompoundTag::copy);
    }

    /** @return the item's components where they differ from its own, a copy, so the ware stays as it is */
    @Override
    public Optional<CompoundTag> components() {
        return components.map(CompoundTag::copy);
    }

    /** @return {@code count} of {@code item}, with none of its own components, {@code stackSize} to a stack */
    public static Ware of(Identifier item, int stackSize, long count) {
        return new Ware(item, Optional.empty(), stackSize, count);
    }

    /** @return how many stacks it fills, a part stack counting as one */
    public long stacks() {
        return stacks(count, stackSize);
    }

    /** @return {@code count} items, {@code stackSize} to a stack, in stacks, a part stack counting as one */
    static long stacks(long count, int stackSize) {
        return Math.ceilDiv(count, stackSize);
    }

    /** @return the same item, {@code count} of it */
    public Ware withCount(long count) {
        return new Ware(item, components, stackSize, count);
    }

    /** @return whether {@code other} is the same item with the same components, so the two stack together */
    public boolean alike(Ware other) {
        return item.equals(other.item) && components.equals(other.components);
    }
}
