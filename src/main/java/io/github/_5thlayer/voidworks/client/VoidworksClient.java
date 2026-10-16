// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.butterfly.MoteCrawl;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.butterfly.VoidworksButterflies;
import io.github._5thlayer.voidworks.dragon.CallPayload;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.mutation.BreakdownsPayload;
import io.github._5thlayer.voidworks.mutation.KnownBreakdowns;
import io.github._5thlayer.voidworks.pressure.KnownPressure;
import io.github._5thlayer.voidworks.pressure.PressurePayload;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Module's client entry point: it draws the butterflies a {@link MoteFlight} or a {@link MoteCrawl} shows and the
 * result floating over a Void Crucible ({@link VoidCrucibleRenderer}), a Void Well's fill in its shaft
 * ({@link VoidWellRenderer}, {@link ShaftButterflies}) and the Void Dragon
 * ({@link VoidDragonRenderer}), and keeps the Void Pressure
 * entry the server sends ({@link KnownPressure}).
 */
@Mod(value = Voidworks.MOD_ID, dist = Dist.CLIENT)
public final class VoidworksClient {

    /** The butterfly's frames, once the particle sprites have loaded. */
    private static SpriteSet sprites;

    public VoidworksClient(IEventBus modBus) {
        modBus.addListener(VoidworksClient::registerParticles);
        modBus.addListener(VoidworksClient::registerPayloadHandlers);
        modBus.addListener(VoidworksClient::registerRenderers);
        // Over the world but under the hotbar: a dragon's rider sees only the void through its dive.
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "dive"), DiveOverlay::render));
        // Just over the action bar's word that a called dragon is coming.
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE,
                Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "call"), CallOverlay::render));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> DiveSound.tickClient());
        KnownPressure.setViewer(() -> Minecraft.getInstance().player);
        VoidWellBlockEntity.onWatcherTick(well -> {
            if (sprites != null) {
                ShaftButterflies.tick(well, sprites);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            KnownPressure.forget();
            KnownBreakdowns.forget();
            CallOverlay.forget();
        });
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(VoidworksButterflies.MOTE_BUTTERFLY.get(), spriteSet -> {
            sprites = spriteSet;
            // /particle voidworks:mote_butterfly shows one lowest-grade mote flying along its delta.
            return (options, level, x, y, z, dx, dy, dz, random) -> new ButterflyParticle(level, new Vec3(x, y, z),
                    new Vec3(x + dx, y + dy, z + dz), MoteFlight.NO_ENTITY, VoidEnergy.MIN_GRADE, 1, spriteSet);
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VoidworksBlocks.VOID_CRUCIBLE_ENTITY.get(), context -> new VoidCrucibleRenderer());
        event.registerBlockEntityRenderer(VoidworksBlocks.VOID_WELL_ENTITY.get(), context -> new VoidWellRenderer());
        event.registerEntityRenderer(VoidworksEntities.VOID_DRAGON.get(), VoidDragonRenderer::new);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(MoteFlight.TYPE, (flight, context) -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.level != null && sprites != null) {
                ButterflyParticle.show(minecraft.level, flight, sprites, minecraft.particleEngine::add);
            }
        });
        event.register(MoteCrawl.TYPE, (crawl, context) -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.level != null && sprites != null) {
                ButterflyParticle.crawl(minecraft.level, crawl, sprites, minecraft.particleEngine::add);
            }
        });
        event.register(PressurePayload.TYPE, (payload, context) -> KnownPressure.receive(payload));
        event.register(BreakdownsPayload.TYPE, (payload, context) -> KnownBreakdowns.receive(payload));
        event.register(CallPayload.TYPE, (payload, context) -> CallOverlay.receive(payload));
    }
}
