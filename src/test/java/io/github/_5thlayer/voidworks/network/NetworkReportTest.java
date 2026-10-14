// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

/**
 * What a Voidstone network shows in Jade: its motes by grade, lowest first, each tinted like its
 * butterflies, then its total against its capacity, then what the Voidstone looked at holds, then what they are worth in void energy where it
 * stands. A Void Well looked at shows no line of its own: what it holds is most of its network's total.
 */
class NetworkReportTest {

    private static final int PER_VOIDSTONE = 100;
    private static final BlockPos A = new BlockPos(0, 64, 0);
    /** The Overworld's default Void Pressure, where grade 3 is worth {@link VoidEnergy#BASE} a mote. */
    private static final int PRESSURE = 2;

    private final VoidNetworks networks = new VoidNetworks(() -> PER_VOIDSTONE, () -> PER_VOIDSTONE);

    @Test
    void itHoldsEachGradesMotesLowestFirstWithTheNetworksTotalAndCapacity() {
        networks.join(A);
        networks.join(A.east());
        put(7, 5);
        put(3, 20);

        var report = NetworkReport.of(networks.at(A).orElseThrow(), networks.store(A).orElseThrow(), PRESSURE);

        assertEquals(List.of(3, 7), List.copyOf(report.motes().keySet()));
        assertEquals(Map.of(3, 20L, 7, 5L), report.motes());
        assertEquals(25, report.total());
        assertEquals(2 * PER_VOIDSTONE, report.capacity());
        assertEquals(Optional.of(new NetworkReport.Held(25, PER_VOIDSTONE)), report.here());
        assertEquals(VoidEnergy.worth(Map.of(3, 20, 7, 5), PRESSURE), report.worth());
    }

    @Test
    void itsLinesAreAGradeEachTintedLikeItsButterfliesThenTheTotalAgainstTheCapacity() {
        var report = new NetworkReport(Map.of(3, 20L, 7, 5L), 200, Optional.of(new NetworkReport.Held(9, 100)), 5000);

        var lines = report.lines();

        assertEquals(5, lines.size());
        assertLine(lines.get(0), "voidworks.jade.network.grade", 3, 20L);
        assertEquals(TextColor.fromRgb(GradeColor.of(3)), lines.get(0).getStyle().getColor());
        assertLine(lines.get(1), "voidworks.jade.network.grade", 7, 5L);
        assertEquals(TextColor.fromRgb(GradeColor.of(7)), lines.get(1).getStyle().getColor());
        assertLine(lines.get(2), "voidworks.jade.network.held", 25L, 200L);
        assertLine(lines.get(3), "voidworks.jade.network.here", 9L, 100L);
        assertLine(lines.get(4), "voidworks.jade.network.worth", 5000L);
    }

    @Test
    void motesNotAboveThePressureAreWorthNothingThere() {
        networks.join(A);
        put(PRESSURE, 10);

        var report = NetworkReport.of(networks.at(A).orElseThrow(), networks.store(A).orElseThrow(), PRESSURE);

        assertEquals(0, report.worth());
        assertLine(report.lines().getLast(), "voidworks.jade.network.worth", 0L);
    }

    @Test
    void anEmptyNetworkShowsOnlyItsTotalNotItsWorth() {
        networks.join(A);

        var lines = NetworkReport.of(networks.at(A).orElseThrow(), networks.store(A).orElseThrow(), PRESSURE).lines();

        assertEquals(2, lines.size());
        assertLine(lines.getFirst(), "voidworks.jade.network.held", 0L, (long) PER_VOIDSTONE);
        assertLine(lines.get(1), "voidworks.jade.network.here", 0L, (long) PER_VOIDSTONE);
    }

    @Test
    void aVoidWellLookedAtShowsNoLineOfItsOwn() {
        networks.join(A, StoreKind.WELL);
        put(3, 20);

        var report = NetworkReport.of(networks.at(A).orElseThrow(), networks.store(A).orElseThrow(), PRESSURE);

        assertEquals(Optional.empty(), report.here());
        var lines = report.lines();
        assertEquals(3, lines.size());
        assertLine(lines.get(0), "voidworks.jade.network.grade", 3, 20L);
        assertLine(lines.get(1), "voidworks.jade.network.held", 20L, (long) PER_VOIDSTONE);
        assertLine(lines.get(2), "voidworks.jade.network.worth", VoidEnergy.worth(Map.of(3, 20), PRESSURE));
    }

    private void put(int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            networks.store(A).orElseThrow().insert(MoteResource.of(grade), count, transaction);
            transaction.commit();
        }
    }

    private static void assertLine(Component line, String key, Object... args) {
        var contents = (TranslatableContents) line.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(List.of(args), List.of(contents.getArgs()));
    }
}
