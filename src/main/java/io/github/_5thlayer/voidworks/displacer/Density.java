// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.List;
import java.util.stream.IntStream;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A fed block's Density, by tag: {@code voidworks:density/1} to {@code voidworks:density/4}, a
 * whitelist following the Family ladders. Every block no tag names, the renewable and abundant ones
 * above all, is Density 0, and nothing harvests it. How many motes each Density yields is
 * {@link VoidworksConfig#motes}.
 */
public final class Density {

    /** The Density tags, Density 1 first. */
    public static final List<TagKey<Item>> TAGS = IntStream.rangeClosed(1, VoidworksConfig.DENSITIES)
            .mapToObj(density -> TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "density/" + density)))
            .toList();

    private Density() {
    }

    /** @return the Density of {@code item}: the highest whose tag names it, or 0 when none does */
    public static int of(ItemResource item) {
        if (item.isEmpty()) {
            return 0;
        }
        for (int density = TAGS.size(); density >= 1; density--) {
            if (item.typeHolder().is(TAGS.get(density - 1))) {
                return density;
            }
        }
        return 0;
    }
}
