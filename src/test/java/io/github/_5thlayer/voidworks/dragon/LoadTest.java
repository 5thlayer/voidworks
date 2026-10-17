// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * What a dragon's presence hangs under its body of what it carries: a Matter-line load's first stack, and
 * nothing of motes; and how a load is saved.
 */
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

    @Test
    void aMatterLineLoadIsSavedWithItsTollAndWhatItsContainersHold() {
        var box = new Ware(SHULKER_BOX, Optional.empty(), 1, 1, new TreeMap<>(Map.of(64, 27 * 64L)));
        var load = new MatterLoad(List.of(Ware.of(COBBLESTONE, 64, 200), box), 7_168);
        var saved = Load.CODEC.encodeStart(JsonOps.INSTANCE, load).getOrThrow();
        assertEquals(load, Load.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
        var motes = MoteLoad.of(Map.of(4, 1_000L));
        assertEquals(motes, Load.CODEC.parse(JsonOps.INSTANCE, Load.CODEC.encodeStart(JsonOps.INSTANCE, motes).getOrThrow()).getOrThrow());
    }

    @Test
    void aMatterLineLoadSavedBeforeTheTollReadsAsHavingPaidNone() {
        var saved = JsonParser.parseString("[{\"item\": \"minecraft:cobblestone\", \"stack_size\": 64, \"count\": 10}]");
        assertEquals(new MatterLoad(List.of(Ware.of(COBBLESTONE, 64, 10))), Load.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
    }

    @Test
    void itKeepsItsTollAsItsItemsGoButPaidNoneOnceTheyAreAllGone() {
        var load = new MatterLoad(List.of(Ware.of(COBBLESTONE, 64, 128)), 512);
        assertEquals(512, load.less(Ware.of(COBBLESTONE, 64, 64)).toll());
        assertEquals(0, load.less(Ware.of(COBBLESTONE, 64, 128)).toll());
    }
}
