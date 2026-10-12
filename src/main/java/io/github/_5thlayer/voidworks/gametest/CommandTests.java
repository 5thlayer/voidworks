// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The {@code /voidworks} commands, run on a real server by a server player: {@code pressure} reports
 * what {@link VoidPressure} says where the player stands, and nothing to a player who is not an
 * operator; {@code mote} fills the network at a Voidstone. A message is checked by its translation key and arguments, since the server has no language loaded.
 */
final class CommandTests {

    private CommandTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("pressure_command_reports_the_pressure_and_harvest_kind_here", 1, CommandTests::pressureReportsHere);
        tests.test("pressure_command_refuses_a_player_who_is_not_an_operator", 1, CommandTests::pressureRefusesNonOperators);
        tests.test("mote_command_puts_motes_into_the_network_at_a_voidstone", 1, CommandTests::motePutsMotesIn);
        tests.test("mote_command_with_no_count_fills_the_network", 1, CommandTests::moteFills);
        tests.test("mote_command_refuses_a_position_with_no_voidstone", 1, CommandTests::moteRefusesNoVoidstone);
    }

    /** Two Voidstone, so one network; the command names the one at the west end and the motes are at the east end too. */
    private static void motePutsMotesIn(GameTestHelper helper) {
        var west = new BlockPos(2, 1, 2);
        helper.setBlock(west, VoidworksBlocks.VOIDSTONE.get());
        helper.setBlock(west.east(), VoidworksBlocks.VOIDSTONE.get());
        var messages = run(playerOnThePlatform(helper), true, "voidworks mote " + coordinates(helper, west) + " 4 10");

        var message = onlyMessage(helper, messages);
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.mote".equals(message.getKey()) || !Integer.valueOf(10).equals(message.getArgs()[0])) {
            helper.fail("the command said " + describe(message) + ", not that it put in 10 motes");
            return;
        }
        if (!DisplacerTests.motes(helper, west.east()).equals(Map.of(4, 10))) {
            helper.fail("the network holds " + DisplacerTests.motes(helper, west.east()) + ", not 10 motes of grade 4");
            return;
        }
        helper.succeed();
    }

    private static void moteFills(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, VoidworksBlocks.VOIDSTONE.get());
        run(playerOnThePlatform(helper), true, "voidworks mote " + coordinates(helper, pos) + " 3 1");
        run(playerOnThePlatform(helper), true, "voidworks mote " + coordinates(helper, pos) + " 5");

        int capacity = VoidworksConfig.voidstoneCapacity();
        var expected = Map.of(3, 1, 5, capacity - 1);
        if (!DisplacerTests.motes(helper, pos).equals(expected)) {
            helper.fail("the network holds " + DisplacerTests.motes(helper, pos) + ", not filled to " + expected);
            return;
        }
        helper.succeed();
    }

    private static void moteRefusesNoVoidstone(GameTestHelper helper) {
        var messages = run(playerOnThePlatform(helper), true, "voidworks mote " + coordinates(helper, new BlockPos(2, 1, 2)) + " 3 10");
        // A failure comes styled, the translation as its one sibling.
        if (messages.size() != 1 || messages.getFirst().getSiblings().size() != 1
                || !(messages.getFirst().getSiblings().getFirst().getContents() instanceof TranslatableContents message)
                || !"commands.voidworks.mote.not_voidstone".equals(message.getKey())) {
            helper.fail("the command should refuse where there is no Voidstone, but said " + messages);
            return;
        }
        helper.succeed();
    }

    /** The absolute coordinates of {@code pos}, as a command takes them. */
    private static String coordinates(GameTestHelper helper, BlockPos pos) {
        var absolute = helper.absolutePos(pos);
        return absolute.getX() + " " + absolute.getY() + " " + absolute.getZ();
    }

    private static void pressureReportsHere(GameTestHelper helper) {
        var player = playerOnThePlatform(helper);
        var messages = run(player, true, "voidworks pressure");

        var level = helper.getLevel();
        int pressure = VoidPressure.at(level, player.blockPosition());
        var kind = VoidPressure.harvestKind(level).getSerializedName();
        var message = onlyMessage(helper, messages);
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.pressure".equals(message.getKey())
                || !List.of(pressure, kind).equals(List.of(message.getArgs()))) {
            helper.fail("the command said " + describe(message) + ", not the pressure " + pressure + " and harvest kind " + kind);
            return;
        }
        helper.succeed();
    }

    private static void pressureRefusesNonOperators(GameTestHelper helper) {
        var messages = run(playerOnThePlatform(helper), false, "voidworks pressure");
        if (messages.stream().anyMatch(CommandTests::isPressureReport)) {
            helper.fail("a player who is not an operator was told the pressure: " + messages);
            return;
        }
        helper.succeed();
    }

    /**
     * A server player of its own, standing on the platform and holding nothing. It is NeoForge's
     * fake player, as the game's own mock server player is deprecated for removal; a fake player
     * ignores teleports, so it is placed directly.
     */
    private static ServerPlayer playerOnThePlatform(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-player"));
        Vec3 feet = helper.absoluteVec(new Vec3(4.5, 1, 4.5));
        player.setPos(feet.x, feet.y, feet.z);
        return player;
    }

    /** Runs {@code command} as {@code player}, an operator or not, and returns what it said back. */
    private static List<Component> run(ServerPlayer player, boolean operator, String command) {
        var messages = new ArrayList<Component>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                messages.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = player.createCommandSourceStack().withSource(sink);
        if (operator) {
            source = source.withPermission(PermissionSet.ALL_PERMISSIONS);
        }
        player.level().getServer().getCommands().performPrefixedCommand(source, command);
        return messages;
    }

    /** The one message the command sent, as a translation; fails the test and returns null if it sent anything else. */
    private static TranslatableContents onlyMessage(GameTestHelper helper, List<Component> messages) {
        if (messages.size() != 1 || !(messages.getFirst().getContents() instanceof TranslatableContents contents)) {
            helper.fail("the command should send one translatable message, sent " + messages);
            return null;
        }
        return contents;
    }

    private static boolean isPressureReport(Component message) {
        return message.getContents() instanceof TranslatableContents contents
                && "commands.voidworks.pressure".equals(contents.getKey());
    }

    private static String describe(TranslatableContents message) {
        return message.getKey() + " " + List.of(message.getArgs());
    }
}
