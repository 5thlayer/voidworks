// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.taking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * The 12 masks Voidworks ships ({@link TakingMask#path}): each a block texture's size, each painting
 * all the one before it painted and more, so the void only ever grows, and the last painting the
 * whole face.
 */
class ShippedMasksTest {

    @Test
    void eachMaskIsSixteenPixelsSquare() throws IOException {
        for (int stage = 1; stage <= TakingStages.STAGES; stage++) {
            var image = load(stage);
            assertEquals(TakingMask.SIZE, image.getWidth(), "stage " + stage);
            assertEquals(TakingMask.SIZE, image.getHeight(), "stage " + stage);
        }
    }

    @Test
    void eachMaskPaintsMoreThanTheOneBefore() throws IOException {
        var masks = painted();
        for (int stage = 1; stage < masks.size(); stage++) {
            int before = count(masks.get(stage - 1));
            int now = count(masks.get(stage));
            assertTrue(now > before, "stage " + (stage + 1) + " paints " + now + " pixels, stage " + stage + " " + before);
            for (int pixel = 0; pixel < now; pixel++) {
                assertTrue(!masks.get(stage - 1)[pixel] || masks.get(stage)[pixel],
                        "stage " + (stage + 1) + " leaves out pixel " + pixel + ", which stage " + stage + " paints");
            }
        }
    }

    @Test
    void theLastMaskPaintsTheWholeFace() throws IOException {
        assertEquals(TakingMask.SIZE * TakingMask.SIZE, count(painted().getLast()));
    }

    private static List<boolean[]> painted() throws IOException {
        var masks = new ArrayList<boolean[]>();
        for (int stage = 1; stage <= TakingStages.STAGES; stage++) {
            var image = load(stage);
            var mask = new boolean[TakingMask.SIZE * TakingMask.SIZE];
            for (int y = 0; y < TakingMask.SIZE; y++) {
                for (int x = 0; x < TakingMask.SIZE; x++) {
                    mask[y * TakingMask.SIZE + x] = TakingMask.paints(image.getRGB(x, y));
                }
            }
            masks.add(mask);
        }
        return masks;
    }

    private static BufferedImage load(int stage) throws IOException {
        var path = "/assets/" + TakingMask.path(stage).getNamespace() + "/" + TakingMask.path(stage).getPath();
        try (var in = ShippedMasksTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "no mask at " + path);
            return ImageIO.read(in);
        }
    }

    private static int count(boolean[] mask) {
        int count = 0;
        for (boolean paints : mask) {
            count += paints ? 1 : 0;
        }
        return count;
    }
}
