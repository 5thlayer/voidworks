// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * A grown Void Dragon's life stages: young, adult, senior and old, each lasting the same cargo
 * unloaded; each multiplies its speed, stacked on its health's floored share, and its load; and only
 * the cargo it unloads as an adult counts toward its eggs.
 */
class LifeStagesTest {

    private static final long STAGE = 1_000;
    private static final LifeStages STAGES = new LifeStages(STAGE, LifeStages.DEFAULTS);
    private static final Place HERE = new Place(Identifier.withDefaultNamespace("overworld"), BlockPos.ZERO);

    @Test
    void aDragonIsYoungThenAdultThenSeniorThenOldByTheCargoItHasUnloaded() {
        assertEquals(LifeStage.YOUNG, STAGES.of(0));
        assertEquals(LifeStage.YOUNG, STAGES.of(999));
        assertEquals(LifeStage.ADULT, STAGES.of(1_000));
        assertEquals(LifeStage.SENIOR, STAGES.of(2_500));
        assertEquals(LifeStage.OLD, STAGES.of(3_999));
        assertEquals(LifeStage.OLD, STAGES.of(10_000));
    }

    @Test
    void itHasOutlivedItsOldStageOnceThatTooRunsOut() {
        assertFalse(STAGES.outlived(3_999));
        assertTrue(STAGES.outlived(4_000));
    }

    @Test
    void eachStageMultipliesItsLoad() {
        assertEquals(8_192, STAGES.load(8_192, 0));
        assertEquals(16_384, STAGES.load(8_192, 1_000));
        assertEquals(28_672, STAGES.load(8_192, 2_000));
        assertEquals(49_152, STAGES.load(8_192, 3_000));
    }

    @Test
    void itsSpeedIsItsStagesFactorTimesItsHealthsShareFlooredAtAQuarter() {
        assertEquals(100, STAGES.speed(dragon(100, 0)));
        assertEquals(60, STAGES.speed(dragon(100, 1_000)));
        // An old dragon at its weakest flies at a fifth of a quarter: the floor bounds health's share alone.
        assertEquals(5, STAGES.speed(dragon(1, 3_000)));
        assertEquals(21, STAGES.speed(dragon(60, 2_000)));
    }

    @Test
    void onlyCargoUnloadedAsAnAdultCountsTowardItsEggs() {
        assertEquals(0, STAGES.towardEggs(0, 900));
        // Crossing into adulthood, only what lies beyond its start counts.
        assertEquals(300, STAGES.towardEggs(900, 400));
        assertEquals(500, STAGES.towardEggs(1_200, 500));
        // Crossing out of it, only what lies before its end.
        assertEquals(200, STAGES.towardEggs(1_800, 700));
        assertEquals(0, STAGES.towardEggs(2_000, 500));
    }

    @Test
    void configuredFactorsAreTakenAsTheDefaultsUnlessEachIsFourPositiveOnes() {
        var configured = LifeStages.of(STAGE, List.of(2.0, 1.0, 0.5, 0.25), List.of(1.0, 1.5, 2.0, 3.0));
        assertEquals(new LifeStages.Factors(0.5, 2.0), configured.factors().get(LifeStage.SENIOR));
        assertEquals(STAGES, LifeStages.of(STAGE, List.of(1.0, 1.0), LifeStages.DEFAULT_LOADS));
        assertEquals(STAGES, LifeStages.of(STAGE, LifeStages.DEFAULT_SPEEDS, List.of(1.0, 0.0, 1.0, 1.0)));
    }

    private static VoidDragonRecord dragon(int health, long cargo) {
        var grown = VoidDragonRecord.grown(UUID.randomUUID(), Line.MOTE, HERE);
        var aged = new VoidDragonRecord(grown.id(), grown.owner(), grown.line(), grown.health(), grown.rest(), grown.load(), grown.trip(), 0, cargo);
        return health == VoidDragonRecord.FULL_HEALTH ? aged : aged.weakened(VoidDragonRecord.FULL_HEALTH - health);
    }
}
