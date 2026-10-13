// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.butterfly;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The butterflies harvested motes are seen as: the particle type whose sprites they are drawn with,
 * the {@link MoteFlight} payload that shows them flying and the {@link MoteCrawl} one that shows them
 * walking through a network. The client draws them
 * ({@code client.VoidworksClient}); {@code /voidworks butterflies} shows them by hand
 * ({@code command.VoidworksCommands}).
 */
public final class VoidworksButterflies {

    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Voidworks.MOD_ID);

    /** The butterfly's sprite frames, {@code assets/voidworks/particles/mote_butterfly.json}. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE_BUTTERFLY =
            PARTICLE_TYPES.register("mote_butterfly", () -> new SimpleParticleType(false));

    private VoidworksButterflies() {
    }

    public static void register(IEventBus modBus) {
        PARTICLE_TYPES.register(modBus);
        modBus.addListener(VoidworksButterflies::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The client registers the handler; a dedicated server only sends. Optional, so a client
        // without Voidworks can still join and sees nothing.
        var registrar = event.registrar("1").optional();
        registrar.playToClient(MoteFlight.TYPE, MoteFlight.STREAM_CODEC);
        registrar.playToClient(MoteCrawl.TYPE, MoteCrawl.STREAM_CODEC);
    }
}
