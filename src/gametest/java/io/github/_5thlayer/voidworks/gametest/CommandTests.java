// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedHatchery;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.SavedWellHatches;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.VoidWells;
import io.github._5thlayer.voidworks.well.WellHatchBlock;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The {@code /voidworks} commands, run on a real server by a server player: {@code pressure} reports
 * what {@link VoidPressure} says where the player stands, and nothing to a player who is not an
 * operator; {@code mote} fills the network at a Voidstone; {@code well} readies a Well for whoever
 * runs it, {@code dragon} gives them a grown Void Dragon at one, of the Mote line or the one named,
 * {@code dragon remove} takes every dragon out of a box, and {@code hatch} readies a Well hatch on
 * their Well as a filled provider or an asking requester, the commands a Gallery pack scene builds a
 * dragon and a matter link with. A message is checked by its translation key and arguments, since the server has no language loaded.
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
        tests.test("well_command_readies_a_well_for_whoever_runs_it_theirs_with_its_target_paid_and_in_its_network_at_once", 1,
                CommandTests::wellReadies);
        tests.test("well_command_refuses_a_position_with_no_well_core", 1, CommandTests::wellRefusesNoCore);
        tests.test("dragon_command_adds_a_grown_dragon_owned_by_whoever_runs_it_resting_at_the_well_and_hatches_nothing", 1,
                CommandTests::dragonAdds);
        tests.test("dragon_command_with_a_line_adds_a_grown_dragon_of_that_line", 1, CommandTests::dragonAddsOfALine);
        tests.test("dragon_command_refuses_a_position_with_no_well_core", 1, CommandTests::dragonRefusesNoCore);
        tests.test("hatch_command_readies_a_provider_for_whoever_runs_it_on_their_well_holding_so_many_stacks", 1,
                CommandTests::hatchReadiesAProvider);
        tests.test("hatch_command_readies_a_requester_for_whoever_runs_it_on_their_well_asking_for_so_many_stacks", 1,
                CommandTests::hatchReadiesARequester);
        tests.test("hatch_command_refuses_a_position_with_no_hatch", 1, CommandTests::hatchRefusesNoHatch);
        tests.test("hatch_command_refuses_a_hatch_on_another_players_well", 1, CommandTests::hatchRefusesAnothersWell);
        tests.test("dragon_remove_command_removes_every_dragon_resting_in_the_box_or_on_a_trip_to_or_from_it_and_no_other", 1,
                CommandTests::dragonRemoveRemovesInTheBox);
        tests.test("well_dragon_and_dragon_remove_commands_refuse_a_player_who_is_not_an_operator", 1,
                CommandTests::wellAndDragonRefuseNonOperators);
    }

    /** The Well's core, in the middle of its north wall's middle course, its floor on the platform. */
    private static final BlockPos WELL = new BlockPos(4, 3, 2);

    /** A Well hatch's place against the outer face of {@link #WELL}'s east wall, facing east, away from it. */
    private static final BlockPos HATCH = WellHatchTests.HATCH;

    /** The well around {@link #WELL}, its core placed by command, Dormant and nobody's. */
    private static VoidWellBlockEntity buildWell(GameTestHelper helper) {
        WellTests.buildWell(helper, WELL, Direction.NORTH);
        helper.setBlock(WELL, VoidworksBlocks.VOID_WELL.get().defaultBlockState().setValue(VoidWellBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(WELL, VoidWellBlockEntity.class);
    }

    private static void wellReadies(GameTestHelper helper) {
        var well = buildWell(helper);
        var player = playerOnThePlatform(helper);
        var message = onlyMessage(helper, run(player, true, "voidworks well " + coordinates(helper, WELL) + " full"));
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.well".equals(message.getKey())) {
            helper.fail("the command said " + describe(message) + ", not that it readied the Well");
            return;
        }
        helper.assertTrue(well.owner().map(owner -> owner.id().equals(player.getUUID())).orElse(false), "the Well is " + well.owner() + "'s, not the runner's");
        helper.assertTrue(well.target() == FillTarget.FULL, "the Well's target is " + well.target() + ", not full");
        helper.assertTrue(well.dormancy().awake(), "the Well is still Dormant, " + well.dormancy().paid() + " paid");
        var stored = VoidstoneNetworks.of(helper.getLevel()).store(helper.absolutePos(WELL));
        helper.assertTrue(stored.filter(store -> store.kind() == StoreKind.WELL).isPresent(), "the Well is not in a network yet: " + stored);
        var place = Place.of(helper.getLevel(), helper.absolutePos(WELL));
        var listed = SavedVoidWells.of(helper.getLevel().getServer()).owned(player.getUUID());
        helper.assertTrue(listed.equals(List.of(new VoidWells.Entry(place, player.getUUID(), FillTarget.FULL))), "the server lists the runner's Wells as " + listed);
        helper.succeed();
    }

    private static void wellRefusesNoCore(GameTestHelper helper) {
        assertRefused(helper, run(playerOnThePlatform(helper), true, "voidworks well " + coordinates(helper, WELL) + " full"), "commands.voidworks.well.not_a_well");
    }

    private static void dragonAdds(GameTestHelper helper) {
        buildWell(helper);
        var player = playerOnThePlatform(helper);
        var server = helper.getLevel().getServer();
        var message = onlyMessage(helper, run(player, true, "voidworks dragon " + coordinates(helper, WELL)));
        var dragons = SavedVoidDragons.of(server).all().stream().filter(dragon -> dragon.owner().equals(player.getUUID())).toList();
        SavedVoidDragons.of(server).removeAt(place -> dragons.stream().anyMatch(dragon -> dragon.rest().equals(place)));
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.dragon".equals(message.getKey())) {
            helper.fail("the command said " + describe(message) + ", not that it added a dragon");
            return;
        }
        var place = Place.of(helper.getLevel(), helper.absolutePos(WELL));
        helper.assertTrue(dragons.size() == 1 && dragons.getFirst().rest().equals(place) && dragons.getFirst().trip().isEmpty()
                && dragons.getFirst().health() == VoidDragonRecord.FULL_HEALTH, "the runner's dragons are " + dragons + ", not one whole one resting at the Well");
        helper.assertTrue(dragons.getFirst().line() == Line.MOTE, "the command's dragon is of the " + dragons.getFirst().line() + " line, not the Mote line");
        helper.assertTrue(SavedHatchery.of(server).dragons(player.getUUID()) == 0, "the command's dragon counts as hatched");
        helper.succeed();
    }

    private static void dragonAddsOfALine(GameTestHelper helper) {
        buildWell(helper);
        var player = playerOnThePlatform(helper);
        var server = helper.getLevel().getServer();
        var message = onlyMessage(helper, run(player, true, "voidworks dragon " + coordinates(helper, WELL) + " matter"));
        var dragons = SavedVoidDragons.of(server).all().stream().filter(dragon -> dragon.owner().equals(player.getUUID())).toList();
        SavedVoidDragons.of(server).removeAt(place -> dragons.stream().anyMatch(dragon -> dragon.rest().equals(place)));
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.dragon".equals(message.getKey())) {
            helper.fail("the command said " + describe(message) + ", not that it added a dragon");
            return;
        }
        helper.assertTrue(dragons.size() == 1 && dragons.getFirst().line() == Line.MATTER,
                "the runner's dragons are " + dragons + ", not one of the Matter line");
        helper.succeed();
    }

    /** A provider hatch, readied by the Well's owner, is theirs, joined to their Well, and holds four full stacks. */
    private static void hatchReadiesAProvider(GameTestHelper helper) {
        var hatches = SavedWellHatches.of(helper.getLevel().getServer());
        var player = playerOnThePlatform(helper);
        var place = readiedHatch(helper, player, WellHatches.Mode.REQUESTER);
        var message = onlyMessage(helper, run(player, true, "voidworks hatch " + coordinates(helper, HATCH) + " provider minecraft:gold_block 4"));
        var hatch = hatches.remove(place);
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.hatch".equals(message.getKey())) {
            helper.fail("the command said " + describe(message) + ", not that it readied the hatch");
            return;
        }
        assertJoined(helper, hatch, player, WellHatches.Mode.PROVIDER);
        var held = hatch.get().contents().copyToList().stream().filter(stack -> !stack.isEmpty()).toList();
        helper.assertTrue(held.size() == 4 && held.stream().allMatch(stack -> stack.is(Items.GOLD_BLOCK) && stack.getCount() == stack.getMaxStackSize()),
                "the provider holds " + held + ", not four stacks of gold blocks");
        helper.assertTrue(hatch.get().request().isEmpty(), "the provider asks for " + hatch.get().request());
        helper.succeed();
    }

    /** A requester hatch, readied by the Well's owner, is theirs, joined to their Well, asks for three stacks and holds nothing. */
    private static void hatchReadiesARequester(GameTestHelper helper) {
        var hatches = SavedWellHatches.of(helper.getLevel().getServer());
        var player = playerOnThePlatform(helper);
        var place = readiedHatch(helper, player, WellHatches.Mode.PROVIDER);
        var message = onlyMessage(helper, run(player, true, "voidworks hatch " + coordinates(helper, HATCH) + " requester minecraft:gold_block 3"));
        var hatch = hatches.remove(place);
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.hatch.requester".equals(message.getKey())) {
            helper.fail("the command said " + describe(message) + ", not that it readied the hatch");
            return;
        }
        assertJoined(helper, hatch, player, WellHatches.Mode.REQUESTER);
        helper.assertTrue(hatch.get().request().equals(Optional.of(new WellHatches.Request(Items.GOLD_BLOCK, 3))),
                "the requester asks for " + hatch.get().request() + ", not three stacks of gold blocks");
        helper.assertTrue(hatch.get().contents().copyToList().stream().allMatch(ItemStack::isEmpty), "the requester holds " + hatch.get().contents().copyToList());
        helper.succeed();
    }

    private static void hatchRefusesNoHatch(GameTestHelper helper) {
        assertRefused(helper, run(playerOnThePlatform(helper), true, "voidworks hatch " + coordinates(helper, HATCH) + " provider minecraft:gold_block 1"),
                "commands.voidworks.hatch.not_a_hatch");
    }

    /** A hatch on a Well another player readied is not the runner's to ready, and stays as it was. */
    private static void hatchRefusesAnothersWell(GameTestHelper helper) {
        var hatches = SavedWellHatches.of(helper.getLevel().getServer());
        var place = readiedHatch(helper, playerOnThePlatform(helper), WellHatches.Mode.PROVIDER);
        var messages = run(playerOnThePlatform(helper), true, "voidworks hatch " + coordinates(helper, HATCH) + " requester minecraft:gold_block 1");
        var hatch = hatches.remove(place);
        helper.assertTrue(hatch.isEmpty() || hatch.get().mode() == WellHatches.Mode.PROVIDER, "another player's hatch was readied: " + hatch);
        assertRefused(helper, messages, "commands.voidworks.hatch.not_your_well");
    }

    /**
     * {@link #WELL}, readied for {@code owner}, with a hatch of {@code mode} at {@link #HATCH} placed by
     * no player, so it joined no Well yet.
     *
     * @return where the hatch stands
     */
    private static Place readiedHatch(GameTestHelper helper, ServerPlayer owner, WellHatches.Mode mode) {
        buildWell(helper);
        run(owner, true, "voidworks well " + coordinates(helper, WELL) + " none");
        helper.setBlock(HATCH, VoidworksBlocks.WELL_HATCH.get().defaultBlockState().setValue(WellHatchBlock.FACING, Direction.EAST)
                .setValue(WellHatchBlock.MODE, mode));
        return Place.of(helper.getLevel(), helper.absolutePos(HATCH));
    }

    /** Asserts {@code hatch} is {@code owner}'s, joined to {@link #WELL}, a {@code mode}, as its block shows. */
    private static void assertJoined(GameTestHelper helper, Optional<WellHatches.Hatch> hatch, ServerPlayer owner, WellHatches.Mode mode) {
        helper.assertTrue(hatch.isPresent(), "there is no hatch at " + HATCH);
        helper.assertTrue(hatch.get().owner().map(who -> who.id().equals(owner.getUUID())).orElse(false), "the hatch is " + hatch.get().owner() + "'s");
        helper.assertTrue(hatch.get().well().equals(Optional.of(Place.of(helper.getLevel(), helper.absolutePos(WELL)))),
                "the hatch joined " + hatch.get().well() + ", not the Well");
        helper.assertTrue(hatch.get().mode() == mode && helper.getBlockState(HATCH).getValue(WellHatchBlock.MODE) == mode,
                "the hatch is a " + hatch.get().mode() + ", its block a " + helper.getBlockState(HATCH).getValue(WellHatchBlock.MODE) + ", not a " + mode);
    }

    private static void dragonRefusesNoCore(GameTestHelper helper) {
        assertRefused(helper, run(playerOnThePlatform(helper), true, "voidworks dragon " + coordinates(helper, WELL)), "commands.voidworks.dragon.not_a_well");
    }

    /** Dragons of anyone's, resting in the test's box, bound there, loading there, and one away from it, which stays. */
    private static void dragonRemoveRemovesInTheBox(GameTestHelper helper) {
        var level = helper.getLevel();
        var dragons = SavedVoidDragons.of(level.getServer());
        var inside = Place.of(level, helper.absolutePos(new BlockPos(1, 1, 1)));
        var alsoInside = Place.of(level, helper.absolutePos(new BlockPos(7, 2, 7)));
        // In the Nether, far off, where nothing loads, so the dragons there show no presence.
        var outside = new Place(Level.NETHER.identifier(), new BlockPos(100_000, 64, 100_000));
        var resting = dragons.add(UUID.randomUUID(), inside);
        var bound = dragons.add(UUID.randomUUID(), outside);
        dragons.update(on(bound, new Trip(Optional.empty(), alsoInside, 10, false, Trip.Leg.TO_DESTINATION, Long.MAX_VALUE, 0, 0)));
        var loading = dragons.add(UUID.randomUUID(), outside);
        dragons.update(on(loading, new Trip(Optional.of(inside), outside, 10, false, Trip.Leg.TO_SOURCE, Long.MAX_VALUE, 0, 0)));
        var away = dragons.add(UUID.randomUUID(), outside);

        var message = onlyMessage(helper, run(playerOnThePlatform(helper), true,
                "voidworks dragon remove " + coordinates(helper, new BlockPos(0, 0, 0)) + " " + coordinates(helper, new BlockPos(8, 4, 8))));
        var left = List.of(resting, bound, loading, away).stream().filter(dragon -> dragons.get(dragon.id()).isPresent()).toList();
        dragons.removeAt(outside::equals);
        if (message == null) {
            return;
        }
        if (!"commands.voidworks.dragon.remove".equals(message.getKey()) || !Integer.valueOf(3).equals(message.getArgs()[0])) {
            helper.fail("the command said " + describe(message) + ", not that it removed 3 dragons");
            return;
        }
        helper.assertTrue(left.equals(List.of(away)), "the dragons left are " + left + ", not the one away from the box");
        helper.succeed();
    }

    /** A player who is not an operator readies no Well, gets no dragon and removes none. */
    private static void wellAndDragonRefuseNonOperators(GameTestHelper helper) {
        var well = buildWell(helper);
        var player = playerOnThePlatform(helper);
        var dragons = SavedVoidDragons.of(helper.getLevel().getServer());
        var resting = dragons.add(UUID.randomUUID(), Place.of(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
        run(player, false, "voidworks well " + coordinates(helper, WELL) + " full");
        run(player, false, "voidworks dragon " + coordinates(helper, WELL));
        run(player, false, "voidworks dragon remove " + coordinates(helper, new BlockPos(0, 0, 0)) + " " + coordinates(helper, new BlockPos(8, 4, 8)));
        boolean kept = dragons.get(resting.id()).isPresent();
        var given = dragons.all().stream().filter(dragon -> dragon.owner().equals(player.getUUID())).toList();
        dragons.removeAt(resting.rest()::equals);
        dragons.removeAt(place -> given.stream().anyMatch(dragon -> dragon.rest().equals(place)));
        helper.assertTrue(well.owner().isEmpty() && !well.dormancy().awake(), "a player who is not an operator readied the Well");
        helper.assertTrue(given.isEmpty(), "a player who is not an operator was given a dragon");
        helper.assertTrue(kept, "a player who is not an operator removed a dragon");
        helper.succeed();
    }

    /** @return {@code dragon} on {@code trip}, which it is in no hurry to finish */
    private static VoidDragonRecord on(VoidDragonRecord dragon, Trip trip) {
        return new VoidDragonRecord(dragon.id(), dragon.owner(), dragon.line(), dragon.health(), dragon.rest(), dragon.load(), Optional.of(trip), dragon.towardEgg(), dragon.cargo());
    }

    /** Asserts the command refused, saying {@code key}: a failure comes styled, the translation as its one sibling. */
    private static void assertRefused(GameTestHelper helper, List<Component> messages, String key) {
        if (messages.size() != 1 || messages.getFirst().getSiblings().size() != 1
                || !(messages.getFirst().getSiblings().getFirst().getContents() instanceof TranslatableContents message)
                || !key.equals(message.getKey())) {
            helper.fail("the command should refuse with " + key + ", but said " + messages);
            return;
        }
        helper.succeed();
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
    static String coordinates(GameTestHelper helper, BlockPos pos) {
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
