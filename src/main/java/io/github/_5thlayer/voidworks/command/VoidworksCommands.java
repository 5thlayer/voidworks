// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.command;

import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.network.MoteNetwork;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The {@code /voidworks} commands, a way to check the mote mechanic by hand in game before any
 * machine exists. They are for operators only.
 *
 * <ul>
 *   <li>{@code /voidworks pressure} reports the Void Pressure where the player stands, the open
 *       void's extra step included, and the dimension's harvest kind.</li>
 *   <li>{@code /voidworks butterflies <grade> <count>} shows {@code count} motes of {@code grade}
 *       flying to the player as butterflies.</li>
 *   <li>{@code /voidworks mote <pos> <grade> [count]} puts {@code count} motes of {@code grade}
 *       into the network of the Voidstone at {@code pos}, as many as it has room for, or with no
 *       count fills it.</li>
 * </ul>
 */
public final class VoidworksCommands {

    /** The most motes one {@code /voidworks butterflies} shows. */
    static final int MAX_COUNT = 4096;

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
                .then(Commands.literal("butterflies")
                        .then(Commands.argument("grade", IntegerArgumentType.integer(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                        .executes(VoidworksCommands::butterflies))))
                .then(Commands.literal("mote")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("grade", IntegerArgumentType.integer(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE))
                                        .executes(context -> mote(context, Integer.MAX_VALUE))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> mote(context, IntegerArgumentType.getInteger(context, "count"))))))));
    }

    /** Fills the network at a Voidstone, from it outward. @return how many motes went in */
    private static int mote(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        var level = context.getSource().getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(context, level, "pos");
        int grade = IntegerArgumentType.getInteger(context, "grade");
        var networks = VoidstoneNetworks.of(level);
        Optional<MoteNetwork> network = level.getBlockState(pos).is(VoidworksBlocks.VOIDSTONE.get())
                ? networks.at(pos)
                : Optional.empty();
        if (network.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.voidworks.mote.not_voidstone", pos.toShortString()));
            return 0;
        }
        int inserted;
        try (var transaction = Transaction.openRoot()) {
            inserted = networks.deposit(pos, MoteResource.of(grade), count, transaction);
            transaction.commit();
        }
        long holds = network.get().total();
        long capacity = network.get().capacity();
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.mote",
                inserted, grade, pos.toShortString(), holds, capacity), false);
        return inserted;
    }

    /** @return the Void Pressure at the player's feet */
    private static int pressure(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int pressure = VoidPressure.at(player.level(), player.blockPosition());
        String harvestKind = VoidPressure.harvestKind(player.level()).getSerializedName();
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.pressure", pressure, harvestKind), false);
        return pressure;
    }

    /** Shows the motes flying to the player from four blocks ahead of them. @return how many butterflies */
    private static int butterflies(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int grade = IntegerArgumentType.getInteger(context, "grade");
        int count = IntegerArgumentType.getInteger(context, "count");
        Vec3 ahead = player.getEyePosition().add(player.getLookAngle().multiply(4, 0, 4));
        int butterflies = MoteFlight.toEntity(player.level(), ahead, player, grade, count);
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.voidworks.butterflies", butterflies, count, grade), false);
        return butterflies;
    }
}
