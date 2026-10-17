// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Map;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/**
 * What a Void Dragon carries, in its Line's unit, kept in its record. Its Line's {@link Cargo} takes it
 * out of Wells and puts it into them; the Mote line's is a {@link MoteLoad}, and the Matter line's a
 * {@link MatterLoad}.
 */
public sealed interface Load permits MoteLoad, MatterLoad {

    /** Saved as its kind saves it: a Mote-line load as the load was before Lines, a map, and a Matter-line one as a list. */
    Codec<Load> CODEC = Codec.either(MoteLoad.CODEC, MatterLoad.CODEC).xmap(
            either -> either.map(motes -> (Load) motes, matter -> matter),
            load -> switch (load) {
                case MoteLoad motes -> Either.left(motes);
                case MatterLoad matter -> Either.right(matter);
            });

    /** @return how much it is, in its Line's unit: motes for the Mote line, stacks for the Matter line */
    long amount();

    /** @return whether it is nothing */
    boolean isEmpty();

    /** @return how many of each item's kind it carries, whatever their components: none for motes */
    default Map<Identifier, Long> items() {
        return Map.of();
    }

    /** @return the first stack it carries, which its dragon's presence hangs under its body: none for motes */
    default Optional<Ware> firstStack() {
        return Optional.empty();
    }

    /** @return the butterflies following its dragon's presence, of each grade it carries: none for matter */
    default Followers followers() {
        return Followers.NONE;
    }
}
