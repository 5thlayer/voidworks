// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * A Family, as a datapack writes it in {@code data/<namespace>/voidworks/family/<name>.json}: its
 * Rungs in order, bottom first. A datapack adds a Family with a file of its own and replaces one by
 * overriding its file.
 *
 * <pre>{@code
 * {"rungs": [
 *   {"items": "#voidworks:rung/metal_ingots/copper"},
 *   {"items": "#voidworks:rung/metal_ingots/iron", "result": "minecraft:iron_ingot", "cost": 100, "chance": 0.8}
 * ]}
 * }</pre>
 */
public record Family(List<Family.Rung> rungs) {

    /**
     * One Rung as written: the items it accepts, by tag, and, on every Rung but the bottom, the
     * upgrade into it from the Rung below.
     */
    public record Rung(TagKey<Item> items, Optional<Upgrade<Item>> upgrade) {

        private record Written(TagKey<Item> items, Optional<Item> result, Optional<Integer> count, Optional<Long> cost,
                Optional<Double> chance) {

            private static final Codec<Written> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    TagKey.hashedCodec(Registries.ITEM).fieldOf("items").forGetter(Written::items),
                    BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("result").forGetter(Written::result),
                    Codec.INT.optionalFieldOf("count").forGetter(Written::count),
                    Codec.LONG.optionalFieldOf("cost").forGetter(Written::cost),
                    Codec.DOUBLE.optionalFieldOf("chance").forGetter(Written::chance)
            ).apply(instance, Written::new));

            private boolean namesAnUpgrade() {
                return result.isPresent() || count.isPresent() || cost.isPresent() || chance.isPresent();
            }

            private DataResult<Rung> read() {
                if (!namesAnUpgrade()) {
                    return DataResult.success(new Rung(items, Optional.empty()));
                }
                if (result.isEmpty() || cost.isEmpty() || chance.isEmpty()) {
                    return DataResult.error(() -> "a Rung names all of its result, cost and chance, or none of them on the bottom Rung: "
                            + items.location());
                }
                try {
                    var upgrade = new Upgrade<>(result.get(), count.orElse(1), cost.get(), chance.get());
                    return DataResult.success(new Rung(items, Optional.of(upgrade)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "the Rung " + items.location() + ": " + e.getMessage());
                }
            }

            private static Written of(Rung rung) {
                var upgrade = rung.upgrade();
                return new Written(rung.items(), upgrade.map(Upgrade::result), upgrade.map(Upgrade::count).filter(count -> count != 1),
                        upgrade.map(Upgrade::cost), upgrade.map(Upgrade::chance));
            }
        }

        public static final Codec<Rung> CODEC =
                Written.CODEC.flatXmap(Written::read, rung -> DataResult.success(Written.of(rung)));
    }

    public static final Codec<Family> CODEC = RecordCodecBuilder.<Family>create(instance -> instance.group(
            Rung.CODEC.listOf().fieldOf("rungs").forGetter(Family::rungs)
    ).apply(instance, Family::new)).validate(Family::validate);

    private static DataResult<Family> validate(Family family) {
        var rungs = family.rungs();
        if (rungs.isEmpty()) {
            return DataResult.error(() -> "a Family has at least one Rung");
        }
        if (rungs.getFirst().upgrade().isPresent()) {
            return DataResult.error(() -> "a Family's bottom Rung names only its items, since nothing upgrades into it: "
                    + rungs.getFirst().items().location());
        }
        for (var rung : rungs.subList(1, rungs.size())) {
            if (rung.upgrade().isEmpty()) {
                return DataResult.error(() -> "a Rung above the bottom names its result, cost and chance: " + rung.items().location());
            }
        }
        return DataResult.success(family);
    }
}
