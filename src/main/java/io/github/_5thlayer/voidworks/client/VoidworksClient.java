// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.butterfly.VoidworksButterflies;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * The Library's client entry point: it draws the butterflies a {@link MoteFlight} shows.
 */
@Mod(value = Voidworks.MOD_ID, dist = Dist.CLIENT)
public final class VoidworksClient {

    /** The butterfly's frames, once the particle sprites have loaded. */
    private static SpriteSet sprites;

    public VoidworksClient(IEventBus modBus) {
        modBus.addListener(VoidworksClient::registerParticles);
        modBus.addListener(VoidworksClient::registerPayloadHandlers);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(VoidworksButterflies.MOTE_BUTTERFLY.get(), spriteSet -> {
            sprites = spriteSet;
            // /particle voidworks:mote_butterfly shows one lowest-grade mote flying along its delta.
            return (options, level, x, y, z, dx, dy, dz, random) -> new ButterflyParticle(level, new Vec3(x, y, z),
                    new Vec3(x + dx, y + dy, z + dz), MoteFlight.NO_ENTITY, VoidEnergy.MIN_GRADE, 1, spriteSet);
        });
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(MoteFlight.TYPE, (flight, context) -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.level != null && sprites != null) {
                ButterflyParticle.show(minecraft.level, flight, sprites, minecraft.particleEngine::add);
            }
        });
    }
}
