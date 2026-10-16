// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.github._5thlayer.voidworks.taking.VoidTakesBlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The blocks the void took in one test's structure, heard through {@link VoidTakesBlockEvent} from
 * {@link #watch} until {@link #stop}: what each position held, asked by its position in the structure.
 */
final class Takings implements Consumer<VoidTakesBlockEvent> {

    private final GameTestHelper helper;
    /** By absolute position, as a test can be rotated. */
    private final Map<BlockPos, BlockState> taken = new HashMap<>();

    private Takings(GameTestHelper helper) {
        this.helper = helper;
    }

    static Takings watch(GameTestHelper helper) {
        var takings = new Takings(helper);
        NeoForge.EVENT_BUS.addListener(takings);
        return takings;
    }

    @Override
    public void accept(VoidTakesBlockEvent event) {
        if (event.level() == helper.getLevel() && helper.getBounds().contains(Vec3.atCenterOf(event.pos()))) {
            taken.put(event.pos(), event.taken());
        }
    }

    /** @return what the void took at {@code pos} in the structure, or null when it took nothing there */
    @Nullable BlockState at(BlockPos pos) {
        return taken.get(helper.absolutePos(pos));
    }

    /** Asserts that the void took {@code expected} at {@code pos}, in the structure. */
    void assertTook(BlockPos pos, BlockState expected) {
        var at = at(pos);
        helper.assertTrue(expected.equals(at), "the void took " + at + " at " + pos + ", not " + expected + "; it took " + taken + " (absolute)");
    }

    void stop() {
        NeoForge.EVENT_BUS.unregister(this);
    }
}
