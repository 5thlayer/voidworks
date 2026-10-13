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

/** The two eggs a dragon hatches from: the vanilla egg, founder of every line, and a Drained Dragon Egg. */
public enum EggKind implements StringRepresentable {
    DRAGON_EGG("dragon_egg"),
    DRAINED("drained");

    private final String name;

    EggKind(String name) {
        this.name = name;
    }

    /** @return the egg's own block, which falls and teleports when hit, and its item's */
    public Block block() {
        return this == DRAGON_EGG ? Blocks.DRAGON_EGG : VoidworksBlocks.DRAINED_DRAGON_EGG.get();
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
