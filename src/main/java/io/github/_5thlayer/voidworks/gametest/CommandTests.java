// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.voidworks.item.MoteItem;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code /voidworks} commands, run on a real server by a real player: {@code pressure} reports
 * what {@link VoidPressure} says where the player stands, {@code mote} puts motes of a grade in the
 * player's inventory, and neither does anything for a player who is not an operator. A message is
 * checked by its translation key and arguments, since the server has no language loaded.
 */
final class CommandTests {

    private CommandTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("pressure_command_reports_the_pressure_and_harvest_kind_here", 1, CommandTests::pressureReportsHere);
        tests.test("pressure_command_refuses_a_player_who_is_not_an_operator", 1, CommandTests::pressureRefusesNonOperators);
        tests.test("mote_command_gives_the_grade_and_count", 1, CommandTests::moteGivesGradeAndCount);
        tests.test("mote_command_gives_one_by_default", 1, CommandTests::moteGivesOneByDefault);
        tests.test("mote_command_splits_a_count_into_full_stacks", 1, CommandTests::moteSplitsIntoStacks);
        tests.test("mote_command_refuses_a_player_who_is_not_an_operator", 1, CommandTests::moteRefusesNonOperators);
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

    private static void moteGivesGradeAndCount(GameTestHelper helper) {
        var player = playerOnThePlatform(helper);
        var messages = run(player, true, "voidworks mote 3 20");

        var held = motesHeldBy(player);
        if (!held.equals(List.of(new Held(3, 20)))) {
            helper.fail("the player should hold 20 motes of grade 3, holds " + held);
            return;
        }
        var message = onlyMessage(helper, messages);
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.mote".equals(message.getKey()) || !List.of(3, 20).equals(List.of(message.getArgs()))) {
            helper.fail("the command said " + describe(message) + ", not 20 motes of grade 3");
            return;
        }
        helper.succeed();
    }

    private static void moteGivesOneByDefault(GameTestHelper helper) {
        var player = playerOnThePlatform(helper);
        run(player, true, "voidworks mote 5");

        var held = motesHeldBy(player);
        if (!held.equals(List.of(new Held(5, 1)))) {
            helper.fail("the player should hold 1 mote of grade 5, holds " + held);
            return;
        }
        helper.succeed();
    }

    /** A count beyond one stack arrives as several full stacks, as {@code /give} hands them out. */
    private static void moteSplitsIntoStacks(GameTestHelper helper) {
        var player = playerOnThePlatform(helper);
        run(player, true, "voidworks mote 2 100");

        var held = motesHeldBy(player);
        if (held.stream().mapToInt(Held::count).sum() != 100 || held.stream().anyMatch(h -> h.grade() != 2 || h.count() > 64)) {
            helper.fail("the player should hold 100 motes of grade 2 in stacks of at most 64, holds " + held);
            return;
        }
        helper.succeed();
    }

    private static void moteRefusesNonOperators(GameTestHelper helper) {
        var player = playerOnThePlatform(helper);
        run(player, false, "voidworks mote 3 20");

        var held = motesHeldBy(player);
        if (!held.isEmpty()) {
            helper.fail("a player who is not an operator was given motes: " + held);
            return;
        }
        helper.succeed();
    }

    /** A real player, standing on the platform and holding nothing: the mock player spawns outside the test box. */
    private static ServerPlayer playerOnThePlatform(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 feet = helper.absoluteVec(new Vec3(4.5, 1, 4.5));
        player.teleportTo(feet.x, feet.y, feet.z);
        player.getInventory().clearContent();
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

    private static List<Held> motesHeldBy(ServerPlayer player) {
        var held = new ArrayList<Held>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof MoteItem) {
                held.add(new Held(MoteItem.gradeOf(stack), stack.getCount()));
            }
        }
        return held;
    }

    private record Held(int grade, int count) {
    }
}
