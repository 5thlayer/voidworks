// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * The mote, the unit of void, as a NeoForge transfer resource: one per grade, so motes of one grade
 * pool and motes of different grades never do. A mote is never an item (ADR 0002): only Voidstone,
 * void machines and the Void Siphon hold motes, reached through
 * {@link io.github._5thlayer.voidworks.VoidworksCapabilities#MOTES}.
 */
public final class MoteResource implements Resource {

    /** No mote: what an empty slot of a mote store holds. */
    public static final MoteResource EMPTY = new MoteResource(0);

    private static final MoteResource[] BY_GRADE = new MoteResource[VoidEnergy.MAX_GRADE + 1];

    static {
        for (int grade = VoidEnergy.MIN_GRADE; grade <= VoidEnergy.MAX_GRADE; grade++) {
            BY_GRADE[grade] = new MoteResource(grade);
        }
    }

    /** A mote saved as its grade; it refuses a grade no mote has. Not for {@link #EMPTY}. */
    public static final Codec<MoteResource> CODEC = Codec.INT.comapFlatMap(
            grade -> VoidEnergy.isGrade(grade)
                    ? DataResult.success(of(grade))
                    : DataResult.error(() -> "no mote has grade " + grade),
            MoteResource::grade);

    private final int grade;

    private MoteResource(int grade) {
        this.grade = grade;
    }

    /**
     * The mote of {@code grade}, the same instance each time.
     *
     * @throws IllegalArgumentException when no mote has {@code grade}, outside
     *         {@link VoidEnergy#MIN_GRADE} to {@link VoidEnergy#MAX_GRADE}
     */
    public static MoteResource of(int grade) {
        return BY_GRADE[VoidEnergy.requireGrade(grade)];
    }

    /** The mote's grade; {@link #EMPTY}'s is 0, which no mote has. */
    public int grade() {
        return grade;
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY;
    }

    @Override
    public String toString() {
        return isEmpty() ? "MoteResource[empty]" : "MoteResource[grade " + grade + "]";
    }
}
