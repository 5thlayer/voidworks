// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.github._5thlayer.voidworks.energy.MoteResource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

/** What a Voidstone network shows in Jade: its motes by grade, lowest first, each tinted like its butterflies, then its total against its capacity. */
class NetworkReportTest {

    private static final int PER_VOIDSTONE = 100;
    private static final BlockPos A = new BlockPos(0, 64, 0);

    private final VoidNetworks networks = new VoidNetworks(() -> PER_VOIDSTONE);

    @Test
    void itHoldsEachGradesMotesLowestFirstWithTheNetworksTotalAndCapacity() {
        networks.join(A);
        networks.join(A.east());
        put(7, 5);
        put(3, 20);

        var report = NetworkReport.of(networks.at(A).orElseThrow());

        assertEquals(List.of(3, 7), List.copyOf(report.motes().keySet()));
        assertEquals(Map.of(3, 20L, 7, 5L), report.motes());
        assertEquals(25, report.total());
        assertEquals(2 * PER_VOIDSTONE, report.capacity());
    }

    @Test
    void itsLinesAreAGradeEachTintedLikeItsButterfliesThenTheTotalAgainstTheCapacity() {
        var report = new NetworkReport(Map.of(3, 20L, 7, 5L), 200);

        var lines = report.lines();

        assertEquals(3, lines.size());
        assertLine(lines.get(0), "voidworks.jade.network.grade", 3, 20L);
        assertEquals(TextColor.fromRgb(GradeColor.of(3)), lines.get(0).getStyle().getColor());
        assertLine(lines.get(1), "voidworks.jade.network.grade", 7, 5L);
        assertEquals(TextColor.fromRgb(GradeColor.of(7)), lines.get(1).getStyle().getColor());
        assertLine(lines.get(2), "voidworks.jade.network.held", 25L, 200L);
    }

    @Test
    void anEmptyNetworkShowsOnlyItsTotal() {
        networks.join(A);

        var lines = NetworkReport.of(networks.at(A).orElseThrow()).lines();

        assertEquals(1, lines.size());
        assertLine(lines.getFirst(), "voidworks.jade.network.held", 0L, (long) PER_VOIDSTONE);
    }

    private void put(int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            networks.at(A).orElseThrow().insert(MoteResource.of(grade), count, transaction);
            transaction.commit();
        }
    }

    private static void assertLine(Component line, String key, Object... args) {
        var contents = (TranslatableContents) line.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(List.of(args), List.of(contents.getArgs()));
    }
}
