// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.command;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * What {@code /voidworks showcase} builds, to watch conduction in game: a line of {@link #LENGTH}
 * Voidstone, its near end filled with {@link #MOTES} motes one grade above the Void Pressure there,
 * and a Dormant Mutation Chamber at its far end. The motes crawl along the line, the Chamber soaks
 * its cost from them and wakes, and the rest settle. The Voidstone stays: only a command or a
 * creative player removes it.
 */
public final class Showcase {

    /** How many Voidstone the line is. */
    public static final int LENGTH = 8;
    /** How many motes go in at its near end: the Chamber's shipped cost twice over, one step above the pressure. */
    public static final int MOTES = 32;

    private Showcase() {
    }

    /**
     * Builds the showcase from {@code start} along {@code facing}, the Chamber just past the last Voidstone.
     *
     * @return how many motes went in, or -1 when it built nothing: the Void Pressure at {@code start}
     *         is the highest, so no mote has the grade above it
     */
    public static int build(ServerLevel level, BlockPos start, Direction facing) {
        int grade = VoidPressure.at(level, start) + 1;
        if (!VoidEnergy.isGrade(grade)) {
            return -1;
        }
        var voidstone = VoidworksBlocks.VOIDSTONE.get().in(level);
        for (int i = 0; i < LENGTH; i++) {
            level.setBlock(start.relative(facing, i), voidstone, Block.UPDATE_ALL);
        }
        level.setBlock(start.relative(facing, LENGTH), VoidworksBlocks.MUTATION_CHAMBER.get().defaultBlockState(), Block.UPDATE_ALL);
        try (var transaction = Transaction.openRoot()) {
            int inserted = VoidstoneNetworks.of(level).deposit(start, MoteResource.of(grade), MOTES, transaction);
            transaction.commit();
            return inserted;
        }
    }
}
