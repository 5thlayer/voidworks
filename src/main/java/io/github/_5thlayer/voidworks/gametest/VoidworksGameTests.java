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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Library's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code scripts/build-gametest-structures.py} writes, and sets up
 * what it needs itself, so the setup is in the diff.
 */
public final class VoidworksGameTests {

    /** The system property that build.gradle's {@code gameTestServer} run sets, to load the test packs. */
    private static final String TEST_PACKS_PROPERTY = "voidworks.gametestPacks";

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
        // The packs that tests lean on are for the game test run alone, which names them in build.gradle.
        if (Boolean.getBoolean(TEST_PACKS_PROPERTY)) {
            modBus.addListener(VoidworksGameTests::addTestPacks);
        }
    }

    private static void addTestPacks(AddPackFindersEvent event) {
        event.addPackFinders(id("resourcepacks/gametest_pressure_override"), PackType.SERVER_DATA,
                Component.literal("Voidworks game test: Void Pressure override"), PackSource.BUILT_IN,
                true, Pack.Position.TOP);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment);
        LoadTests.register(tests);
        MoteTests.register(tests);
        PressureTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(environment, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
