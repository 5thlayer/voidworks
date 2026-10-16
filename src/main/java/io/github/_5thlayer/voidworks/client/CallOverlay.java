// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.dragon.CallPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * What an owner calling a Void Dragon sees while it dives to them: a bar just over the action bar's
 * word that one is coming, filling as the dive goes, in the colour of the dimension it comes to
 * ({@link CallPayload}). The server sends the dive each step; once it stops, or the dive is over,
 * the bar is gone.
 */
final class CallOverlay {

    /** How long a dive sent is shown with no other sent after it: a little over a step. */
    private static final int KEPT_TICKS = 30;

    /** The bar's gap over the action bar's text. */
    private static final int BAR_GAP = 3;

    /**
     * Where vanilla's {@code Gui} sets the action bar's text: by default this far over the bottom of the
     * screen, raised by as much as the status bars rise over the held item's name, at {@link #ITEM_NAME_Y};
     * the text is drawn {@link #TEXT_RISE} over that.
     */
    private static final int ACTION_BAR_Y = 68;
    private static final int ITEM_NAME_Y = 59;
    private static final int TEXT_RISE = 4;

    private static @Nullable CallPayload shown;
    private static long receivedAt;

    private CallOverlay() {
    }

    /** Keeps the dive the server sent. */
    static void receive(CallPayload payload) {
        var level = Minecraft.getInstance().level;
        shown = payload;
        receivedAt = level == null ? 0 : level.getGameTime();
    }

    /** Forgets the dive, as the client leaves a server. */
    static void forget() {
        shown = null;
    }

    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var minecraft = Minecraft.getInstance();
        var call = shown;
        if (call == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        long now = minecraft.level.getGameTime();
        float progress = call.shown().progress(now, deltaTracker.getGameTimeDeltaPartialTick(true));
        if (now - receivedAt > KEPT_TICKS || progress >= 1.0F) {
            return;
        }
        // The action bar's text rises over the status bars, as vanilla's Gui moves it; the bar rides over it.
        var gui = minecraft.gui;
        int textY = graphics.guiHeight() - Math.max(Math.max(gui.leftHeight, gui.rightHeight) + (ACTION_BAR_Y - ITEM_NAME_Y), ACTION_BAR_Y) - TEXT_RISE;
        DiveBar.draw(graphics, graphics.guiWidth() / 2, textY - BAR_GAP - DiveBar.HEIGHT, progress, call.colour());
    }
}
