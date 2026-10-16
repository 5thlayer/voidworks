// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;

/**
 * What FactoryWorks #691's shared gallery check asserts of a Gallery pack, kept here until libworks'
 * testkit carries it (ADR 0008, libworks#30), and written to move there as it is: the pack is
 * offered and off by default; its build function, run by an operator player, runs every command
 * without failing, a failure naming its command; and each scene's key block stands where the scene
 * puts it. It looks at the world, never at how the functions are written.
 */
final class GalleryCheck {

    private GalleryCheck() {
    }

    /**
     * Asserts the server offers {@code pack}, visible, and that it is off by default: neither required
     * nor added to a world on its own, new or old. It asks the pack, not whether it is on now, which
     * another test may have left so.
     */
    static void assertOfferedAndOff(GameTestHelper helper, String pack) {
        var repository = helper.getLevel().getServer().getPackRepository();
        var offered = repository.getPack(pack);
        helper.assertTrue(offered != null && !offered.isHidden(), "the server does not offer " + pack + ": " + repository.getAvailableIds());
        helper.assertFalse(offered.isRequired() || offered.getPackSource().shouldAddAutomatically(), pack + " is on by default");
    }

    /** Turns {@code pack} on or off, as {@code /datapack} does. @return the reload, done once the pack is on or off */
    static CompletableFuture<Void> select(MinecraftServer server, String pack, boolean on) {
        var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        selected.remove(pack);
        if (on) {
            selected.add(pack);
        }
        return server.reloadResources(selected);
    }

    /** Asserts {@code function}, run as {@code source}, runs every command it calls without failing. */
    static void assertRuns(GameTestHelper helper, CommandSourceStack source, Identifier function) {
        var failures = failures(source, function);
        helper.assertTrue(failures.isEmpty(), function + " failed: " + String.join("; ", failures));
    }

    /** Asserts each key block, at its position from {@code origin}, stands there. */
    static void assertKeyBlocks(GameTestHelper helper, BlockPos origin, Map<BlockPos, Block> keys) {
        keys.forEach((offset, block) -> {
            var pos = origin.offset(offset);
            var found = helper.getLevel().getBlockState(pos);
            helper.assertTrue(found.is(block), "the scene's " + block + " is not at " + pos.toShortString() + ", where stands " + found);
        });
    }

    /**
     * Runs {@code function} as {@code source}, traced as {@code /debug function} traces it, so a
     * command that fails anywhere in it, in a function it calls too, is named.
     *
     * @return each failing command with what it said, in the order they ran; one entry when there is no such function
     */
    static List<String> failures(CommandSourceStack source, Identifier function) {
        var functions = source.getServer().getFunctions();
        var found = functions.get(function);
        if (found.isEmpty()) {
            return List.of("there is no function " + function);
        }
        var failures = new ArrayList<String>();
        var tracer = new TraceCallbacks() {
            private String command = "";

            @Override
            public void onCommand(int depth, String command) {
                this.command = command;
            }

            @Override
            public void onReturn(int depth, String command, int result) {
            }

            @Override
            public void onError(String message) {
                failures.add("`" + command + "`: " + message);
            }

            @Override
            public void onCall(int depth, Identifier function, int size) {
            }

            @Override
            public void close() {
            }
        };
        Commands.executeCommandInContext(source, context -> {
            context.tracer(tracer);
            try {
                ExecutionContext.queueInitialFunctionCall(context, found.get().instantiate(null, functions.getDispatcher()), source, CommandResultCallback.EMPTY);
            } catch (FunctionInstantiationException e) {
                failures.add(e.messageComponent().getString());
            }
        });
        return failures;
    }
}
