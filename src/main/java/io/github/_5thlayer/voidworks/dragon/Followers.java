// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import io.github._5thlayer.voidworks.energy.VoidEnergy;

/**
 * The butterflies following a Mote-line dragon's presence, of each grade it carries, tinted as every
 * mote's are ({@link Load#followers()}); a Matter-line load and nothing carried have none. Its
 * presence syncs only which grades they are, as a bit per grade, never their counts.
 *
 * @param bits a bit per grade, grade {@code g} at bit {@code g}; any other bit is dropped
 */
public record Followers(int bits) {

    /** A bit at each grade a mote can have. */
    private static final int GRADE_BITS = (1 << VoidEnergy.MAX_GRADE + 1) - (1 << VoidEnergy.MIN_GRADE);

    /** No followers. */
    public static final Followers NONE = new Followers(0);

    public Followers {
        bits &= GRADE_BITS;
    }

    /** @return followers of each of {@code grades}, leaving out any number that is no grade */
    public static Followers of(Collection<Integer> grades) {
        int bits = 0;
        for (int grade : grades) {
            if (VoidEnergy.isGrade(grade)) {
                bits |= 1 << grade;
            }
        }
        return new Followers(bits);
    }

    /** @return the grades of its butterflies, lowest first */
    public SortedSet<Integer> grades() {
        var grades = new TreeSet<Integer>();
        for (int grade = VoidEnergy.MIN_GRADE; grade <= VoidEnergy.MAX_GRADE; grade++) {
            if ((bits & 1 << grade) != 0) {
                grades.add(grade);
            }
        }
        return grades;
    }

    /** @return whether it is no butterflies */
    public boolean isEmpty() {
        return bits == 0;
    }
}
