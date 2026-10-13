// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The breakdowns the Unmaking Chamber reads: the server's crafting-table recipes in reverse
 * ({@link Breakdowns}), shaped and shapeless alike, and the {@code voidworks:breakdown} data map on
 * items, a pack's entry that fills in or bars an item's breakdown, in
 * {@code data/<namespace>/data_maps/item/breakdown.json}, such as
 * {@code {"values": {"minecraft:stick": {"count": 4, "ingredients": [{"item": "minecraft:oak_planks", "count": 2}]}}}}.
 * An entry with no ingredients bars the item. A crafting recipe of another kind, such as fireworks or
 * banner copies, has no fixed ingredients, so the item it makes has no breakdown from it, and two
 * recipes make an item it shares with a shaped or shapeless one. The recipes are read again
 * whenever the server's recipe manager is replaced, as on a reload.
 */
public final class UnmakingRecipes {

    public static final Identifier DATA_MAP_ID = Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "breakdown");

    private record Stack(Item item, int count) {

        static final Codec<Stack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Stack::item),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Stack::count)).apply(instance, Stack::new));
    }

    /** An entry: how many of the item a breakdown takes, 1 unless set, and its ingredients, none to bar it. */
    public static final Codec<Breakdown<Item>> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Breakdown::count),
            Stack.CODEC.listOf().fieldOf("ingredients").forGetter(UnmakingRecipes::stacks)).apply(instance, UnmakingRecipes::entry));

    public static final DataMapType<Item, Breakdown<Item>> DATA_MAP = DataMapType.builder(DATA_MAP_ID, Registries.ITEM, ENTRY_CODEC).build();

    private static @Nullable RecipeManager readFrom;
    private static Breakdowns<Item> recipes = Breakdowns.empty();

    private UnmakingRecipes() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(UnmakingRecipes::registerDataMap);
    }

    private static void registerDataMap(RegisterDataMapTypesEvent event) {
        event.register(DATA_MAP);
    }

    /** @return the breakdown {@code stack}'s item has, whatever it is worn or enchanted with; empty for nothing or an item with none */
    public static Optional<Breakdown<Item>> of(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : of(stack.typeHolder());
    }

    /** @return the breakdown an item of {@code resource} has, as {@link #of(ItemStack)} */
    static Optional<Breakdown<Item>> of(ItemResource resource) {
        return resource.isEmpty() ? Optional.empty() : of(resource.getItem().builtInRegistryHolder());
    }

    /** @return whether an item of {@code resource} has a breakdown, so the Unmaking Chamber's input takes it */
    static boolean hasBreakdown(ItemResource resource) {
        return of(resource).isPresent();
    }

    private static Optional<Breakdown<Item>> of(Holder<Item> item) {
        return recipes().of(item.value(), Optional.ofNullable(item.getData(DATA_MAP)));
    }

    /** The current server's recipes, read again once it replaces its recipe manager; none with no server. */
    private static synchronized Breakdowns<Item> recipes() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Breakdowns.empty();
        }
        var manager = server.getRecipeManager();
        if (manager != readFrom) {
            recipes = read(manager, server.registryAccess());
            readFrom = manager;
        }
        return recipes;
    }

    private static Breakdowns<Item> read(RecipeManager manager, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var read = new ArrayList<Breakdowns.Recipe<Item>>();
        for (var holder : manager.recipeMap().byType(RecipeType.CRAFTING)) {
            switch (holder.value()) {
                case ShapedRecipe shaped -> read.add(recipe(shaped, shaped.getIngredients().stream().flatMap(Optional::stream).toList()));
                case ShapelessRecipe shapeless -> read.add(recipe(shapeless, shapeless.placementInfo().ingredients()));
                default -> unfixed(holder.value(), ops).ifPresent(read::add);
            }
        }
        return Breakdowns.of(read);
    }

    /**
     * A crafting recipe of another kind, such as fireworks, banner copies or map extending, builds its
     * result from what the grid holds, so it has no fixed ingredients; it still makes its item, which
     * its {@code result} field names, read as the recipe is written, since no method gives it.
     *
     * @return the item it makes, with no breakdown; empty when it names none
     */
    private static Optional<Breakdowns.Recipe<Item>> unfixed(CraftingRecipe recipe, DynamicOps<JsonElement> ops) {
        return Recipe.CODEC.encodeStart(ops, recipe).result()
                .filter(JsonElement::isJsonObject)
                .map(json -> json.getAsJsonObject().get("result"))
                .flatMap(result -> ItemStackTemplate.CODEC.parse(ops, result).result())
                .map(result -> new Breakdowns.Recipe<>(result.item().value(), result.count(), Optional.empty()));
    }

    // Ingredient.items is deprecated for display, but is the only order an ingredient has.
    @SuppressWarnings("deprecation")
    private static Breakdowns.Recipe<Item> recipe(NormalCraftingRecipe recipe, List<Ingredient> ingredients) {
        // A shaped or shapeless recipe's result is fixed, whatever the grid holds.
        var result = recipe.assemble(CraftingInput.EMPTY);
        if (!result.isComponentsPatchEmpty()) {
            return new Breakdowns.Recipe<>(result.getItem(), result.getCount(), Optional.empty());
        }
        var read = new ArrayList<Breakdowns.Ingredient<Item>>();
        for (var ingredient : ingredients) {
            var first = ingredient.items().findFirst();
            if (first.isEmpty()) {
                // An ingredient that accepts nothing, as an empty tag: nothing could make the item.
                return new Breakdowns.Recipe<>(result.getItem(), result.getCount(), Optional.empty());
            }
            var item = first.get().value();
            read.add(new Breakdowns.Ingredient<>(item, item.getCraftingRemainder(new ItemStack(first.get())) != null));
        }
        return new Breakdowns.Recipe<>(result.getItem(), result.getCount(), Optional.of(read));
    }

    private static Breakdown<Item> entry(int count, List<Stack> ingredients) {
        var returns = new LinkedHashMap<Item, Integer>();
        ingredients.forEach(stack -> returns.merge(stack.item(), stack.count(), Integer::sum));
        return new Breakdown<>(count, returns);
    }

    private static List<Stack> stacks(Breakdown<Item> breakdown) {
        return breakdown.returns().entrySet().stream().map(entry -> new Stack(entry.getKey(), entry.getValue())).toList();
    }
}
