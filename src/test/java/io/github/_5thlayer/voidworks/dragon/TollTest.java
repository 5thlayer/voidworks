// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The Toll a Matter-line load pays as it loads (spec § Moving matter, Toll): 256 base void energy a
 * stack-equivalent, doubled for each step of Void Pressure the load climbs, paid by the spend rule at the
 * departure Well. The spec's table is the worked check.
 */
class TollTest {

    private static final int NETHER = 1;
    private static final int OVERWORLD = 2;
    private static final int END = 3;

    private static final Toll TOLL = new Toll(256);

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier PEARL = Identifier.withDefaultNamespace("ender_pearl");
    private static final Identifier SHULKER_BOX = Identifier.withDefaultNamespace("shulker_box");

    @Test
    void aStackPaysTheBaseDoubledForEachStepItClimbsAndTheBaseLevelOrDownhill() {
        assertEquals(1_024, TOLL.perStack(NETHER, END));
        assertEquals(512, TOLL.perStack(OVERWORLD, END));
        assertEquals(256, TOLL.perStack(END, END));
        assertEquals(256, TOLL.perStack(END, NETHER));
        assertEquals(256, TOLL.perStack(NETHER, NETHER));
        assertEquals(256, TOLL.perStack(OVERWORLD, OVERWORLD));
    }

    /** Each row of the spec's table: the Toll, and the motes of each grade that pay it at the departure Well, or refused. */
    @Test
    void eachRowOfTheSpecsTableHolds() {
        assertRow(NETHER, END, 1_024, 1_024, 512, 256, 128);
        assertRow(OVERWORLD, END, 512, -1, 512, 256, 128);
        assertRow(END, END, 256, -1, -1, 256, 128);
        assertRow(END, NETHER, 256, -1, -1, 256, 128);
        assertRow(NETHER, NETHER, 256, 256, 128, 64, 32);
        assertRow(OVERWORLD, OVERWORLD, 256, -1, 256, 128, 64);
    }

    /** @param paid how many motes of grades 2 to 5 pay it, each alone, {@code -1} for refused */
    private static void assertRow(int from, int to, long toll, long... paid) {
        assertEquals(toll, TOLL.perStack(from, to));
        for (int grade = 2; grade <= 5; grade++) {
            var spend = Toll.spend(Map.of(grade, 1_000_000L), from, toll);
            long expected = paid[grade - 2];
            assertEquals(expected < 0 ? Optional.empty() : Optional.of(Map.of(grade, (int) expected)), spend.map(VoidEnergy.Spend::motes),
                    "grade " + grade + " from pressure " + from + " to " + to);
        }
    }

    @Test
    void aPartStackPaysItsShare() {
        assertEquals(128, TOLL.of(Ware.of(COBBLESTONE, 64, 32), OVERWORLD, OVERWORLD));
        assertEquals(256, TOLL.of(Ware.of(PEARL, 16, 16), OVERWORLD, OVERWORLD));
        assertEquals(1_024, TOLL.of(Ware.of(COBBLESTONE, 64, 128), OVERWORLD, END));
    }

    @Test
    void aFilledContainerPaysForWhatItHoldsPlusOneForItself() {
        // A shulker box holding 27 stacks of diamond blocks and 8 ender pearls: 27 + 1/2 + 1 stack-equivalents.
        var box = new Ware(SHULKER_BOX, Optional.empty(), 1, 1, new TreeMap<>(Map.of(64, 27 * 64L, 16, 8L)));
        assertEquals(28 * 256 + 128, TOLL.of(box, END, END));
        assertEquals(2 * (28 * 256 + 128), TOLL.of(box.withCount(2), END, END));
        // An empty one pays for itself alone.
        assertEquals(256, TOLL.of(Ware.of(SHULKER_BOX, 1, 1), END, END));
    }

    @Test
    void asManyOfAWareAsABudgetPaysFor() {
        var cobblestone = Ware.of(COBBLESTONE, 64, 640);
        assertEquals(64, TOLL.affordable(cobblestone, OVERWORLD, OVERWORLD, 256));
        assertEquals(66, TOLL.affordable(cobblestone, OVERWORLD, OVERWORLD, 264));
        assertEquals(32, TOLL.affordable(cobblestone, OVERWORLD, END, 256));
        assertEquals(640, TOLL.affordable(cobblestone, OVERWORLD, OVERWORLD, 1_000_000));
        assertEquals(0, TOLL.affordable(cobblestone, OVERWORLD, OVERWORLD, 3));
        assertEquals(640, Toll.FREE.affordable(cobblestone, OVERWORLD, END, 0));
    }

    @Test
    void aNetworksBudgetIsWhatItsMotesAreWorthAtTheDepartureInBaseVoidEnergy() {
        // Grade 2 is worth nothing in the Overworld; 10 grade-3 motes are worth one base each, 3 grade-4 two.
        assertEquals(16, Toll.budget(Map.of(2, 500L, 3, 10L, 4, 3L), OVERWORLD));
    }

    @Test
    void thePriceIsReadFromItsDataAsADatapackSpellsIt() {
        var json = JsonParser.parseString("{\"stack_price\": 512}");
        assertEquals(new Toll(512), Toll.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
