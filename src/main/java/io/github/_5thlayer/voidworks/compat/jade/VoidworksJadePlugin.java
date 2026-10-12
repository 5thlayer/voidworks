// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.jade;

import java.util.List;

import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.displacer.DisplacerBlock;
import io.github._5thlayer.voidworks.displacer.DisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.DisplacerReport;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.energy.TouchingMotes;
import io.github._5thlayer.voidworks.mutation.ChamberReport;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlock;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlockEntity;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * {@link Dormancy#status}, with what the mote stores touching a Dormant one are worth; a Void
 * Displacer's {@link DisplacerReport}; and a Mutation Chamber's {@link ChamberReport}. All but the
 * first are libworks-runtime's machine view; Voidstone, with no block entity, has its own provider.
 * A Displacer that is Dormant and fed shows both its payment and its fed block, as a Dormant Chamber
 * shows its payment and its input.
 *
 * <p>Found by Jade's annotation scan and named nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public final class VoidworksJadePlugin implements IWailaPlugin {

    private static final MachineReport<BlockEntity, List<Component>> DORMANT = new MachineReport<>(id("dormant"), BlockEntity.class,
            VoidworksJadePlugin::dormancy, ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()));

    private static final MachineReport<DisplacerBlockEntity, DisplacerReport> DISPLACER =
            new MachineReport<>(id("displacer"), DisplacerBlockEntity.class, VoidworksJadePlugin::displacer, DisplacerReport.STREAM_CODEC);

    private static final MachineReport<MutationChamberBlockEntity, ChamberReport> CHAMBER =
            new MachineReport<>(id("chamber"), MutationChamberBlockEntity.class, VoidworksJadePlugin::chamber, ChamberReport.STREAM_CODEC);

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, path);
    }

    /** Its payment, and while Dormant, what the best mote store touching it is worth there, so a player sees whether it will wake. */
    private static @Nullable List<Component> dormancy(BlockEntity machine) {
        if (!(machine instanceof Dormancy.Holder holder) || !(machine.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        var dormancy = holder.dormancy();
        return dormancy.awake()
                ? List.of(dormancy.status())
                : List.of(dormancy.status(), TouchingMotes.describe(TouchingMotes.bestWorth(level, machine.getBlockPos())));
    }

    private static @Nullable DisplacerReport displacer(DisplacerBlockEntity displacer) {
        return displacer.getLevel() instanceof ServerLevel level ? DisplacerReport.of(displacer, level) : null;
    }

    private static @Nullable ChamberReport chamber(MutationChamberBlockEntity chamber) {
        return chamber.getLevel() instanceof ServerLevel level ? ChamberReport.of(chamber, level) : null;
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(VoidstoneReport.INSTANCE, VoidstoneBlock.class);
        registration.registerBlockDataProvider(DORMANT, BlockEntity.class);
        registration.registerBlockDataProvider(DISPLACER, DisplacerBlockEntity.class);
        registration.registerBlockDataProvider(CHAMBER, MutationChamberBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(VoidstoneReport.Client.INSTANCE, VoidstoneBlock.class);
        registration.registerBlockComponent(new MachineReport.Client<>(DORMANT, (lines, tooltip) -> lines.forEach(tooltip::add)), Block.class);
        registration.registerBlockComponent(new MachineReport.Client<>(DISPLACER, (report, tooltip) -> report.lines().forEach(tooltip::add)),
                DisplacerBlock.class);
        registration.registerBlockComponent(new MachineReport.Client<>(CHAMBER, (report, tooltip) -> report.lines().forEach(tooltip::add)),
                MutationChamberBlock.class);
    }
}
