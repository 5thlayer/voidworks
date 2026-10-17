// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

/**
 * How a Line's grown dragons live, counted in that Line's unit, motes or stacks, as they unload it:
 * how they age, and how much an adult carries for each egg of its Line it lays.
 *
 * @param stages how it ages, each stage lasting so much unloaded
 * @param perEgg how much it carries as an adult for each egg it lays
 */
public record Life(LifeStages stages, long perEgg) {

    public Life {
        if (perEgg < 1) {
            throw new IllegalArgumentException("an egg takes at least one carried: " + perEgg);
        }
    }
}
