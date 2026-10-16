// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

/**
 * What a Void Dragon carries, in its Line's unit, kept in its record. Its Line's {@link Cargo} takes it
 * out of Wells and puts it into them; the Mote line's is a {@link MoteLoad}.
 */
public sealed interface Load permits MoteLoad {

    /** @return how much it is, in its Line's unit: motes for the Mote line */
    long amount();

    /** @return whether it is nothing */
    boolean isEmpty();
}
