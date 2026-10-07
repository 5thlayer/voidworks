// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

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
    void anUnnamedDimensionWithNoOverworldEntryTakesTheDefaultOverworld() {
        assertEquals(new DimensionPressure(DimensionPressure.OVERWORLD_DEFAULT.base(), HarvestKind.NONE),
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
    void theDefaultsAreTheSpecs() {
        assertEquals(new DimensionPressure(1, HarvestKind.NETHER), DimensionPressure.NETHER_DEFAULT);
        assertEquals(new DimensionPressure(2, HarvestKind.OVERWORLD), DimensionPressure.OVERWORLD_DEFAULT);
        assertEquals(new DimensionPressure(3, HarvestKind.END), DimensionPressure.END_DEFAULT);
    }

    @Test
    void theDefaultsRiseFromNetherThroughOverworldToEnd() {
        assertTrue(DimensionPressure.NETHER_DEFAULT.base() < DimensionPressure.OVERWORLD_DEFAULT.base());
        assertTrue(DimensionPressure.OVERWORLD_DEFAULT.base() < DimensionPressure.END_DEFAULT.base());
    }

    @Test
    void harvestKindsAreNamedInLowerCase() {
        assertEquals("end", HarvestKind.END.getSerializedName());
        assertEquals("overworld", HarvestKind.OVERWORLD.getSerializedName());
        assertEquals("nether", HarvestKind.NETHER.getSerializedName());
        assertEquals("none", HarvestKind.NONE.getSerializedName());
    }
}
