// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** What a dragon's presence hangs under its body of what it carries: a Matter-line load's first stack, and nothing of motes. */
class LoadTest {

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier SHULKER_BOX = Identifier.withDefaultNamespace("shulker_box");

    @Test
    void motesHangNothing() {
        assertEquals(Optional.empty(), MoteLoad.of(Map.of(4, 1_000L)).firstStack());
    }

    @Test
    void nothingCarriedHangsNothing() {
        assertEquals(Optional.empty(), MatterLoad.NONE.firstStack());
    }

    @Test
    void itHangsOneStackOfWhatItTookFirst() {
        var load = new MatterLoad(List.of(Ware.of(COBBLESTONE, 64, 200), Ware.of(SHULKER_BOX, 1, 1)));
        assertEquals(Optional.of(Ware.of(COBBLESTONE, 64, 64)), load.firstStack());
    }

    @Test
    void aPartStackHangsAsItIs() {
        var load = new MatterLoad(List.of(Ware.of(COBBLESTONE, 64, 10)));
        assertEquals(Optional.of(Ware.of(COBBLESTONE, 64, 10)), load.firstStack());
    }

    @Test
    void aFilledShulkerBoxHangsWithItsContents() {
        var contents = new CompoundTag();
        contents.putString("minecraft:container", "full");
        var box = new Ware(SHULKER_BOX, Optional.of(contents), 1, 1);
        assertEquals(Optional.of(box), new MatterLoad(List.of(box, Ware.of(COBBLESTONE, 64, 64))).firstStack());
    }
}
