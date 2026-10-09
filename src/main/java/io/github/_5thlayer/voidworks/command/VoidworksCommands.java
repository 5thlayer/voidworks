// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /voidworks} commands, a way to check the mote mechanic by hand in game before any
 * machine exists. They are for operators only.
 *
 * <ul>
 *   <li>{@code /voidworks pressure} reports the Void Pressure where the player stands, the open
 *       void's extra step included, and the dimension's harvest kind.</li>
 * </ul>
 *
 * <p>{@code /voidworks mote <pos> <grade> [count]} comes with the first mote store (ADR 0002).
 */
public final class VoidworksCommands {

    private VoidworksCommands() {
    }

    public static void register() {
        // RegisterCommandsEvent is a game-bus event, so it does not go through the mod bus.
        NeoForge.EVENT_BUS.addListener(VoidworksCommands::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("voidworks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("pressure")
                        .executes(VoidworksCommands::pressure)));
    }

    /** @return the Void Pressure at the player's feet */
    private static int pressure(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int pressure = VoidPressure.at(player.level(), player.blockPosition());
        String harvestKind = VoidPressure.harvestKind(player.level()).getSerializedName();
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.pressure", pressure, harvestKind), false);
        return pressure;
    }
}
