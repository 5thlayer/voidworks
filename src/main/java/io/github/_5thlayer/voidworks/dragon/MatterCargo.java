// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Comparator;

/**
 * The Matter line's cargo, which carries no motes: no stop is short of anything it carries, offers it
 * any or has room for it, so routing gives a Matter-line dragon nothing to load. It rests, is ridden
 * and called, and heals, as any dragon does.
 */
final class MatterCargo implements Cargo {

    @Override
    public long shortfall(Stop stop) {
        return 0;
    }

    @Override
    public long room(Stop stop, long healing) {
        return 0;
    }

    @Override
    public long offer(Stop from, Stop to) {
        return 0;
    }

    @Override
    public Comparator<Stop> sources(Stop to) {
        return Comparator.comparingInt(stop -> 0);
    }

    @Override
    public boolean wants(Stop stop, Load load) {
        return false;
    }

    /** Nothing, since no stop offers anything. */
    @Override
    public Load take(Stop from, Place place, Stop to, long most) {
        return MoteLoad.NONE;
    }

    /** All of it left, since no stop takes anything. */
    @Override
    public Load put(Load load, Place place, Stop to, long most, boolean stow) {
        return load;
    }

    @Override
    public Load putBack(Load load, Place place, long most) {
        return load;
    }
}
