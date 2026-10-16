// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.OptionalInt;

import io.github._5thlayer.voidworks.VoidworksConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The line under the name of an item a Density tag names: its Density and, once the server's config
 * is known, the motes one yields fed to a Void Displacer. An item of Density 0 shows nothing.
 */
public final class DensityTooltip {

    private DensityTooltip() {
    }

    /** Adds the line to every item tooltip built: a client's, and the game tests'. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(DensityTooltip::onItemTooltip);
    }

    private static void onItemTooltip(ItemTooltipEvent event) {
        int density = Density.of(ItemResource.of(event.getItemStack()));
        if (density > 0) {
            // Known on a client only once a server has sent its config.
            var motes = VoidworksConfig.loaded()
                    ? OptionalInt.of(VoidworksConfig.motes(density))
                    : OptionalInt.empty();
            event.getToolTip().add(1, line(density, motes));
        }
    }

    /**
     * @param motes what one yields fed to a Displacer, or empty when that is not known
     * @return the line an item of {@code density}, above 0, shows
     */
    static Component line(int density, OptionalInt motes) {
        MutableComponent line;
        if (motes.isEmpty()) {
            line = Component.translatable("voidworks.density.tooltip", density);
        } else {
            String key = motes.getAsInt() == 1 ? "voidworks.density.tooltip.mote" : "voidworks.density.tooltip.motes";
            line = Component.translatable(key, density, motes.getAsInt());
        }
        return line.withStyle(ChatFormatting.GRAY);
    }
}
