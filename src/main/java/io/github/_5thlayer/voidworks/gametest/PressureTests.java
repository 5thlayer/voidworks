// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.pressure.DimensionPressure;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The Void Pressure at a position, read from the dimension data map on a real server: the shipped
 * defaults, a dimension the map does not name, and a datapack entry overriding a default. The
 * override is a pack that only the game test server loads
 * ({@code resourcepacks/gametest_pressure_override}); it overrides the End, so the End's default is
 * checked in the shipped file itself.
 */
final class PressureTests {

    private static final ResourceKey<Level> UNNAMED_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "unnamed"));

    private PressureTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("void_pressure_here_matches_the_defaults", 1, PressureTests::herePressureMatchesTheDefaults);
        tests.test("void_pressure_nether_matches_the_default", 1, PressureTests::netherMatchesTheDefault);
        tests.test("void_pressure_unnamed_dimension_takes_the_overworlds", 1, PressureTests::unnamedDimensionTakesTheOverworlds);
    }

    /** The test's own dimension is the Overworld, whose entry is the middle of the three. */
    private static void herePressureMatchesTheDefaults(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 2, 0));
        int pressure = VoidPressure.at(level, pos);
        if (pressure != DimensionPressure.OVERWORLD_DEFAULT.base()) {
            helper.fail("the Void Pressure here is " + pressure + ", not the Overworld's default "
                    + DimensionPressure.OVERWORLD_DEFAULT.base());
            return;
        }
        var kind = VoidPressure.harvestKind(level);
        if (kind != HarvestKind.OVERWORLD) {
            helper.fail("the harvest kind here is " + kind + ", not overworld");
            return;
        }
        helper.succeed();
    }

    private static void netherMatchesTheDefault(GameTestHelper helper) {
        var entry = VoidPressure.dimension(helper.getLevel().registryAccess(), Level.NETHER);
        if (!entry.equals(DimensionPressure.NETHER_DEFAULT)) {
            helper.fail("the Nether's entry is " + entry + ", not its default " + DimensionPressure.NETHER_DEFAULT);
            return;
        }
        helper.succeed();
    }

    private static void unnamedDimensionTakesTheOverworlds(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var entry = VoidPressure.dimension(registries, UNNAMED_DIMENSION);
        var overworld = VoidPressure.dimension(registries, Level.OVERWORLD);
        if (entry.base() != overworld.base() || entry.harvestKind() != HarvestKind.NONE) {
            helper.fail("an unnamed dimension has " + entry + ", not the Overworld's pressure " + overworld.base()
                    + " with harvest kind none");
            return;
        }
        helper.succeed();
    }
}
