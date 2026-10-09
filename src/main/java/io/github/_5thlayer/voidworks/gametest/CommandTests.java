// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.commands.CommandSource;
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
 * operator. A message is checked by its translation key and arguments, since the server has no language loaded.
 */
final class CommandTests {

    private CommandTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("pressure_command_reports_the_pressure_and_harvest_kind_here", 1, CommandTests::pressureReportsHere);
        tests.test("pressure_command_refuses_a_player_who_is_not_an_operator", 1, CommandTests::pressureRefusesNonOperators);
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
