// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.item;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Library's data components, registered from the mod's entry point. */
public final class VoidworksDataComponents {

    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Voidworks.MOD_ID);

    /**
     * A mote's grade, a small integer on the Void Pressure scale, never below
     * {@link VoidEnergy#MIN_GRADE}. Read it with {@link MoteItem#gradeOf}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GRADE =
            COMPONENTS.registerComponentType("grade", builder -> builder
                    .persistent(ExtraCodecs.intRange(VoidEnergy.MIN_GRADE, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private VoidworksDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
