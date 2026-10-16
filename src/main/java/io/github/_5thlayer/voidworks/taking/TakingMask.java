// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.taking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * The masks a face is taken through, after Just Dire Things' goo: one per stage, {@link #SIZE}
 * pixels square, each painting what the void has reached by its stage. A pixel is reached from the
 * first stage whose mask paints it and stays reached, and the last stage reaches every pixel, so a
 * mask need not repeat the ones before it. What of a face is left is read as runs along its rows,
 * the masks turned one of {@link #ORIENTATIONS} ways by the face's position so neighbours differ.
 */
public final class TakingMask {

    /** A mask's width and height, in pixels: a block texture's. */
    public static final int SIZE = 16;

    /** The ways a face turns its masks: four turns, each mirrored or not. */
    public static final int ORIENTATIONS = 8;

    /** What of a face's row {@code y} is left, from pixel {@code x0} up to but not including {@code x1}. */
    public record Run(int y, int x0, int x1) {
    }

    /** For each pixel, row by row, the stage that reaches it, from 1 to {@link TakingStages#STAGES}. */
    private final byte[] reachedAt;

    private TakingMask(byte[] reachedAt) {
        this.reachedAt = reachedAt;
    }

    /**
     * @param masks one per stage, in order, each {@link #SIZE} squared pixels row by row, true where it paints
     * @throws IllegalArgumentException when there are not {@link TakingStages#STAGES} masks, or one is not {@link #SIZE} square
     */
    public static TakingMask of(List<boolean[]> masks) {
        if (masks.size() != TakingStages.STAGES) {
            throw new IllegalArgumentException("a face is taken through " + TakingStages.STAGES + " masks, not " + masks.size());
        }
        var reachedAt = new byte[SIZE * SIZE];
        Arrays.fill(reachedAt, (byte) TakingStages.STAGES);
        for (int stage = masks.size(); stage >= 1; stage--) {
            var mask = masks.get(stage - 1);
            if (mask.length != SIZE * SIZE) {
                throw new IllegalArgumentException("mask " + stage + " has " + mask.length + " pixels, not " + SIZE * SIZE);
            }
            for (int pixel = 0; pixel < mask.length; pixel++) {
                if (mask[pixel]) {
                    reachedAt[pixel] = (byte) stage;
                }
            }
        }
        return new TakingMask(reachedAt);
    }

    /** @return where the mask of {@code stage}, from 1 to {@link TakingStages#STAGES}, is among the client's resources */
    public static Identifier path(int stage) {
        return Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "textures/void_taking/stage_" + stage + ".png");
    }

    /** @return whether a mask's pixel of colour {@code argb} paints: whether it is at least half opaque */
    public static boolean paints(int argb) {
        return (argb >>> 24) >= 0x80;
    }

    /** @return the way the face of the block at {@code pos} towards {@code face} turns its masks, from 0 to {@link #ORIENTATIONS} */
    public static int orientation(BlockPos pos, Direction face) {
        // SplitMix64's finaliser, so that neighbouring positions scatter.
        long hash = pos.asLong() * Direction.values().length + face.get3DDataValue();
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (int) (hash & (ORIENTATIONS - 1));
    }

    /**
     * @param reached how many stages the void has reached, from 0 to {@link TakingStages#STAGES}
     * @param orientation how the face turns the masks, from {@link #orientation}
     * @return what of the face the void has not reached, as runs along its rows, top row first
     */
    public List<Run> left(int reached, int orientation) {
        var runs = new ArrayList<Run>();
        for (int y = 0; y < SIZE; y++) {
            int start = -1;
            for (int x = 0; x <= SIZE; x++) {
                boolean left = x < SIZE && reachedAt[turned(x, y, orientation)] > reached;
                if (left && start < 0) {
                    start = x;
                } else if (!left && start >= 0) {
                    runs.add(new Run(y, start, x));
                    start = -1;
                }
            }
        }
        return runs;
    }

    /** @return the mask's pixel that the face's pixel at {@code x}, {@code y} shows, turned by {@code orientation} */
    private static int turned(int x, int y, int orientation) {
        if ((orientation & 1) != 0) {
            x = SIZE - 1 - x;
        }
        if ((orientation & 2) != 0) {
            y = SIZE - 1 - y;
        }
        if ((orientation & 4) != 0) {
            int swap = x;
            x = y;
            y = swap;
        }
        return y * SIZE + x;
    }
}
