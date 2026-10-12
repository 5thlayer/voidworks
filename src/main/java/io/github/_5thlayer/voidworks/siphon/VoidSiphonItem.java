// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import java.util.function.Consumer;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.transfer.access.ItemAccess;

/**
 * The Void Siphon: ghast tear, crying obsidian and a blaze rod. Used in the main hand it harvests
 * once ({@link SiphonHarvest}), as the dimension's harvest kind says; sneak-used it flips its toggle
 * between charging from the Voidstone its holder stands on and emptying into it
 * ({@link SiphonCharging}); set to empty and used on a Dormant machine, it pays what its motes can
 * of the machine's cost. It holds motes up to {@link VoidworksConfig#siphonCapacity()} and keeps them wherever it
 * goes, dropped, stored or through a portal.
 */
public final class VoidSiphonItem extends Item {

    public VoidSiphonItem(Properties properties) {
        super(properties);
    }

    /**
     * @return the motes of the Siphon at {@code siphon}, as a mote store that can change, which a
     *         harvest, a charge and a payment reach them through. Kept to this package, and handed
     *         only a player's slot, so no block charges or empties a Siphon (ADR 0002).
     */
    static SiphonMoteHandler moteStore(ItemAccess siphon) {
        return new SiphonMoteHandler(siphon, VoidworksConfig.siphonCapacity());
    }

    /** @return the motes {@code stack} holds, by grade */
    public static SiphonMotes heldMotes(ItemStack stack) {
        return stack.getOrDefault(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY);
    }

    /** @return whether {@code stack} charges or empties on Voidstone */
    public static SiphonMode mode(ItemStack stack) {
        return stack.getOrDefault(VoidworksItems.SIPHON_MODE.get(), SiphonMode.CHARGE);
    }

    /** Set to empty and used on a Dormant machine, it pays the machine; anything else is a harvest, aimed by {@link #use}. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (player == null || player.isSecondaryUseActive() || !state.hasProperty(Dormancy.DORMANT) || !state.getValue(Dormancy.DORMANT)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof Dormancy.Holder machine)) {
            return InteractionResult.PASS;
        }
        var slot = context.getHand() == InteractionHand.MAIN_HAND
                ? ItemAccess.forPlayerSlot(player, player.getInventory().getSelectedSlot())
                : ItemAccess.forPlayerSlot(player, Inventory.SLOT_OFFHAND);
        if (mode(context.getItemInHand()) != SiphonMode.EMPTY) {
            player.sendOverlayMessage(Component.translatable("voidworks.siphon.pay.charging"));
            return InteractionResult.FAIL;
        }
        long paid = machine.dormancy().payFrom(server, moteStore(slot), MoteFlight.aimAt(player));
        player.sendOverlayMessage(paid > 0 ? machine.dormancy().status() : Component.translatable("voidworks.siphon.pay.nothing"));
        return paid > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Sneak-used, it flips its toggle; in the main hand, it harvests where its holder aims. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (level instanceof ServerLevel) {
                var mode = mode(stack).flipped();
                stack.set(VoidworksItems.SIPHON_MODE.get(), mode);
                player.sendOverlayMessage(Component.translatable("voidworks.siphon.mode." + mode.getSerializedName()));
            }
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        // Answered on the client too, so the off hand's block is never placed instead.
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        var kind = VoidPressure.harvestKind(server);
        var hit = getPlayerPOVHitResult(level, player, kind == HarvestKind.NETHER ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
        var outcome = hit.getType() == HitResult.Type.BLOCK
                ? SiphonHarvest.harvest(server, player, hit, kind)
                : kind == HarvestKind.NONE ? SiphonHarvest.Outcome.NO_HARVEST_HERE : SiphonHarvest.Outcome.NOTHING_TO_HARVEST;
        if (outcome != SiphonHarvest.Outcome.HARVESTED) {
            player.sendOverlayMessage(outcome.message());
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    // -- how it looks ---------------------------------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return VoidworksConfig.loaded() && heldMotes(stack).total() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * heldMotes(stack).total() / VoidworksConfig.siphonCapacity()), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        var motes = heldMotes(stack).byGrade();
        return motes.isEmpty() ? 0xFFFFFF : GradeColor.of(motes.lastKey()) & 0xFFFFFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        var motes = heldMotes(stack);
        if (VoidworksConfig.loaded()) {
            builder.accept(Component.translatable("voidworks.siphon.tooltip.held", motes.total(), VoidworksConfig.siphonCapacity())
                    .withStyle(ChatFormatting.GRAY));
        }
        motes.byGrade().forEach((grade, count) -> builder.accept(
                Component.translatable("voidworks.siphon.tooltip.grade", grade, count).withColor(GradeColor.of(grade) & 0xFFFFFF)));
        builder.accept(Component.translatable("voidworks.siphon.tooltip." + mode(stack).getSerializedName()).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
