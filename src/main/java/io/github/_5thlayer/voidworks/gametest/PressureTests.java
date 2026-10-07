// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
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

    /*
     * The defaults docs/spec/void-energy.md gives, which the shipped file must hold. The code's own
     * copy, for a dimension the map does not name, is held to the spec by DimensionPressureTest.
     */
    private static final DimensionPressure NETHER_DEFAULT = new DimensionPressure(1, HarvestKind.NETHER);
    private static final DimensionPressure OVERWORLD_DEFAULT = new DimensionPressure(2, HarvestKind.OVERWORLD);
    private static final DimensionPressure END_DEFAULT = new DimensionPressure(3, HarvestKind.END);

    private static final ResourceKey<Level> UNNAMED_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "unnamed"));

    private PressureTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("void_pressure_here_matches_the_defaults", 1, PressureTests::herePressureMatchesTheDefaults);
        tests.test("void_pressure_nether_matches_the_default", 1, PressureTests::netherMatchesTheDefault);
        tests.test("void_pressure_unnamed_dimension_takes_the_overworlds", 1, PressureTests::unnamedDimensionTakesTheOverworlds);
        tests.test("void_pressure_datapack_entry_overrides_a_default", 1, PressureTests::datapackEntryOverridesADefault);
        tests.test("void_pressure_shipped_entries_match_the_defaults", 1, PressureTests::shippedEntriesMatchTheDefaults);
        tests.test("void_pressure_entry_reads_harvest_kinds_by_lower_case_name", 1, PressureTests::entryReadsHarvestKindsByName);
    }

    /** An entry spells its harvest kind in lower case, and a name that is no kind is an error, not a default. */
    private static void entryReadsHarvestKindsByName(GameTestHelper helper) {
        for (var kind : HarvestKind.values()) {
            var read = parseEntry("{\"base\": 1, \"harvest_kind\": \"" + kind.getSerializedName() + "\"}");
            if (!read.result().equals(Optional.of(new DimensionPressure(1, kind)))) {
                helper.fail("an entry naming " + kind.getSerializedName() + " reads as " + read);
                return;
            }
        }
        var unknown = parseEntry("{\"base\": 1, \"harvest_kind\": \"lava\"}");
        if (unknown.isSuccess()) {
            helper.fail("an entry naming the harvest kind lava should be refused, but reads as " + unknown);
            return;
        }
        helper.succeed();
    }

    private static DataResult<DimensionPressure> parseEntry(String json) {
        return VoidPressure.ENTRY_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    /** The override pack's entry for the End, which is not the shipped one. */
    private static final DimensionPressure OVERRIDDEN_END = new DimensionPressure(9, HarvestKind.NETHER);

    private static void datapackEntryOverridesADefault(GameTestHelper helper) {
        var entry = VoidPressure.dimension(helper.getLevel().registryAccess(), Level.END);
        if (!entry.equals(OVERRIDDEN_END)) {
            helper.fail("the End's entry is " + entry + ", not the datapack's " + OVERRIDDEN_END);
            return;
        }
        helper.succeed();
    }

    /** The shipped file is where the defaults are, and it must hold the spec's. */
    private static void shippedEntriesMatchTheDefaults(GameTestHelper helper) {
        var shipped = readShippedEntries();
        var expected = Map.of(
                "minecraft:the_nether", NETHER_DEFAULT,
                "minecraft:overworld", OVERWORLD_DEFAULT,
                "minecraft:the_end", END_DEFAULT);
        if (!shipped.equals(expected)) {
            helper.fail("the shipped void_pressure.json holds " + shipped + ", not the defaults " + expected);
            return;
        }
        helper.succeed();
    }

    private static Map<String, DimensionPressure> readShippedEntries() {
        var path = "/data/" + Voidworks.MOD_ID + "/data_maps/dimension/" + VoidPressure.DATA_MAP_ID.getPath() + ".json";
        try (var reader = new InputStreamReader(PressureTests.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            var values = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("values");
            var entries = new HashMap<String, DimensionPressure>();
            for (var entry : values.entrySet()) {
                entries.put(entry.getKey(), VoidPressure.ENTRY_CODEC.parse(JsonOps.INSTANCE, entry.getValue()).getOrThrow());
            }
            return entries;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("cannot read the shipped " + path, e);
        }
    }

    /** The test's own dimension is the Overworld, whose entry is the middle of the three. */
    private static void herePressureMatchesTheDefaults(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 2, 0));
        int pressure = VoidPressure.at(level, pos);
        if (pressure != OVERWORLD_DEFAULT.base()) {
            helper.fail("the Void Pressure here is " + pressure + ", not the Overworld's default "
                    + OVERWORLD_DEFAULT.base());
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
        if (!entry.equals(NETHER_DEFAULT)) {
            helper.fail("the Nether's entry is " + entry + ", not its default " + NETHER_DEFAULT);
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
