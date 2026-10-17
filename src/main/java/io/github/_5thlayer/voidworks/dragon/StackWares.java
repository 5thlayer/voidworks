// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Turns item stacks into the {@link Ware}s a Matter-line dragon's routing sees, and back, components and all. */
public final class StackWares {

    private StackWares() {
    }

    /** @return {@code stack} as a ware, as many as it holds */
    public static Ware of(ItemStack stack, HolderLookup.Provider registries) {
        return of(ItemResource.of(stack), stack.getCount(), registries);
    }

    /** @return {@code count} of {@code resource} as a ware */
    public static Ware of(ItemResource resource, long count, HolderLookup.Provider registries) {
        var patch = resource.getComponentsPatch();
        var components = patch.isEmpty() ? Optional.<CompoundTag>empty()
                : DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), patch).result()
                        .filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast);
        return new Ware(BuiltInRegistries.ITEM.getKey(resource.getItem()), components, resource.getMaxStackSize(), count, held(resource.toStack(1)));
    }

    /**
     * @return what one of {@code stack} holds, as a filled shulker box or bundle does: a stack size to how
     *         many items of that stack size, what those hold in turn counted in, which a Toll pays for
     */
    static SortedMap<Integer, Long> held(ItemStack stack) {
        var held = new TreeMap<Integer, Long>();
        Stream.concat(stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream(),
                stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopyStream()).forEach(inside -> {
                    held.merge(inside.getMaxStackSize(), (long) inside.getCount(), Long::sum);
                    held(inside).forEach((stackSize, count) -> held.merge(stackSize, count * inside.getCount(), Long::sum));
                });
        return held;
    }

    /** @return the item {@code ware} is, with its components, as a resource with no count */
    public static ItemResource resource(Ware ware, HolderLookup.Provider registries) {
        var patch = ware.components()
                .flatMap(components -> DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), components).result())
                .orElse(DataComponentPatch.EMPTY);
        return ItemResource.of(BuiltInRegistries.ITEM.getValue(ware.item()), patch);
    }

    /** @return {@code ware} as one stack of it, as many of it as a stack holds at most */
    public static ItemStack stack(Ware ware, HolderLookup.Provider registries) {
        return resource(ware, registries).toStack((int) Math.min(ware.count(), ware.stackSize()));
    }
}
