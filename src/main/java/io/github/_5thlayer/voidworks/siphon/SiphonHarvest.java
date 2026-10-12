// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import java.util.Locale;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.displacer.Density;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * One harvest by hand: the Void Siphon in the main hand, a block of Density above 0 in the off hand,
 * aimed like placing a block. It behaves by the dimension's harvest kind, yields one Density step
 * below a Displacer ({@link SiphonMotes#handYield}) at one grade above the Void Pressure where it
 * harvests, credits the Siphon, consumes the fed block, and leaves Voidstone where it harvested:
 *
 * <ul>
 *   <li>{@code end}: the fed block goes into the air aimed at and becomes Voidstone;</li>
 *   <li>{@code overworld}: it swaps with the natural block aimed at, which comes back to the player;</li>
 *   <li>{@code nether}: it displaces the lava source aimed at, which fills an empty bucket or another
 *       mod's fluid item from the player's inventory, and is refused with none.</li>
 * </ul>
 *
 * <p>A refused harvest changes nothing.
 */
public final class SiphonHarvest {

    /** What a harvest came to: harvested, or why it was refused, which the player is told. */
    public enum Outcome {
        HARVESTED,
        /** The dimension's harvest kind is {@code none}. */
        NO_HARVEST_HERE,
        /** The off hand holds no block of Density above 0. */
        NOTHING_TO_FEED,
        /** What is aimed at is not what this kind harvests: air for the End, natural terrain, a lava source. */
        NOTHING_TO_HARVEST,
        /** No empty bucket or fluid item in the inventory takes the lava. */
        NO_CONTAINER,
        /** The Siphon has no room for the harvest's motes. */
        FULL,
        /** The Void Pressure there is so high that no mote has the grade above it. */
        NO_GRADE,
        /** The player may not change the world there. */
        NOT_ALLOWED;

        /** @return what the player is told, on the action bar */
        public Component message() {
            return Component.translatable("voidworks.siphon.harvest." + name().toLowerCase(Locale.ROOT));
        }
    }

    private SiphonHarvest() {
    }

    /**
     * Harvests once where {@code hit} aims, as a Siphon in {@code player}'s main hand does in a
     * dimension of harvest kind {@code kind}. The item asks with the dimension's own kind.
     *
     * @param hit where the player aims, with fluid sources hit for {@code nether}
     */
    public static Outcome harvest(ServerLevel level, Player player, BlockHitResult hit, HarvestKind kind) {
        if (kind == HarvestKind.NONE) {
            return Outcome.NO_HARVEST_HERE;
        }
        var fed = player.getOffhandItem();
        int density = Density.of(ItemResource.of(fed));
        if (density == 0) {
            return Outcome.NOTHING_TO_FEED;
        }
        var target = kind == HarvestKind.END ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
        var harvested = level.getBlockState(target);
        if (!harvestable(level, target, harvested, kind)) {
            return Outcome.NOTHING_TO_HARVEST;
        }
        if (!level.mayInteract(player, target) || !player.mayUseItemAt(target, hit.getDirection(), player.getMainHandItem())) {
            return Outcome.NOT_ALLOWED;
        }
        int grade = VoidPressure.at(level, target) + 1;
        if (!VoidEnergy.isGrade(grade)) {
            return Outcome.NO_GRADE;
        }
        int motes = SiphonMotes.handYield(density, VoidworksConfig::motes);
        var siphon = VoidSiphonItem.moteStore(ItemAccess.forPlayerSlot(player, player.getInventory().getSelectedSlot()));
        try (var transaction = Transaction.openRoot()) {
            if (siphon.insert(MoteResource.of(grade), motes, transaction) != motes) {
                return Outcome.FULL;
            }
            if (kind == HarvestKind.NETHER && !fillAContainer(player, transaction)) {
                return Outcome.NO_CONTAINER;
            }
            if (!player.hasInfiniteMaterials()
                    && PlayerInventoryWrapper.of(player).extract(Inventory.SLOT_OFFHAND, ItemResource.of(fed), 1, transaction) != 1) {
                return Outcome.NOTHING_TO_FEED;
            }
            transaction.commit();
        }
        level.setBlock(target, VoidworksBlocks.VOIDSTONE.get().in(level), Block.UPDATE_ALL);
        if (kind == HarvestKind.OVERWORLD) {
            var natural = new ItemStack(harvested.getBlock().asItem());
            if (!natural.isEmpty()) {
                player.getInventory().placeItemBackInInventory(natural);
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, harvested),
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
        }
        MoteFlight.toEntity(level, target.getCenter(), player, grade, motes);
        level.playSound(null, target, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        return Outcome.HARVESTED;
    }

    private static boolean harvestable(ServerLevel level, BlockPos target, BlockState state, HarvestKind kind) {
        return switch (kind) {
            case END -> state.isAir();
            case OVERWORLD -> state.is(VoidworksBlocks.OVERWORLD_TERRAIN);
            case NETHER -> state.getBlock() instanceof LiquidBlock && level.getFluidState(target).is(FluidTags.LAVA)
                    && level.getFluidState(target).isSource();
            case NONE -> false;
        };
    }

    /** Fills the first empty bucket or fluid item in the inventory that takes a bucket of lava. */
    private static boolean fillAContainer(Player player, TransactionContext transaction) {
        var lava = FluidResource.of(Fluids.LAVA);
        for (int slot = 0; slot < PlayerInventoryWrapper.of(player).size(); slot++) {
            var container = ItemAccess.forPlayerSlot(player, slot).oneByOne().getCapability(Capabilities.Fluid.ITEM);
            if (container == null) {
                continue;
            }
            try (var attempt = Transaction.open(transaction)) {
                if (container.insert(lava, FluidType.BUCKET_VOLUME, attempt) == FluidType.BUCKET_VOLUME) {
                    attempt.commit();
                    return true;
                }
            }
        }
        return false;
    }
}
