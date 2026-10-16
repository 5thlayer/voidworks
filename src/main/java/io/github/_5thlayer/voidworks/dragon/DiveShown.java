// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * What a Void Dragon's rider is shown of its dive: the Well they picked, and how far through it they
 * are, in its destination dimension's colour ({@link DiveColour}).
 *
 * @param label what the picked Well goes by, as the picker showed it
 * @param from the game time the dive set off
 * @param until the game time it arrives
 * @param colour the bar's colour, opaque
 */
public record DiveShown(Component label, long from, long until, int colour) {

    /** @return how far through the dive it is at game time {@code now} and {@code partialTick} on, from 0 to 1 */
    public float progress(long now, float partialTick) {
        return until <= from ? 1.0F : Mth.clamp((now - from + partialTick) / (until - from), 0.0F, 1.0F);
    }
}
