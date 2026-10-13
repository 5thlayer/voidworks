// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.EggKind;
import io.github._5thlayer.voidworks.dragon.Hatching;
import io.github._5thlayer.voidworks.dragon.SoakingEggBlock;
import io.github._5thlayer.voidworks.dragon.SoakingEggBlockEntity;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Dragon eggs on a real server: either egg, placed by a player touching a Voidstone network, soaks its
 * price by the spend rule at its block in four stages, then hatches a Dragonling its placer owns.
 * Its price is fixed when it starts soaking, doubled per dragon its owner has hatched or is soaking.
 * An egg no player placed never soaks, and one broken while soaking drops the egg it was and loses
 * what it soaked. The tests stand in the Overworld, where a mote one grade above the pressure
 * releases {@link VoidEnergy#BASE}; each test's player is new, so owns no dragon yet.
 */
final class EggTests {

    private static final BlockPos EGG = new BlockPos(4, 1, 4);
    /** Where a mote store touches the egg. */
    private static final BlockPos BESIDE = EGG.east();
    /**
     * Where Voidstone touches the egg, beside a test's own mote store, which is no network: an egg
     * starts soaking only touching a Voidstone network, then soaks from every store touching it.
     */
    private static final BlockPos NETWORK = EGG.west();
    /** A second egg, apart from the first and touching a store of its own. */
    private static final BlockPos OTHER_EGG = new BlockPos(4, 1, 1);
    private static final BlockPos OTHER_BESIDE = OTHER_EGG.east();
    /** A third egg, touching a store of its own. */
    private static final BlockPos THIRD_EGG = new BlockPos(2, 1, 4);
    private static final BlockPos THIRD_BESIDE = THIRD_EGG.west();

    /** Long enough for several soaks at the configured interval. */
    private static final int TICKS = 400;

    private EggTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("a_dragon_egg_a_player_places_touching_voidstone_starts_soaking_at_the_first_price_owned_by_that_player", TICKS,
                EggTests::startsSoaking);
        tests.test("a_soaking_egg_shows_four_stages_as_it_fills_then_hatches_a_dragonling_its_placer_owns", TICKS, EggTests::stagesThenHatches);
        tests.test("a_drained_dragon_egg_a_player_places_touching_voidstone_soaks_and_hatches_a_dragonling", TICKS, EggTests::drainedEggHatches);
        tests.test("two_eggs_one_player_places_together_do_not_both_get_the_cheap_price_and_another_player_starts_cheap", TICKS, EggTests::twoEggsDoNotBothGetTheCheapPrice);
        tests.test("an_egg_placed_touching_no_voidstone_network_stays_an_egg", TICKS, EggTests::touchingNoNetworkStaysAnEgg);
        tests.test("an_egg_taken_away_unseen_no_longer_counts_once_its_owner_places_another", TICKS, EggTests::anEggTakenAwayUnseenIsForgotten);
        tests.test("an_egg_no_player_placed_never_soaks", TICKS, EggTests::unplacedEggNeverSoaks);
        tests.test("an_egg_broken_while_soaking_drops_the_egg_it_was_and_loses_what_it_soaked", TICKS, EggTests::brokenWhileSoaking);
        tests.test("a_soaking_egg_neither_falls_nor_teleports", TICKS, EggTests::neitherFallsNorTeleports);
        tests.test("a_drained_dragon_egg_not_soaking_falls_as_the_vanilla_egg_does", TICKS, EggTests::drainedEggFalls);
        tests.test("a_soaking_egg_keeps_its_owner_and_price_across_a_save_and_reload", TICKS, EggTests::ownerAndPriceSurviveAReload);
        tests.test("nothing_hurts_a_dragonling", TICKS, EggTests::nothingHurtsADragonling);
        tests.test("the_founder_egg_is_placed_on_the_exit_portal_unless_one_is_there", TICKS, EggTests::founderEggPlaced);
    }

    private static void startsSoaking(GameTestHelper helper) {
        helper.setBlock(BESIDE, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
        var egg = soaking(helper, EGG);
        helper.assertTrue(egg.owner().equals(Optional.of(player.getUUID())), "the egg's owner is " + egg.owner() + ", not its placer");
        helper.assertTrue(egg.dormancy().cost() == VoidworksConfig.firstEggPrice(),
                "the egg's price is " + egg.dormancy().cost() + ", not the first egg's " + VoidworksConfig.firstEggPrice());
        helper.assertTrue(helper.getBlockState(EGG).getValue(SoakingEggBlock.EGG) == EggKind.DRAGON_EGG, "the soaking egg forgot it was the vanilla egg");
        helper.assertTrue(stage(helper, EGG) == 0, "a new egg is at stage " + stage(helper, EGG));
        helper.succeed();
    }

    /** A quarter of its price at a time: each quarter shows the next stage, and the last hatches it. */
    private static void stagesThenHatches(GameTestHelper helper) {
        var store = new MoteStore();
        SpendTests.placeStore(helper, BESIDE, store);
        helper.setBlock(NETWORK, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
        int grade = DisplacerTests.pressure(helper, EGG) + 1;
        int quarter = Math.toIntExact(VoidworksConfig.firstEggPrice() / 4 / VoidEnergy.BASE);
        var sequence = helper.startSequence();
        for (int stage = 1; stage <= SoakingEggBlock.LAST_STAGE; stage++) {
            int expected = stage;
            sequence.thenExecute(() -> store.add(grade, quarter))
                    .thenWaitUntil(() -> helper.assertTrue(VoidSpender.countByGrade(store).isEmpty(), "the egg has not soaked the quarter"))
                    .thenExecute(() -> helper.assertTrue(stage(helper, EGG) == expected,
                            "the egg paid " + expected + " quarters shows stage " + stage(helper, EGG)));
        }
        sequence.thenExecute(() -> store.add(grade, quarter))
                .thenWaitUntil(() -> helper.assertBlockNotPresent(VoidworksBlocks.SOAKING_EGG.get(), EGG))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, EGG);
                    assertHatchedFor(helper, player);
                    helper.assertTrue(VoidSpender.countByGrade(store).isEmpty(), "the store holds " + VoidSpender.countByGrade(store));
                })
                .thenSucceed();
    }

    private static void drainedEggHatches(GameTestHelper helper) {
        int grade = DisplacerTests.pressure(helper, EGG) + 1;
        var store = new MoteStore().add(grade, Math.toIntExact(VoidworksConfig.firstEggPrice() / VoidEnergy.BASE));
        SpendTests.placeStore(helper, BESIDE, store);
        helper.setBlock(NETWORK, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        placeOnTheFloor(helper, player, VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get(), EGG);
        helper.assertTrue(helper.getBlockState(EGG).getValue(SoakingEggBlock.EGG) == EggKind.DRAINED, "the soaking egg forgot it was a Drained Dragon Egg");
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, EGG);
            assertHatchedFor(helper, player);
        });
    }

    private static void twoEggsDoNotBothGetTheCheapPrice(GameTestHelper helper) {
        helper.setBlock(BESIDE, VoidworksBlocks.VOIDSTONE.get());
        helper.setBlock(OTHER_BESIDE, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
        placeOnTheFloor(helper, player, VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get(), OTHER_EGG);
        long first = VoidworksConfig.firstEggPrice();
        helper.assertTrue(soaking(helper, EGG).dormancy().cost() == first, "the first egg costs " + soaking(helper, EGG).dormancy().cost());
        helper.assertTrue(soaking(helper, OTHER_EGG).dormancy().cost() == 2 * first,
                "the second egg costs " + soaking(helper, OTHER_EGG).dormancy().cost() + ", not twice the first's " + first);
        helper.setBlock(THIRD_BESIDE, VoidworksBlocks.VOIDSTONE.get());
        placeOnTheFloor(helper, player(helper), Items.DRAGON_EGG, THIRD_EGG);
        helper.assertTrue(soaking(helper, THIRD_EGG).dormancy().cost() == first,
                "another player's first egg costs " + soaking(helper, THIRD_EGG).dormancy().cost() + ", not " + first);
        helper.succeed();
    }

    /** On the bare floor, and beside a mote store that is no Voidstone network, such as a Consumer's machine. */
    private static void touchingNoNetworkStaysAnEgg(GameTestHelper helper) {
        placeOnTheFloor(helper, player(helper), Items.DRAGON_EGG, EGG);
        helper.assertBlockPresent(Blocks.DRAGON_EGG, EGG);
        int grade = DisplacerTests.pressure(helper, OTHER_EGG) + 1;
        SpendTests.placeStore(helper, OTHER_BESIDE, new MoteStore().add(grade, 50));
        placeOnTheFloor(helper, player(helper), Items.DRAGON_EGG, OTHER_EGG);
        helper.assertBlockPresent(Blocks.DRAGON_EGG, OTHER_EGG);
        helper.succeed();
    }

    /** Taken away with no side effects, as a world edit may, an egg is forgotten once its owner places another. */
    private static void anEggTakenAwayUnseenIsForgotten(GameTestHelper helper) {
        helper.setBlock(BESIDE, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
        helper.getLevel().setBlock(helper.absolutePos(EGG), Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
        helper.assertTrue(soaking(helper, EGG).dormancy().cost() == VoidworksConfig.firstEggPrice(),
                "the egg taken away still counts: the next costs " + soaking(helper, EGG).dormancy().cost());
        helper.succeed();
    }

    /** An egg set down as the exit portal sets it, and a soaking egg set down by command, beside motes: neither takes any. */
    private static void unplacedEggNeverSoaks(GameTestHelper helper) {
        int grade = DisplacerTests.pressure(helper, EGG) + 1;
        var store = new MoteStore().add(grade, 50);
        var other = new MoteStore().add(grade, 50);
        var before = store.snapshot();
        SpendTests.placeStore(helper, BESIDE, store);
        SpendTests.placeStore(helper, OTHER_BESIDE, other);
        helper.setBlock(EGG, Blocks.DRAGON_EGG);
        helper.setBlock(OTHER_EGG, VoidworksBlocks.SOAKING_EGG.get());
        helper.startSequence()
                .thenIdle(3 * VoidworksConfig.soakInterval())
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.DRAGON_EGG, EGG);
                    helper.assertBlockPresent(VoidworksBlocks.SOAKING_EGG.get(), OTHER_EGG);
                    helper.assertTrue(soaking(helper, OTHER_EGG).owner().isEmpty(), "an egg set by command has an owner");
                    helper.assertTrue(store.snapshot().equals(before), "an egg no player placed soaked: " + store.snapshot());
                    helper.assertTrue(other.snapshot().equals(before), "a soaking egg no player placed soaked: " + other.snapshot());
                })
                .thenSucceed();
    }

    /** A quarter soaked, then broken: the vanilla egg drops, the quarter is lost, and the owner's next egg is cheap again. */
    private static void brokenWhileSoaking(GameTestHelper helper) {
        int grade = DisplacerTests.pressure(helper, EGG) + 1;
        int quarter = Math.toIntExact(VoidworksConfig.firstEggPrice() / 4 / VoidEnergy.BASE);
        var store = new MoteStore().add(grade, quarter);
        SpendTests.placeStore(helper, BESIDE, store);
        helper.setBlock(NETWORK, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stage(helper, EGG) == 1, "the egg has not soaked a quarter"))
                .thenExecute(() -> {
                    helper.getLevel().destroyBlock(helper.absolutePos(EGG), true);
                    helper.assertBlockPresent(Blocks.AIR, EGG);
                    helper.assertItemEntityCountIs(Items.DRAGON_EGG, EGG, 2.0, 1);
                    helper.assertTrue(VoidSpender.countByGrade(store).isEmpty(), "the soaked motes came back: " + VoidSpender.countByGrade(store));
                    placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
                    var again = soaking(helper, EGG);
                    helper.assertTrue(again.dormancy().paid() == 0, "the egg placed again kept " + again.dormancy().paid());
                    helper.assertTrue(again.dormancy().cost() == VoidworksConfig.firstEggPrice(),
                            "the broken egg still counts: the next costs " + again.dormancy().cost());
                })
                .thenSucceed();
    }

    /** Placed against a store's side over air, it hangs there, and neither a use nor a hit moves it. */
    private static void neitherFallsNorTeleports(GameTestHelper helper) {
        var egg = EGG.above();
        var store = egg.east();
        helper.setBlock(store, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        var storeAbsolute = helper.absolutePos(store);
        place(player, Items.DRAGON_EGG, new BlockHitResult(Vec3.atCenterOf(storeAbsolute).add(-0.5, 0, 0), Direction.WEST, storeAbsolute, false));
        helper.assertBlockPresent(VoidworksBlocks.SOAKING_EGG.get(), egg);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertBlockPresent(VoidworksBlocks.SOAKING_EGG.get(), egg);
                    helper.useBlock(egg, player);
                    var absolute = helper.absolutePos(egg);
                    helper.getBlockState(egg).attack(helper.getLevel(), absolute, player);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertBlockPresent(VoidworksBlocks.SOAKING_EGG.get(), egg))
                .thenSucceed();
    }

    /** It is the vanilla egg's own block, so it teleports when hit as that does; set down in the air, it falls. */
    private static void drainedEggFalls(GameTestHelper helper) {
        helper.assertTrue(VoidworksBlocks.DRAINED_DRAGON_EGG.get() instanceof DragonEggBlock, "a Drained Dragon Egg is not a dragon egg");
        var high = EGG.above(2);
        helper.setBlock(high, VoidworksBlocks.DRAINED_DRAGON_EGG.get());
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, high);
            helper.assertBlockPresent(VoidworksBlocks.DRAINED_DRAGON_EGG.get(), EGG);
        });
    }

    private static void ownerAndPriceSurviveAReload(GameTestHelper helper) {
        helper.setBlock(BESIDE, VoidworksBlocks.VOIDSTONE.get());
        var player = player(helper);
        placeOnTheFloor(helper, player, Items.DRAGON_EGG, EGG);
        var level = helper.getLevel();
        var pos = helper.absolutePos(EGG);
        var state = level.getBlockState(pos);
        var saved = soaking(helper, EGG).saveWithFullMetadata(level.registryAccess());
        // Put back as a chunk loading does, with no removal for the hatchery to see.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var reloaded = soaking(helper, EGG);
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        helper.assertTrue(reloaded.owner().equals(Optional.of(player.getUUID())), "the reloaded egg's owner is " + reloaded.owner());
        helper.assertTrue(reloaded.dormancy().cost() == VoidworksConfig.firstEggPrice(), "the reloaded egg's price is " + reloaded.dormancy().cost());
        helper.succeed();
    }

    private static void nothingHurtsADragonling(GameTestHelper helper) {
        var dragonling = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), EGG.above());
        float health = dragonling.getHealth();
        var sources = helper.getLevel().damageSources();
        var creative = player(helper);
        creative.setGameMode(GameType.CREATIVE);
        for (var source : List.of(sources.generic(), sources.magic(), sources.fall(), sources.inFire(), sources.playerAttack(creative))) {
            helper.hurt(dragonling, source, 1000.0F);
            helper.assertTrue(dragonling.isAlive() && dragonling.getHealth() == health,
                    source.getMsgId() + " hurt the Dragonling to " + dragonling.getHealth());
        }
        helper.assertTrue(dragonling.getName().equals(Component.translatable(VoidDragon.DRAGONLING_KEY)),
                "a young Void Dragon is called " + dragonling.getName().getString());
        helper.succeed();
    }

    /** The floor stands in for the exit portal: the founder egg goes on it, and not again on itself. */
    private static void founderEggPlaced(GameTestHelper helper) {
        helper.assertTrue(Hatching.placeFounder(helper.getLevel(), helper.absolutePos(EGG)), "no founder egg was placed");
        helper.assertBlockPresent(Blocks.DRAGON_EGG, EGG);
        helper.assertTrue(!Hatching.placeFounder(helper.getLevel(), helper.absolutePos(EGG.above())), "a second founder egg was placed on the first");
        helper.assertBlockPresent(Blocks.AIR, EGG.above());
        helper.succeed();
    }

    private static void assertHatchedFor(GameTestHelper helper, ServerPlayer player) {
        var hatched = helper.getEntities(VoidworksEntities.VOID_DRAGON.get(), EGG, 3.0);
        helper.assertTrue(hatched.size() == 1, hatched.size() + " Dragonlings hatched, not one");
        var owner = hatched.getFirst().owner();
        helper.assertTrue(owner.equals(Optional.of(player.getUUID())), "the Dragonling's owner is " + owner + ", not the egg's placer");
    }

    private static SoakingEggBlockEntity soaking(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, SoakingEggBlockEntity.class);
    }

    private static int stage(GameTestHelper helper, BlockPos pos) {
        var state = helper.getBlockState(pos);
        return state.hasProperty(SoakingEggBlock.STAGE) ? state.getValue(SoakingEggBlock.STAGE) : -1;
    }

    /** A new player, so one with no dragons yet, on the platform. */
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "egg-placer"));
        var feet = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.setPos(feet.x, feet.y, feet.z);
        return player;
    }

    /** {@code player} places {@code egg} at {@code pos}, using it on the top of the block below. */
    private static void placeOnTheFloor(GameTestHelper helper, ServerPlayer player, Item egg, BlockPos pos) {
        var below = helper.absolutePos(pos.below());
        place(player, egg, new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
        helper.assertTrue(!helper.getBlockState(pos).isAir(), "the player placed no egg at " + pos);
    }

    private static void place(ServerPlayer player, Item egg, BlockHitResult hit) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(egg));
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
