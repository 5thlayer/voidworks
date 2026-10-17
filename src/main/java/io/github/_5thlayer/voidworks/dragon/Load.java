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

    /**
     * Saved as its kind saves it: a Mote-line load as the load was before Lines, a map of grades, and a
     * Matter-line one as its wares and Toll, read first, since a map of grades has no wares. Made when
     * first used, since a load's class may be initialising this one.
     */
    Codec<Load> CODEC = Codec.lazyInitialized(() -> Codec.either(MatterLoad.CODEC, MoteLoad.CODEC).xmap(
            either -> either.map(matter -> (Load) matter, motes -> motes),
            load -> switch (load) {
                case MatterLoad matter -> Either.left(matter);
                case MoteLoad motes -> Either.right(motes);
            }));

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
