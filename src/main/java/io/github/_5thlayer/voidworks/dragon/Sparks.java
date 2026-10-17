// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.github._5thlayer.voidworks.energy.VoidEnergy;

/**
 * The sparks a Mote-line dragon's presence trails, one colour for each grade it carries, in the colours
 * its butterflies take ({@link Load#sparks()}); a Matter-line load and nothing carried trail none. Its
 * presence syncs only which grades they are, as a bit per grade, never their counts.
 *
 * @param bits a bit per grade carried, grade {@code g} at bit {@code g}
 */
public record Sparks(int bits) {

    /** No sparks. */
    public static final Sparks NONE = new Sparks(0);

    /** @return a spark of each of {@code grades}, leaving out any no mote has */
    public static Sparks of(Collection<Integer> grades) {
        int bits = 0;
        for (int grade : grades) {
            if (VoidEnergy.isGrade(grade)) {
                bits |= 1 << grade;
            }
        }
        return new Sparks(bits);
    }

    /** @return the grades it trails, lowest first */
    public SortedSet<Integer> grades() {
        var grades = new TreeSet<Integer>();
        for (int grade = VoidEnergy.MIN_GRADE; grade <= VoidEnergy.MAX_GRADE; grade++) {
            if ((bits & 1 << grade) != 0) {
                grades.add(grade);
            }
        }
        return grades;
    }

    /** @return the RGB of each grade's sparks, lowest grade first */
    public List<Integer> colours() {
        return grades().stream().map(GradeColor::of).toList();
    }

    /** @return whether it trails nothing */
    public boolean isEmpty() {
        return bits == 0;
    }
}
