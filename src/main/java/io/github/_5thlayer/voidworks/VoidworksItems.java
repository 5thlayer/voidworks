// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.siphon.SiphonCharging;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.SiphonMotes;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Void Siphon and the data components that hold its motes and its toggle. */
public final class VoidworksItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Voidworks.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Voidworks.MOD_ID);

    /** {@code voidworks:siphon_motes}: the motes a Void Siphon holds, by grade; none when absent. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SiphonMotes>> SIPHON_MOTES =
            COMPONENTS.registerComponentType("siphon_motes",
                    builder -> builder.persistent(SiphonMotes.CODEC).networkSynchronized(SiphonMotes.STREAM_CODEC));

    /** {@code voidworks:siphon_mode}: whether a Void Siphon charges or empties on Voidstone; it charges when absent. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SiphonMode>> SIPHON_MODE =
            COMPONENTS.registerComponentType("siphon_mode",
                    builder -> builder.persistent(SiphonMode.CODEC).networkSynchronized(SiphonMode.STREAM_CODEC));

    public static final DeferredItem<VoidSiphonItem> VOID_SIPHON = ITEMS.registerItem("void_siphon", VoidSiphonItem::new,
            properties -> properties.stacksTo(1));

    private VoidworksItems() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener(SiphonCharging::onPlayerTick);
    }
}
