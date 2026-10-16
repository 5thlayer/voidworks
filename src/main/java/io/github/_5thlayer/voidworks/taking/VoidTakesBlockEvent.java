// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.taking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

/**
 * The void took a block: a Void Displacer or a Void Siphon harvested {@link #pos}, or a spent
 * Displacer there became Voidstone. It is posted on the NeoForge event bus, on the server, once the
 * position is Voidstone, so {@link #taken} is what it held before: the harvested block, lava or air,
 * or the Displacer. Each client tracking the position is then shown the void taking it.
 */
public final class VoidTakesBlockEvent extends Event {

    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState taken;

    public VoidTakesBlockEvent(ServerLevel level, BlockPos pos, BlockState taken) {
        this.level = level;
        this.pos = pos.immutable();
        this.taken = taken;
    }

    public ServerLevel level() {
        return level;
    }

    /** @return the position the void took, Voidstone now */
    public BlockPos pos() {
        return pos;
    }

    /** @return what the position held before the void took it */
    public BlockState taken() {
        return taken;
    }
}
