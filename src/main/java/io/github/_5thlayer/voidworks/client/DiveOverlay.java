// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.dragon.DiveColour;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * What a Void Dragon's rider sees through its dive: the void, black, with motes of an Ender pearl's
 * purples swirling in from the edges toward the middle, as a portal's particles are drawn into it,
 * and in the middle the Well they picked, over a bar that fills as the dive goes, in its destination
 * dimension's colour ({@link io.github._5thlayer.voidworks.dragon.DiveShown}). Each mote's place is
 * read from the time alone, so it keeps nothing between frames.
 */
final class DiveOverlay {

    /** How many motes swirl at once. */
    private static final int MOTES = 160;

    /** How long, in milliseconds, a mote takes to swirl in from the edge to the middle. */
    private static final float SWIRL_MILLIS = 2_400.0F;

    /** How many times a mote turns about the middle on its way in. */
    private static final float TURNS = 1.5F;

    private static final int VOID = ARGB.color(255, 0, 0, 0);
    private static final int DEEP = DiveColour.VOID;
    private static final int BRIGHT = ARGB.color(255, 230, 120, 255);

    /** The progress bar's width and height, the gap between it and the label over it, its empty track's colour, and the label's. */
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_GAP = 4;
    private static final int BAR_TRACK = ARGB.color(255, 30, 10, 50);
    private static final int LABEL = ARGB.color(255, 255, 255, 255);

    private DiveOverlay() {
    }

    /** Draws the void over the whole screen while the player rides a dragon through its dive, once it has taken them into the shaft. */
    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof VoidDragon presence) || !presence.inTheVoid()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, VOID);
        float centreX = width / 2.0F;
        float centreY = height / 2.0F;
        float reach = Mth.sqrt(centreX * centreX + centreY * centreY);
        float now = Util.getMillis() / SWIRL_MILLIS;
        for (int i = 0; i < MOTES; i++) {
            // Each mote its own start and slant, from its index, so they never move in step.
            float phase = hash(i, 1);
            float age = Mth.frac(now * (0.7F + 0.6F * hash(i, 2)) + phase);
            float radius = reach * (1.0F - age) * (0.35F + 0.65F * hash(i, 3));
            float angle = Mth.TWO_PI * (hash(i, 4) + age * TURNS);
            int x = Math.round(centreX + Mth.cos(angle) * radius);
            int y = Math.round(centreY + Mth.sin(angle) * radius);
            int size = age > 0.8F ? 1 : 2;
            int alpha = Math.round(255 * Math.min(1.0F, age * 4.0F));
            int colour = ARGB.color(alpha, ARGB.srgbLerp(age, DEEP, BRIGHT));
            graphics.fill(x, y, x + size, y + size, colour);
        }
        var shown = presence.diveShown();
        var font = Minecraft.getInstance().font;
        int barLeft = Math.round(centreX) - BAR_WIDTH / 2;
        int barTop = Math.round(centreY) + BAR_GAP / 2;
        graphics.centeredText(font, shown.label(), Math.round(centreX), barTop - BAR_GAP - font.lineHeight, LABEL);
        graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT, BAR_TRACK);
        float progress = shown.progress(player.level().getGameTime(), deltaTracker.getGameTimeDeltaPartialTick(true));
        graphics.fill(barLeft, barTop, barLeft + Math.round(BAR_WIDTH * progress), barTop + BAR_HEIGHT, shown.colour());
    }

    /** @return a number from 0 to 1 that stays the same for {@code index} and {@code salt} */
    private static float hash(int index, int salt) {
        int h = index * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65535.0F;
    }
}
