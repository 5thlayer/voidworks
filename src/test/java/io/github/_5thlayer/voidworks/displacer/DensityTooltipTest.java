// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** The Density line: the Density alone until the motes are known, then with them, one mote said as one. */
class DensityTooltipTest {

    @Test
    void itShowsTheDensityAloneUntilTheMotesAreKnown() {
        assertLine("voidworks.density.tooltip", List.of(4), DensityTooltip.line(4, OptionalInt.empty()));
    }

    @Test
    void itShowsTheMotesOnceKnown() {
        assertLine("voidworks.density.tooltip.motes", List.of(4, 64), DensityTooltip.line(4, OptionalInt.of(64)));
    }

    @Test
    void itSaysOneMoteAsOne() {
        assertLine("voidworks.density.tooltip.mote", List.of(1, 1), DensityTooltip.line(1, OptionalInt.of(1)));
    }

    private static void assertLine(String key, List<Object> args, Component line) {
        var contents = (TranslatableContents) line.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(args, List.of(contents.getArgs()));
    }
}
