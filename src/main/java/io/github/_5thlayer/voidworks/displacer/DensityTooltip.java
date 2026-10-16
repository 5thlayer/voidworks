// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import io.github._5thlayer.voidworks.VoidworksConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The line under the name of an item a Density tag names: its Density and, once the server's config
 * is known, the motes one yields fed to a Void Displacer. An item of Density 0 shows nothing.
 */
public final class DensityTooltip {

    private DensityTooltip() {
    }

    /** Puts {@code event}'s item's Density line right under its name, when it has a Density. */
    public static void onItemTooltip(ItemTooltipEvent event) {
        int density = Density.of(ItemResource.of(event.getItemStack()));
        if (density > 0) {
            event.getToolTip().add(1, line(density));
        }
    }

    /** @return the line an item of {@code density}, above 0, shows */
    private static Component line(int density) {
        // Known on a client only once a server has sent its config.
        if (!VoidworksConfig.loaded()) {
            return Component.translatable("voidworks.density.tooltip", density).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("voidworks.density.tooltip.motes", density, VoidworksConfig.motes(density))
                .withStyle(ChatFormatting.GRAY);
    }
}
