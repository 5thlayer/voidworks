// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Void Dragon, as a creature, and its attributes. */
public final class VoidworksEntities {

    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Voidworks.MOD_ID);

    /** About a fifth of the Ender Dragon's size, as a Dragonling. */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidDragon>> VOID_DRAGON = ENTITIES.registerEntityType("void_dragon",
            VoidDragon::new, MobCategory.CREATURE, builder -> builder.sized(3.2F, 1.6F).fireImmune().clientTrackingRange(10));

    private VoidworksEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> event.put(VOID_DRAGON.get(), VoidDragon.createAttributes().build()));
    }
}
