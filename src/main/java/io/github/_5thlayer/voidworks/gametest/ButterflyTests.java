// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * {@code /voidworks butterflies}, run on a real server: it sends the butterfly effect to the players
 * nearby and says how many butterflies it showed, and does nothing for a player who is not an
 * operator. What the butterflies look like is the client's, checked in game.
 */
final class ButterflyTests {

    private ButterflyTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("butterflies_command_shows_the_fewest_butterflies_for_the_count", 1, ButterflyTests::showsTheFewest);
        tests.test("butterflies_command_refuses_a_player_who_is_not_an_operator", 1, ButterflyTests::refusesNonOperators);
    }

    private static void showsTheFewest(GameTestHelper helper) {
        var messages = run(playerOnThePlatform(helper), true, "voidworks butterflies 7 73");

        // 73 = 64 + 8 + 1
        if (messages.size() != 1 || !(messages.getFirst().getContents() instanceof TranslatableContents message)
                || !"commands.voidworks.butterflies".equals(message.getKey())
                || !List.of(3, 73, 7).equals(List.of(message.getArgs()))) {
            helper.fail("the command should say it showed 3 butterflies for 73 motes of grade 7, said " + messages);
            return;
        }
        helper.succeed();
    }

    private static void refusesNonOperators(GameTestHelper helper) {
        var messages = run(playerOnThePlatform(helper), false, "voidworks butterflies 7 73");
        if (messages.stream().anyMatch(m -> m.getContents() instanceof TranslatableContents c
                && "commands.voidworks.butterflies".equals(c.getKey()))) {
            helper.fail("a player who is not an operator was shown butterflies: " + messages);
            return;
        }
        helper.succeed();
    }

    private static ServerPlayer playerOnThePlatform(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-player"));
        Vec3 feet = helper.absoluteVec(new Vec3(4.5, 1, 4.5));
        player.setPos(feet.x, feet.y, feet.z);
        return player;
    }

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
        var source = player.createCommandSourceStack().withSource(sink);
        if (operator) {
            source = source.withPermission(PermissionSet.ALL_PERMISSIONS);
        }
        player.level().getServer().getCommands().performPrefixedCommand(source, command);
        return messages;
    }
}
