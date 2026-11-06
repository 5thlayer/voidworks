// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.jade;

import java.util.Optional;

import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.github._5thlayer.voidworks.displacer.DisplacerBlock;
import io.github._5thlayer.voidworks.dragon.DragonReport;
import io.github._5thlayer.voidworks.dragon.DragonlingReport;
import io.github._5thlayer.voidworks.dragon.StackWares;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlock;
import io.github._5thlayer.voidworks.mutation.UnmakingChamberBlock;
import io.github._5thlayer.voidworks.mutation.VoidCrucibleBlock;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.WellHatchBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.ui.JadeUI;

/**
 * {@link VoidworksJadePlugin}'s client half: how each of its reports is drawn in Jade's tooltip.
 * Client-only, and kept out of the plugin class, which a dedicated server loads too.
 */
final class VoidworksJadeClient {

    private VoidworksJadeClient() {
    }

    static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(VoidstoneReport.Client.INSTANCE, VoidstoneBlock.class);
        registration.registerBlockComponent(VoidstoneReport.Client.INSTANCE, VoidWellBlock.class);
        registration.registerBlockComponent(new MachineReport.Client<>(VoidworksJadePlugin.DORMANT, (lines, tooltip) -> lines.forEach(tooltip::add)),
                Block.class);
        registration.registerBlockComponent(
                new MachineReport.Client<>(VoidworksJadePlugin.DISPLACER, (report, tooltip) -> report.lines().forEach(tooltip::add)), DisplacerBlock.class);
        registration.registerBlockComponent(
                new MachineReport.Client<>(VoidworksJadePlugin.CHAMBER, (report, tooltip) -> report.lines().forEach(tooltip::add)), MutationChamberBlock.class);
        registration.registerBlockComponent(
                new MachineReport.Client<>(VoidworksJadePlugin.UNMAKING, (report, tooltip) -> report.lines().forEach(tooltip::add)), UnmakingChamberBlock.class);
        registration.registerBlockComponent(
                new MachineReport.Client<>(VoidworksJadePlugin.CRUCIBLE, (report, tooltip) -> report.lines().forEach(tooltip::add)), VoidCrucibleBlock.class);
        registration.registerBlockComponent(
                new MachineReport.Client<>(VoidworksJadePlugin.WELL, (report, tooltip) -> report.lines().forEach(tooltip::add)), VoidWellBlock.class);
        registration.registerBlockComponent(new MachineReport.Client<>(VoidworksJadePlugin.HATCH, (report, tooltip) -> {
            report.lines().forEach(line -> tooltip.add(JadeUI.text(line).alignSelfCenter()));
            report.request().ifPresent(asked -> tooltip.append(JadeUI.item(new ItemStack(asked.item()), 1, Integer.toString(asked.stacks()))));
        }), WellHatchBlock.class);
        registration.registerEntityComponent(new CreatureReport.Client<>(VoidworksJadePlugin.DRAGON, VoidworksJadeClient::showDragon,
                report -> Optional.of(new CreatureReport.Health(report.health(), VoidDragonRecord.FULL_HEALTH))), VoidDragon.class);
        registration.registerEntityComponent(new CreatureReport.Client<>(VoidworksJadePlugin.DRAGONLING, DragonlingReport::lines), VoidDragon.class);
    }

    /**
     * A dragon's report, a Matter-line dragon's items between its head and tail: one line per item, its
     * icon and name and how many, a filled container followed by the icons of what it holds.
     */
    private static void showDragon(DragonReport report, ITooltip tooltip, EntityAccessor accessor) {
        report.head().forEach(tooltip::add);
        var registries = accessor.getLevel().registryAccess();
        for (var ware : report.items()) {
            var stack = StackWares.stack(ware, registries);
            tooltip.add(JadeUI.item(stack.copyWithCount(1)));
            tooltip.append(JadeUI.text(Component.translatable("voidworks.jade.dragon.item", stack.getHoverName(), ware.count())).alignSelfCenter());
            var held = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
            held.forEach(inside -> tooltip.append(JadeUI.item(inside, 0.75F)));
        }
        report.tail().forEach(tooltip::add);
    }
}
