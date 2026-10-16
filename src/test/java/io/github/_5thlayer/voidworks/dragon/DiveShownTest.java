// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** How far through its dive a rider is shown to be: from nothing as it sets off to all of it as it arrives, and never beyond. */
class DiveShownTest {

    private final DiveShown shown = new DiveShown(Component.literal("Home"), 1_000, 1_060, DiveColour.VOID);

    @Test
    void itsProgressRunsFromSettingOffToArriving() {
        assertEquals(0.0F, shown.progress(1_000, 0.0F));
        assertEquals(0.5F, shown.progress(1_029, 1.0F));
        assertEquals(1.0F, shown.progress(1_060, 0.0F));
    }

    @Test
    void itsProgressStaysWithinTheDive() {
        assertEquals(0.0F, shown.progress(900, 0.0F));
        assertEquals(1.0F, shown.progress(2_000, 0.0F));
        assertEquals(1.0F, new DiveShown(Component.empty(), 5, 5, DiveColour.VOID).progress(0, 0.0F));
    }
}
