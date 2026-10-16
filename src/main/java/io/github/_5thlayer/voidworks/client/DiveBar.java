// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/** The bar a dive's progress shows as, to its rider ({@link DiveOverlay}) and to the owner who called it ({@link CallOverlay}). */
final class DiveBar {

    static final int WIDTH = 120;
    static final int HEIGHT = 4;

    /** Its empty track's colour. */
    private static final int TRACK = ARGB.color(255, 30, 10, 50);

    private DiveBar() {
    }

    /** Draws it centred on {@code centreX}, its top at {@code top}, filled to {@code progress}, from 0 to 1, in {@code colour}. */
    static void draw(GuiGraphicsExtractor graphics, int centreX, int top, float progress, int colour) {
        int left = centreX - WIDTH / 2;
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, TRACK);
        graphics.fill(left, top, left + Math.round(WIDTH * progress), top + HEIGHT, colour);
    }
}
