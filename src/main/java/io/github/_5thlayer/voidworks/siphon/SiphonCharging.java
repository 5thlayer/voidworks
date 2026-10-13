// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.network.VoidstoneMotes;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A player standing on Voidstone charges or empties every Void Siphon they carry, each as its toggle
 * says: up to its tier's {@link VoidworksConfig#siphonRate rate} of motes per Siphon every
 * {@link VoidworksConfig#siphonInterval()} ticks, the highest grade first, as many as the receiver
 * has room for, and without loss. It reaches the Voidstone stood on first, then the Voidstone and
 * Void Wells touching it, so a tier's rate is not held to what one Voidstone holds; conduction
 * brings the rest of the network to them. The motes fly between the Voidstone and the player.
 */
public final class SiphonCharging {

    private SiphonCharging() {
    }

    /** Charges or empties once every {@link VoidworksConfig#siphonInterval()} ticks of a player on a server. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (player.level() instanceof ServerLevel level && !player.isSpectator()
                && player.tickCount % VoidworksConfig.siphonInterval() == 0) {
            exchange(level, player);
        }
    }

    /**
     * Charges or empties each Siphon {@code player} carries, once, when they stand on Voidstone.
     *
     * @return how many motes moved, both ways together
     */
    public static int exchange(ServerLevel level, Player player) {
        var below = player.getOnPos();
        if (!level.getBlockState(below).is(VoidworksBlocks.VOIDSTONE.get())) {
            return 0;
        }
        var reach = reach(level, below);
        var inventory = PlayerInventoryWrapper.of(player);
        int moved = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getResource(slot);
            if (!(stack.getItem() instanceof VoidSiphonItem item)) {
                continue;
            }
            var siphon = VoidSiphonItem.moteStore(ItemAccess.forPlayerSlot(player, slot));
            var charging = VoidSiphonItem.mode(player.getInventory().getItem(slot)) == SiphonMode.CHARGE;
            if (charging && VoidSpender.countByGrade(siphon).values().stream().mapToLong(Integer::longValue).sum() < item.capacity()) {
                // A Siphon with room draws on the Voidstone it reaches; a full one wants nothing.
                reach.forEach(VoidstoneNetworks.of(level)::want);
            }
            int left = item.rate();
            for (int grade = VoidEnergy.MAX_GRADE; grade >= VoidEnergy.MIN_GRADE && left > 0; grade--) {
                for (var pos : reach) {
                    if (left == 0) {
                        break;
                    }
                    var store = VoidstoneMotes.at(level, pos);
                    int count = charging ? move(store, siphon, grade, left) : move(siphon, store, grade, left);
                    if (count > 0) {
                        left -= count;
                        moved += count;
                        if (charging) {
                            MoteFlight.toEntity(level, pos.getCenter(), player, grade, count);
                        } else {
                            MoteFlight.toBlock(level, MoteFlight.aimAt(player), pos, grade, count);
                        }
                    }
                }
            }
        }
        return moved;
    }

    /** @return the Voidstone at {@code below}, then each store of its network touching it */
    private static List<BlockPos> reach(ServerLevel level, BlockPos below) {
        var networks = VoidstoneNetworks.of(level);
        var reach = new ArrayList<BlockPos>();
        reach.add(below.immutable());
        for (var direction : Direction.values()) {
            var next = below.relative(direction);
            if (networks.store(next).isPresent()) {
                reach.add(next);
            }
        }
        return reach;
    }

    private static int move(ResourceHandler<MoteResource> from, ResourceHandler<MoteResource> to, int grade, int most) {
        var mote = MoteResource.of(grade);
        try (var transaction = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(from, to, mote::equals, most, transaction);
            transaction.commit();
            return moved;
        }
    }
}
