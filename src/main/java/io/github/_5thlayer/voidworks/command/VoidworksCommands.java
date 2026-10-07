// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.item.MoteItem;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /voidworks} commands, a way to check the mote mechanic by hand in game before any
 * machine exists. Both are for operators only.
 *
 * <ul>
 *   <li>{@code /voidworks pressure} reports the Void Pressure where the player stands, the open
 *       void's extra step included, and the dimension's harvest kind.</li>
 *   <li>{@code /voidworks mote <grade> [count]} gives the player motes of that grade, one by
 *       default, in full stacks and dropped at the player's feet when the inventory is full.</li>
 * </ul>
 */
public final class VoidworksCommands {

    /** The most motes one {@code /voidworks mote} gives, as many as 64 full stacks: a typo should not flood the world with items. */
    public static final int MAX_COUNT = 4096;

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
                        .executes(VoidworksCommands::pressure))
                .then(Commands.literal("mote")
                        .then(Commands.argument("grade", IntegerArgumentType.integer(VoidEnergy.MIN_GRADE))
                                .executes(context -> mote(context, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                        .executes(context -> mote(context, IntegerArgumentType.getInteger(context, "count")))))));
    }

    /** @return the Void Pressure at the player's feet */
    private static int pressure(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int pressure = VoidPressure.at(player.level(), player.blockPosition());
        String harvestKind = VoidPressure.harvestKind(player.level()).getSerializedName();
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.pressure", pressure, harvestKind), false);
        return pressure;
    }

    /** @return how many motes the player was given */
    private static int mote(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int grade = IntegerArgumentType.getInteger(context, "grade");
        int maxStackSize = MoteItem.stack(grade, 1).getMaxStackSize();
        for (int left = count; left > 0; left -= maxStackSize) {
            give(player, MoteItem.stack(grade, Math.min(left, maxStackSize)));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.mote", grade, count), true);
        return count;
    }

    /** Puts {@code stack} in the player's inventory, or drops what does not fit, as {@code /give} does. */
    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
}
