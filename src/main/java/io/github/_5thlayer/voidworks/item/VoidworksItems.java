// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.item;

import io.github._5thlayer.voidworks.Voidworks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Library's items, registered from the mod's entry point. A later item adds a line here and
 * its class beside {@link MoteItem}.
 */
public final class VoidworksItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Voidworks.MOD_ID);

    /** The mote. Make a stack of one with {@link MoteItem#stack}: the bare item has the lowest grade. */
    public static final DeferredItem<MoteItem> MOTE = ITEMS.registerItem("mote", MoteItem::new,
            properties -> properties.component(VoidworksDataComponents.GRADE.get(), MoteItem.LOWEST_GRADE));

    private VoidworksItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
