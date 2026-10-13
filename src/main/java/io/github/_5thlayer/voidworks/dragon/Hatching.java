// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Arrays;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Where a dragon's life starts. Either egg, the vanilla egg or a Drained Dragon Egg, that a player
 * places touching a Voidstone network becomes a {@link SoakingEggBlock} the player owns; an egg that came
 * any other way, left on the exit portal, fallen or teleported, stays as it is and never soaks. And
 * while no player has hatched a dragon in the world, a dragon kill places the vanilla egg on the
 * exit portal again, so a lost founder is replaced.
 */
public final class Hatching {

    private Hatching() {
    }

    public static void register() {
        // Last, so the placement stands: no other listener cancels it after the egg starts soaking.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Hatching::onPlaced);
        NeoForge.EVENT_BUS.addListener(Hatching::onLeft);
    }

    private static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && event.getLevel() instanceof ServerLevel level) {
            startSoaking(level, event.getPos(), player);
        }
    }

    /**
     * Starts the egg {@code player} has just placed at {@code pos} soaking, when it is one and touches
     * a Voidstone network: Voidstone or a Void Well in one.
     *
     * @return whether it started soaking
     */
    static boolean startSoaking(ServerLevel level, BlockPos pos, Player player) {
        var kind = EggKind.of(level.getBlockState(pos));
        if (kind.isEmpty() || !touchesANetwork(level, pos)) {
            return false;
        }
        level.setBlock(pos, VoidworksBlocks.SOAKING_EGG.get().defaultBlockState().setValue(SoakingEggBlock.EGG, kind.get()), Block.UPDATE_ALL);
        ((SoakingEggBlockEntity) level.getBlockEntity(pos)).startSoaking(level, player.getUUID());
        return true;
    }

    private static boolean touchesANetwork(ServerLevel level, BlockPos pos) {
        var networks = VoidstoneNetworks.of(level);
        return Arrays.stream(Direction.values()).anyMatch(direction -> networks.at(pos.relative(direction)).isPresent());
    }

    /**
     * A dragon kill, while no player has hatched a dragon: the founder egg goes back on the exit
     * portal once the kill is done, since a kill by command rebuilds the portal, knocking off what
     * stands on it, only after the dragon has left.
     */
    private static void onLeft(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || dragon.getRemovalReason() != Entity.RemovalReason.KILLED
                || !(event.getLevel() instanceof ServerLevel level) || dragon.getDragonFight() == null
                // The first kill places its own egg.
                || !dragon.getDragonFight().hasPreviouslyKilledDragon()) {
            return;
        }
        var podium = EndPodiumFeature.getLocation(dragon.getFightOrigin());
        var server = level.getServer();
        server.schedule(server.wrapRunnable(() -> {
            if (!SavedHatchery.of(server).anyHatched()) {
                placeFounder(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, podium));
            }
        }));
    }

    /**
     * Places the vanilla egg at {@code top}, the top of the exit portal, unless the egg is already
     * on it, just below.
     *
     * @return whether it placed one
     */
    public static boolean placeFounder(ServerLevel level, BlockPos top) {
        if (level.getBlockState(top.below()).is(Blocks.DRAGON_EGG)) {
            return false;
        }
        level.setBlockAndUpdate(top, Blocks.DRAGON_EGG.defaultBlockState());
        return true;
    }
}
