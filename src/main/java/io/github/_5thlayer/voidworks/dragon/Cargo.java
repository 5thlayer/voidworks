// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Comparator;
import java.util.List;

/**
 * What a Line's dragons carry, as {@link Routing} sees it: how much a stop is short of, offers and has
 * room for, and moving a {@link Load} out of a Well and into one, counted in the Line's unit. Trips,
 * dives, harm, healing, calling, riding, stages and death are routing's own, the same for every Line;
 * routing finds the other dragons' claims, and the cargo counts them. The Mote line's is
 * {@link MoteCargo}, and the Matter line's {@link MatterCargo}.
 *
 * <p>Wherever it takes {@code bound}, those are the trips of the Line's other dragons, whoever's, on
 * their way to the network of the stop it is short; {@code loading}, those on their way to load on the
 * network of the stop it is taken from.
 */
interface Cargo {

    /** @return the most a young dragon of its Line carries, which its life stage multiplies */
    long load();

    /** @return how much {@code stop} is short of, less what is on its way to it on {@code bound} */
    long shortfall(Stop stop, List<Trip> bound);

    /**
     * @return how much more {@code stop} takes of a load it may not want, before any claim on it, once a
     *         dragon healing there has taken {@code healing} motes out of its network
     */
    long room(Stop stop, long healing);

    /** @return how much {@code stop} takes, before any claim on it */
    default long room(Stop stop) {
        return room(stop, 0);
    }

    /** @return how much {@code from} offers that {@code to} wants, less what other dragons are on their way to load there on {@code loading} */
    long offer(Stop from, Stop to, List<Trip> loading, List<Trip> bound);

    /** @return the order of stops as sources for {@code to}: a dragon loads from the greatest that offers something */
    Comparator<Stop> sources(Stop to);

    /** @return whether {@code stop} wants any of {@code load} */
    boolean wants(Stop stop, Load load);

    /** @return {@code trip}, planned from {@code from} to {@code to}, claiming what it is to load there, up to {@code most} */
    Trip planning(Trip trip, Stop from, Stop to, long most, List<Trip> loading, List<Trip> bound);

    /** Takes up to {@code most} of what {@code to} wants out of {@code from}, at its Well at {@code place}. @return what it took */
    Load take(Stop from, Place place, Stop to, long most, List<Trip> loading, List<Trip> bound);

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
