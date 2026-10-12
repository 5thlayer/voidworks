// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.jade;

import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.displacer.DisplacerBlock;
import io.github._5thlayer.voidworks.displacer.DisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.DisplacerReport;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What a void block holds, in Jade, read on the server where networks and Dormancy live: a
 * Voidstone's network ({@link VoidstoneReport}); any Dormant machine's payment, a Consumer's too, by
 * {@link Dormancy#status}; and a Void Displacer's {@link DisplacerReport}. The last two are
 * libworks-runtime's machine view; Voidstone, with no block entity, has its own provider. A Displacer that is Dormant and fed shows both its payment and
 * its fed block.
 *
 * <p>Found by Jade's annotation scan and named nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public final class VoidworksJadePlugin implements IWailaPlugin {

    private static final MachineReport<BlockEntity, Component> DORMANT =
            new MachineReport<>(id("dormant"), BlockEntity.class, VoidworksJadePlugin::dormancy, ComponentSerialization.STREAM_CODEC);

    private static final MachineReport<DisplacerBlockEntity, DisplacerReport> DISPLACER =
            new MachineReport<>(id("displacer"), DisplacerBlockEntity.class, VoidworksJadePlugin::displacer, DisplacerReport.STREAM_CODEC);

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, path);
    }

    private static @Nullable Component dormancy(BlockEntity machine) {
        return machine instanceof Dormancy.Holder holder ? holder.dormancy().status() : null;
    }

    private static @Nullable DisplacerReport displacer(DisplacerBlockEntity displacer) {
        return displacer.getLevel() instanceof ServerLevel level ? DisplacerReport.of(displacer, level) : null;
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(VoidstoneReport.INSTANCE, VoidstoneBlock.class);
        registration.registerBlockDataProvider(DORMANT, BlockEntity.class);
        registration.registerBlockDataProvider(DISPLACER, DisplacerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(VoidstoneReport.Client.INSTANCE, VoidstoneBlock.class);
        registration.registerBlockComponent(new MachineReport.Client<>(DORMANT, (status, tooltip) -> tooltip.add(status)), Block.class);
        registration.registerBlockComponent(new MachineReport.Client<>(DISPLACER, (report, tooltip) -> report.lines().forEach(tooltip::add)),
                DisplacerBlock.class);
    }
}
