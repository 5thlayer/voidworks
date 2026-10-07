// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.pressure;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * One dimension's entry in the Void Pressure data map, as plain logic that reads no game state: its
 * base pressure and its harvest kind. The pressure is on the same integer scale as a mote's grade
 * ({@link io.github._5thlayer.voidworks.energy.VoidEnergy}).
 *
 * @param base the Void Pressure of the dimension's ordinary positions
 * @param harvestKind how the Void Siphon harvests in the dimension
 */
public record DimensionPressure(int base, HarvestKind harvestKind) {

    /*
     * The provisional numbers (docs/spec/void-energy.md leaves them open), kept in step with the
     * entries the Library ships in data/voidworks/data_maps/dimension/void_pressure.json, which is
     * what a Consumer or a datapack overrides. A game test holds the two together. Only the
     * Overworld's is read in code: it is what an unnamed dimension takes.
     */
    public static final DimensionPressure NETHER_DEFAULT = new DimensionPressure(1, HarvestKind.NETHER);
    public static final DimensionPressure OVERWORLD_DEFAULT = new DimensionPressure(2, HarvestKind.OVERWORLD);
    public static final DimensionPressure END_DEFAULT = new DimensionPressure(3, HarvestKind.END);

    /**
     * The entry that counts for a dimension: its own, or for a dimension the map does not name, the
     * Overworld's pressure with no harvest kind.
     *
     * @param named the dimension's own entry, if the map names it
     * @param overworld the Overworld's entry, if the map names it; {@link #OVERWORLD_DEFAULT} stands
     *        in when a datapack has removed it
     */
    public static DimensionPressure resolve(Optional<DimensionPressure> named, Optional<DimensionPressure> overworld) {
        return named.orElseGet(() ->
                new DimensionPressure(overworld.orElse(OVERWORLD_DEFAULT).base(), HarvestKind.NONE));
    }

    /**
     * The Void Pressure at a position in this dimension.
     *
     * @param openToVoid whether the position is open to the void below it ({@link OpenVoid}); it is
     *        asked only where it counts, so a dimension that is not the End never reads a column
     * @return the base, one step higher where the harvest kind is End and the position is open
     */
    public int pressure(BooleanSupplier openToVoid) {
        if (harvestKind == HarvestKind.END && openToVoid.getAsBoolean()) {
            return base + OpenVoid.EXTRA_PRESSURE;
        }
        return base;
    }
}
