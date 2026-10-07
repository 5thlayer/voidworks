// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import org.junit.jupiter.api.Test;

/** What a dimension's entry in the map gives: its base, its harvest kind, and the open void's step. */
class DimensionPressureTest {

    private static final DimensionPressure END = new DimensionPressure(7, HarvestKind.END);
    private static final DimensionPressure OVERWORLD = new DimensionPressure(4, HarvestKind.OVERWORLD);
    private static final DimensionPressure NETHER = new DimensionPressure(1, HarvestKind.NETHER);

    @Test
    void aNamedDimensionTakesItsOwnEntry() {
        assertEquals(NETHER, DimensionPressure.resolve(Optional.of(NETHER), Optional.of(OVERWORLD)));
    }

    @Test
    void anUnnamedDimensionTakesTheOverworldsPressureWithNoHarvestKind() {
        assertEquals(new DimensionPressure(4, HarvestKind.NONE),
                DimensionPressure.resolve(Optional.empty(), Optional.of(OVERWORLD)));
    }

    @Test
    void anUnnamedDimensionFollowsAnOverriddenOverworld() {
        var overridden = new DimensionPressure(9, HarvestKind.OVERWORLD);
        assertEquals(new DimensionPressure(9, HarvestKind.NONE),
                DimensionPressure.resolve(Optional.empty(), Optional.of(overridden)));
    }

    @Test
    void anUnnamedDimensionWithNoOverworldEntryTakesTheShippedOverworldsPressure() {
        assertEquals(new DimensionPressure(shippedFile("minecraft:overworld").get("base").getAsInt(), HarvestKind.NONE),
                DimensionPressure.resolve(Optional.empty(), Optional.empty()));
    }

    @Test
    void theOpenVoidAddsOneStepWhereTheHarvestKindIsEnd() {
        assertEquals(8, END.pressure(() -> true));
    }

    @Test
    void aClosedEndIsAtItsBase() {
        assertEquals(7, END.pressure(() -> false));
    }

    @Test
    void theOpenVoidAddsNothingInOtherKinds() {
        assertEquals(4, OVERWORLD.pressure(() -> true));
        assertEquals(1, NETHER.pressure(() -> true));
        assertEquals(4, new DimensionPressure(4, HarvestKind.NONE).pressure(() -> true));
    }

    @Test
    void theColumnIsOnlyReadWhereTheOpenVoidCounts() {
        OVERWORLD.pressure(() -> {
            throw new AssertionError("the column of a non-End dimension was read");
        });
    }

    @Test
    void theShippedEntriesAreTheFilesOwn() {
        for (var dimension : new String[] {"minecraft:the_nether", "minecraft:overworld", "minecraft:the_end"}) {
            var file = shippedFile(dimension);
            var kind = HarvestKind.valueOf(file.get("harvest_kind").getAsString().toUpperCase(Locale.ROOT));
            assertEquals(new DimensionPressure(file.get("base").getAsInt(), kind),
                    DimensionPressure.shippedEntries().get(dimension), dimension);
        }
    }

    @Test
    void theShippedDefaultsRiseFromNetherThroughOverworldToEnd() {
        assertTrue(shipped("minecraft:the_nether").base() < shipped("minecraft:overworld").base());
        assertTrue(shipped("minecraft:overworld").base() < shipped("minecraft:the_end").base());
    }

    @Test
    void theLowestGradeIsWhatTheLowestShippedPressureHarvests() {
        // The Nether's is the lowest default (theShippedDefaultsRiseFromNetherThroughOverworldToEnd),
        // and a harvest grades its motes one step above the pressure.
        int lowest = shipped("minecraft:the_nether").base();
        assertEquals(lowest + 1, VoidEnergy.MIN_GRADE);
        assertTrue(VoidEnergy.release(VoidEnergy.MIN_GRADE, lowest).isPresent());
    }

    @Test
    void theHighestShippedHarvestIsAGrade() {
        // The End over the open void, one step above its pressure there.
        int highest = shipped("minecraft:the_end").pressure(() -> true) + 1;
        assertTrue(highest <= VoidEnergy.MAX_GRADE);
    }

    private static DimensionPressure shipped(String dimension) {
        return DimensionPressure.shippedEntries().get(dimension);
    }

    /** The file's own text for a dimension, read with no codec, as an independent check of the codec's reading. */
    private static JsonObject shippedFile(String dimension) {
        var path = "/data/voidworks/data_maps/dimension/void_pressure.json";
        try (var reader = new InputStreamReader(DimensionPressureTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("values").getAsJsonObject(dimension);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void harvestKindsAreNamedInLowerCase() {
        assertEquals("end", HarvestKind.END.getSerializedName());
        assertEquals("overworld", HarvestKind.OVERWORLD.getSerializedName());
        assertEquals("nether", HarvestKind.NETHER.getSerializedName());
        assertEquals("none", HarvestKind.NONE.getSerializedName());
    }
}
