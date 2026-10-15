// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gallery;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Offers the Gallery pack, {@code gallery_pack/} in the jar, in every world's Data Packs screen,
 * off until a player turns it on (ADR 0008). It holds functions only: {@code voidworks:gallery/build}
 * lays out every scene, and {@code voidworks:gallery/<scene>} one. Written as the template for
 * libworks' runtime helper, which takes its place once a runtime release carries it.
 */
@EventBusSubscriber(modid = Voidworks.MOD_ID)
public final class GalleryPack {

    /** The pack's id in the pack repository, as {@code /datapack enable} names it. */
    public static final String ID = "mod/" + Voidworks.MOD_ID + ":gallery_pack";

    /**
     * Built in, as the jar's own, but never added automatically, to a new world or an old one. Not
     * vanilla's {@code FEATURE}, also never added automatically, which world creation lists as an
     * experiment.
     */
    private static final PackSource OPTIONAL_BUILT_IN = PackSource.create(PackSource.BUILT_IN::decorate, false);

    private GalleryPack() {
    }

    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "gallery_pack"), PackType.SERVER_DATA,
                Component.translatable("voidworks.gallery.pack"), OPTIONAL_BUILT_IN, false, Pack.Position.TOP);
    }
}
