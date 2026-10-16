// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
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
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Module's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code sh ./gradlew buildGametestStructures} writes, and sets up
 * what it needs itself, so the setup is in the diff.
 */
public final class VoidworksGameTests {

    /** The system property that build.gradle's {@code gameTestServer} run sets, for what only it registers. */
    private static final String OWN_RUN = "voidworks.gametest";

    /** The system property the convention plugin names each run in, which DevPacks reads too. */
    private static final String RUN = "libworks.run";
    private static final String DEV_CLIENT = "client";

    private static final Identifier PLATFORM = id("gametest/platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Voidworks.MOD_ID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
    }

    private VoidworksGameTests() {
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        // Posted only when game tests are enabled, so a production server never registers the tests.
        modBus.addListener(VoidworksGameTests::registerTests);
        // The capabilities that tests lean on are for the game test run alone, which build.gradle marks
        // with voidworks.gametest. DevPacks switches on the run's datapack, gametest_pack/ (libworks ADR 0004).
        if (Boolean.getBoolean(OWN_RUN)) {
            // A vanilla block stands in for a void machine, so the run alone gives it voidworks:motes.
            modBus.addListener(SpendTests::registerCapabilities);
            // A vanilla item stands in for another mod's FE item, so the run alone gives it an energy handler.
            FeChargingTests.registerStandIns(modBus);
        }
        // A Dormant machine that ships with no Module, which the game-test pack gives a cost, and the dev
        // pack too, so the dev client can pay it by hand with a Void Siphon until a real one ships.
        if (Boolean.getBoolean(OWN_RUN) || DEV_CLIENT.equals(System.getProperty(RUN))) {
            DormantTestMachine.register(modBus);
        }
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        // A batch runs its tests together and the next batch only after, so a test in this one runs with no other.
        var alone = event.registerEnvironment(id("alone"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment, alone);
        ButterflyTests.register(tests);
        CommandTests.register(tests);
        ConductionTests.register(tests);
        CreativeTabTests.register(tests);
        DisplacerTests.register(tests);
        DormantTests.register(tests);
        DragonlingTests.register(tests);
        DragonReportTests.register(tests);
        EggTests.register(tests);
        EndDisplacerTests.register(tests);
        FamilyTests.register(tests);
        FeChargingTests.register(tests);
        GalleryTests.register(tests);
        LoadTests.register(tests);
        MoteTests.register(tests);
        MutationChamberTests.register(tests);
        MutationItemsTests.register(tests);
        NetherDisplacerTests.register(tests);
        PresenceTests.register(tests);
        PressureTests.register(tests);
        RideTests.register(tests);
        RoutingTests.register(tests);
        SiphonTests.register(tests);
        SpendTests.register(tests);
        UnmakingChamberTests.register(tests);
        VoidCrucibleTests.register(tests);
        VoidstoneTests.register(tests);
        WellTests.register(tests);
        ZoneUpgradeTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, Holder<TestEnvironmentDefinition<?>> alone) {

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

        private void register(String name, int maxTicks, Consumer<GameTestHelper> body, Holder<TestEnvironmentDefinition<?>> batch) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(batch, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
