// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github._5thlayer.voidworks.well.WellStructure.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The Void Well's Groundworks Assembly, a second statement of its shape, says what {@link
 * WellStructure#layout} says: placed from its core, facing any way, it stands a whole Well.
 *
 * <p>The declaration is read as Groundworks reads it ({@code AssemblyShape}): layers bottom first,
 * each drawn as a player at the bottom of the page sees it looking up the page, turned into the
 * Key's frame (forward {@code x}, the player's left {@code z}) and that frame into the world by the
 * Key's facing as {@code FootprintShape.Local#inWorld} does, as of Groundworks 0.5.12. No Groundworks
 * type is named.
 */
class WellAssemblyTest {

    private static final String DECLARATION = "/data/voidworks/groundworks/assembly/void_well.json";
    private static final BlockPos CORE = new BlockPos(10, 66, 10);

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void placedFromItsCoreItStandsTheWellsLayout(Direction facing) throws Exception {
        assertEquals(WellStructure.layout(CORE, facing), placed(CORE, facing));
    }

    /** What each position the Assembly places from a Key at {@code core} holds, the Key's own left out. */
    private static Map<BlockPos, Part> placed(BlockPos core, Direction facing) throws Exception {
        var declaration = declaration();
        char key = declaration.get("key").getAsString().charAt(0);
        var legend = declaration.getAsJsonObject("legend");
        assertEquals("voidworks:void_well", legend.get(String.valueOf(key)).getAsString());
        var drawn = drawn(declaration);
        var keys = drawn.entrySet().stream().filter(entry -> entry.getValue() == key).map(Map.Entry::getKey).toList();
        assertEquals(1, keys.size(), "the shape holds its Key once");
        var at = keys.getFirst();

        var placed = new HashMap<BlockPos, Part>();
        drawn.forEach((spot, letter) -> {
            if (letter != key) {
                var offset = inWorld(at.row() - spot.row(), spot.layer() - at.layer(), at.column() - spot.column(), facing);
                placed.put(core.offset(offset), part(legend.get(String.valueOf(letter)).getAsString()));
            }
        });
        return placed;
    }

    /** Where a letter is drawn: its layer from the bottom, its row from the top of the page, its column from the left. */
    private record Spot(int layer, int row, int column) {
    }

    private static JsonObject declaration() throws Exception {
        try (var in = WellAssemblyTest.class.getResourceAsStream(DECLARATION)) {
            assertNotNull(in, DECLARATION);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Every letter the layers draw, spaces left out, by where it is drawn. */
    private static Map<Spot, Character> drawn(JsonObject declaration) {
        var layers = declaration.getAsJsonArray("layers");
        var drawn = new HashMap<Spot, Character>();
        for (int layer = 0; layer < layers.size(); layer++) {
            var rows = layers.get(layer).getAsJsonArray();
            for (int row = 0; row < rows.size(); row++) {
                String letters = rows.get(row).getAsString();
                for (int column = 0; column < letters.length(); column++) {
                    if (letters.charAt(column) != ' ') {
                        drawn.put(new Spot(layer, row, column), letters.charAt(column));
                    }
                }
            }
        }
        return drawn;
    }

    private static Part part(String block) {
        return switch (block) {
            case "voidworks:void_well_casing" -> Part.CASING;
            case "voidworks:void_well_rim" -> Part.RIM;
            case "empty" -> Part.OPEN;
            default -> throw new AssertionError("not a part of the Well: " + block);
        };
    }

    /** Groundworks' turn of an offset in the Key's frame into the world, for a Key facing this way. */
    private static BlockPos inWorld(int x, int y, int z, Direction facing) {
        return switch (facing) {
            case NORTH -> new BlockPos(z, y, x);
            case WEST -> new BlockPos(x, y, -z);
            case SOUTH -> new BlockPos(-z, y, -x);
            case EAST -> new BlockPos(-x, y, z);
            default -> throw new IllegalArgumentException(facing.toString());
        };
    }
}
