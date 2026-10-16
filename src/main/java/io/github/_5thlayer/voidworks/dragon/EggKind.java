// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Arrays;
import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The three eggs a dragon hatches from, each fixing its {@link Line}: the vanilla egg, founder of the
 * Mote line, a Drained Dragon Egg, of the Mote line too, and a Matter Dragon Egg, of the Matter line.
 */
public enum EggKind implements StringRepresentable {
    DRAGON_EGG("dragon_egg", Line.MOTE),
    DRAINED("drained", Line.MOTE),
    MATTER("matter", Line.MATTER);

    private final String name;
    private final Line line;

    EggKind(String name, Line line) {
        this.name = name;
        this.line = line;
    }

    /** @return the egg's own block, which falls and teleports when hit, and its item's */
    public Block block() {
        return switch (this) {
            case DRAGON_EGG -> Blocks.DRAGON_EGG;
            case DRAINED -> VoidworksBlocks.DRAINED_DRAGON_EGG.get();
            case MATTER -> VoidworksBlocks.MATTER_DRAGON_EGG.get();
        };
    }

    /** @return the Line of the dragon it hatches */
    public Line line() {
        return line;
    }

    /** @return the kind of egg {@code state} is, empty when it is no egg's own block */
    public static Optional<EggKind> of(BlockState state) {
        return Arrays.stream(values()).filter(kind -> state.is(kind.block())).findFirst();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
