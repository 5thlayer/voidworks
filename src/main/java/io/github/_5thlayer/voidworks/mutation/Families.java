// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;

/**
 * The Families an upgrade climbs: the {@code voidworks:family} datapack registry, read on the server
 * whenever its data loads. It is the seam the Mutation Chamber and the Void Crucible ask for an
 * item's next Rung. Families are not synced to clients.
 */
public final class Families {

    /** The registry of Families, from {@code data/<namespace>/voidworks/family/<name>.json}. */
    public static final ResourceKey<Registry<Family>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "family"));

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Ladders<Item> ladders = Ladders.empty();

    private Families() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Families::registerRegistry);
        NeoForge.EVENT_BUS.addListener(Families::reload);
    }

    /**
     * @return the upgrade {@code stack}'s item climbs to: the next Rung's result, count, cost and
     *         chance, or empty for an item only on top Rungs, in no Family, or where its Rungs disagree
     */
    public static Optional<Upgrade<Item>> next(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : ladders.next(stack.getItem());
    }

    /** @return the upgrade an item of {@code resource} climbs to, as {@link #next(ItemStack)}, or empty for nothing */
    static Optional<Upgrade<Item>> next(ItemResource resource) {
        return resource.isEmpty() ? Optional.empty() : ladders.next(resource.getItem());
    }

    /** @return whether an item of {@code resource} climbs a Family, so an upgrading block's input takes it */
    static boolean hasUpgrade(ItemResource resource) {
        return next(resource).isPresent();
    }

    /** @return each item whose Rungs disagree on its next step, with the Families that name it; the loader warns of each */
    public static Map<Item, SortedSet<String>> disagreements() {
        return ladders.disagreements();
    }

    private static void registerRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY, Family.CODEC);
    }

    /** Re-reads the Families once the server's tags are bound: a Rung's items are a tag. */
    private static void reload(TagsUpdatedEvent.ServerDataLoad event) {
        ladders = read(event.getRegistries());
        ladders.disagreements().forEach((item, families) -> LOGGER.warn(
                "{} has no upgrade: the Families {} disagree on its next Rung", item, families));
    }

    private static Ladders<Item> read(RegistryAccess registries) {
        var families = registries.lookup(REGISTRY);
        if (families.isEmpty()) {
            return Ladders.empty();
        }
        var items = registries.lookupOrThrow(Registries.ITEM);
        var read = new LinkedHashMap<String, List<Ladders.Rung<Item>>>();
        families.get().listElements().forEach(family -> read.put(family.key().identifier().toString(),
                family.value().rungs().stream().map(rung -> {
                    var accepted = new LinkedHashSet<Item>();
                    items.get(rung.items()).ifPresent(tag -> tag.stream().map(Holder::value).forEach(accepted::add));
                    return new Ladders.Rung<>(accepted, rung.upgrade());
                }).toList()));
        return Ladders.of(read);
    }
}
