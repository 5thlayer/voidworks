// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The Void Well's structure, as plain logic over positions: 5 by 5 by 5 around its core. Its 3×3
 * floor is casing; a 5×5 ring of casing walls stands around the floor, three courses high, the
 * floor one level below the walls' lower course; a ring of rim sits on top; and the 3×3 shaft
 * inside the walls is open. The core is in the middle of one wall, in its middle course, facing
 * out, so what stands outside that wall reaches it.
 *
 * <pre>
 * floor        lower, upper courses   middle course   rim
 * . . . . .    W W W W W              W W C W W       R R R R R
 * . F F F .    W . . . W              W . . . W       R . . . R
 * . F F F .    W . . . W              W . . . W       R . . . R
 * . F F F .    W . . . W              W . . . W       R . . . R
 * . . . . .    W W W W W              W W W W W       R R R R R
 * </pre>
 */
public final class WellStructure {

    /** What a position holds, as far as the well is concerned. */
    public enum Part {
        /** Well casing: the floor and the walls. */
        CASING,
        /** Well rim: the raised lip on top. */
        RIM,
        /** Open: air, which the shaft must be. */
        OPEN,
        /** Anything else. */
        NONE
    }

    private WellStructure() {
    }

    /**
     * @param core where the core stands
     * @param facing the way the core faces, out of its wall; horizontal
     * @return what each position of the well around {@code core} must hold, the core's own left out;
     *         a position under the walls, beside the floor, is not the well's
     */
    public static Map<BlockPos, Part> layout(BlockPos core, Direction facing) {
        var centre = core.relative(facing.getOpposite(), 2);
        var layout = new LinkedHashMap<BlockPos, Part>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                var column = centre.offset(dx, 0, dz);
                boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (wall) {
                    for (int dy = -1; dy <= 1; dy++) {
                        layout.put(column.above(dy), Part.CASING);
                    }
                    layout.put(column.above(2), Part.RIM);
                } else {
                    layout.put(column.below(2), Part.CASING);
                    for (int dy = -1; dy <= 2; dy++) {
                        layout.put(column.above(dy), Part.OPEN);
                    }
                }
            }
        }
        layout.remove(core);
        return layout;
    }

    /**
     * @param core where the core stands
     * @param facing the way the core faces, out of its wall
     * @param parts what each position holds
     * @return whether the well around {@code core} is whole: every casing, rim and open position as
     *         it should be; what stands outside the well does not matter
     */
    public static boolean whole(BlockPos core, Direction facing, Function<BlockPos, Part> parts) {
        if (facing.getAxis().isVertical()) {
            return false;
        }
        return layout(core, facing).entrySet().stream().allMatch(entry -> parts.apply(entry.getKey()) == entry.getValue());
    }
}
