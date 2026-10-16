// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dragon.EggKind;
import io.github._5thlayer.voidworks.dragon.SoakingEggBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * A Matter Dragon Egg's amber, over the Drained Dragon Egg's own texture through its model's tint
 * index: placed, and soaking, where the soaking egg's shell tints only for a Matter egg, and the
 * flecks a soaking one gives off. Its item model tints it the same in the hand and in a chest, by a
 * constant tint of {@link #AMBER}.
 */
final class EggTint {

    /** The amber, as {@code items/matter_dragon_egg.json} writes it too. */
    static final int AMBER = 0xFFFFB02E;

    private EggTint() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) -> {
            event.register(List.of(BlockTintSources.constant(AMBER)), VoidworksBlocks.MATTER_DRAGON_EGG.get());
            event.register(List.of(new Soaking()), VoidworksBlocks.SOAKING_EGG.get());
        });
        SoakingEggBlock.onFleck(EggTint::fleck);
    }

    /** A soaking Matter Dragon Egg's fleck of itself, tinted as the egg is. */
    private static void fleck(Level level, ParticleOptions fleck, double x, double y, double z, double dx, double dy, double dz) {
        if (Minecraft.getInstance().particleEngine.createParticle(fleck, x, y, z, dx, dy, dz) instanceof SingleQuadParticle particle) {
            particle.setColor(ARGB.redFloat(AMBER), ARGB.greenFloat(AMBER), ARGB.blueFloat(AMBER));
        }
    }

    /** Amber for a soaking Matter Dragon Egg; untinted for the other eggs. */
    private static final class Soaking implements BlockTintSource {

        @Override
        public int color(BlockState state) {
            return state.getValue(SoakingEggBlock.EGG) == EggKind.MATTER ? AMBER : -1;
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return Set.of(SoakingEggBlock.EGG);
        }
    }
}
