// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * An item seen moving between a Well hatch and a Matter-line dragon's paws, as it loads or unloads at a
 * stop ({@link Handling}): the first it took from or put into that hatch, on the Well it stopped at.
 *
 * @param hatch where the hatch is
 * @param ware the items seen moving, a stack at most
 */
public record Passing(Place hatch, Ware ware) {

    static final Codec<Passing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Place.CODEC.fieldOf("hatch").forGetter(Passing::hatch),
            Ware.CODEC.fieldOf("ware").forGetter(Passing::ware)
    ).apply(instance, Passing::new));
}
