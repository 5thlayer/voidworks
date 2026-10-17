// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Module's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code sh ./gradlew buildGametestStructures} writes, and sets up
 * what it needs itself, so the setup is in the diff. It lives in the {@code gametest} source set, which
 * only the Module's own runs load, and hooks itself in, so the {@code @Mod} class names no game-test
 * code and nothing gates it (libworks ADR 0011).
 *
 * <p>A test that needs a pack developer's datapack runs in a batch of its own {@linkplain WithTestPack
 * with that pack switched on}.
 */
@EventBusSubscriber(modid = Voidworks.MOD_ID)
public final class VoidworksGameTests {

    private static final Identifier PLATFORM = id("gametest/platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Voidworks.MOD_ID);

    private static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> ENVIRONMENT_TYPES =
            DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, Voidworks.MOD_ID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
        ENVIRONMENT_TYPES.register("with_test_pack", () -> WithTestPack.CODEC);
    }

    private VoidworksGameTests() {
    }

    @SubscribeEvent
    static void construct(FMLConstructModEvent event) {
        register(event.getContainer().getEventBus());
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        ENVIRONMENT_TYPES.register(modBus);
        modBus.addListener(WithTestPack::addPackFinders);
        // Posted only when game tests are enabled, which a production game never is.
        modBus.addListener(VoidworksGameTests::registerTests);
        // A vanilla block stands in for a void machine, so the Module's own runs give it voidworks:motes.
        modBus.addListener(SpendTests::registerCapabilities);
        // A vanilla item stands in for another mod's FE item, so the Module's own runs give it an energy handler.
        FeChargingTests.registerStandIns(modBus);
        // A Dormant machine that ships with no Module, which the game-test pack gives a cost, and the dev
        // pack too, so the dev client can pay it by hand with a Void Siphon until a real one ships.
        // DevPacks switches on the run's datapack (libworks ADR 0004).
        DormantTestMachine.register(modBus);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        // A batch runs its tests together and the next batch only after, so a test in this one runs with no other.
        var alone = event.registerEnvironment(id("alone"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment, alone, new ConcurrentHashMap<>());
        ButterflyTests.register(tests);
        CommandTests.register(tests);
        ConductionTests.register(tests);
        CreativeTabTests.register(tests);
        DensityTooltipTests.register(tests);
        DisplacerTests.register(tests);
        DormantTests.register(tests);
        DragonlingTests.register(tests);
        DragonReportTests.register(tests);
        EggTests.register(tests);
        EndDisplacerTests.register(tests);
        FamilyTests.register(tests);
        FeChargingTests.register(tests);
        FollowerTests.register(tests);
        GalleryTests.register(tests);
        HangingTests.register(tests);
        LineSyncTests.register(tests);
        LoadTests.register(tests);
        MatterLineTests.register(tests);
        MatterLifeTests.register(tests);
        MatterRoutingTests.register(tests);
        MoteTests.register(tests);
        MutationChamberTests.register(tests);
        MutationItemsTests.register(tests);
        NetherDisplacerTests.register(tests);
        PresenceTests.register(tests);
        PressureTests.register(tests);
        RequesterHatchTests.register(tests);
        RideTests.register(tests);
        RoutingTests.register(tests);
        SeenLoadingTests.register(tests);
        SiphonTests.register(tests);
        SpendTests.register(tests);
        TollTests.register(tests);
        UnmakingChamberTests.register(tests);
        VoidCrucibleTests.register(tests);
        VoidstoneTests.register(tests);
        WellHatchTests.register(tests);
        WellTests.register(tests);
        ZoneUpgradeTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, Holder<TestEnvironmentDefinition<?>> alone,
                     Map<String, Holder<TestEnvironmentDefinition<?>>> packEnvironments) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            register(name, maxTicks, body, environment);
        }

        /**
         * A test that runs in a batch with no other test of the Module's, for one whose setup would
         * reach another's, such as a player in the level, whom every mob nearby sees.
         */
        void testAlone(String name, int maxTicks, Consumer<GameTestHelper> body) {
            register(name, maxTicks, body, alone);
        }

        /**
         * A test that runs in a batch of its own, for one that changes the whole server, such as turning
         * a datapack on, which reloads its data under every test running with it.
         */
        void testOnItsOwn(String name, int maxTicks, Consumer<GameTestHelper> body) {
            register(name, maxTicks, body, event.registerEnvironment(id("on_its_own/" + name), new TestEnvironmentDefinition.AllOf(List.of())));
        }

        /**
         * A test that runs in a batch of its own with one of the tests' datapacks, under {@code
         * gametest_packs/}, switched on; the tests that ask for one pack share its batch.
         */
        void testWithPack(String pack, String name, int maxTicks, Consumer<GameTestHelper> body) {
            register(name, maxTicks, body, packEnvironments.computeIfAbsent(pack,
                    named -> event.registerEnvironment(id("with_" + named), new WithTestPack(named))));
        }

        private void register(String name, int maxTicks, Consumer<GameTestHelper> body, Holder<TestEnvironmentDefinition<?>> batch) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(batch, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
