// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Comparator;

/**
 * What a Line's dragons carry, as {@link Routing} sees it: how much a stop is short of, offers and has
 * room for, and moving a {@link Load} out of a Well and into one, counted in the Line's unit. Trips,
 * claims, dives, harm, healing, calling, riding, stages and death are routing's own, the same for
 * every Line. The Mote line's is {@link MoteCargo}.
 */
interface Cargo {

    /** @return how much {@code stop} is short of, before any claim on it */
    long shortfall(Stop stop);

    /** @return how much more {@code stop} takes, once a dragon healing there has taken {@code healing} motes out of it */
    long room(Stop stop, long healing);

    /** @return how much {@code stop} takes, before any claim on it */
    default long room(Stop stop) {
        return room(stop, 0);
    }

    /** @return how much {@code from} offers that {@code to} wants, before any claim on it */
    long offer(Stop from, Stop to);

    /** @return the order of stops as sources for {@code to}: a dragon loads from the greatest that offers something */
    Comparator<Stop> sources(Stop to);

    /** @return whether {@code stop} wants any of {@code load} */
    boolean wants(Stop stop, Load load);

    /** Takes up to {@code most} of what {@code to} wants out of {@code from}, at its Well at {@code place}. @return what it took */
    Load take(Stop from, Place place, Stop to, long most);

    /**
     * Puts up to {@code most} of {@code load} into {@code to}, at its Well at {@code place}: what {@code to}
     * wants, or for a load stowed, anything it has room for.
     *
     * @return what is left of {@code load}
     */
    Load put(Load load, Place place, Stop to, long most, boolean stow);

    /** Puts {@code most} of {@code load} back into the Well at {@code place}, as a ridden dive sheds it, what is worth least first. @return what is left of {@code load} */
    Load putBack(Load load, Place place, long most);
}
