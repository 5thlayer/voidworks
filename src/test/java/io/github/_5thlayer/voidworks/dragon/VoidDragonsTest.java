// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The Void Dragons, server state rather than entities (ADR 0005): each a record of its owner, its
 * health and where it rests, kept across a save and reload.
 */
class VoidDragonsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final Place HERE = new Place(Identifier.withDefaultNamespace("overworld"), new BlockPos(1, 64, 2));
    private static final Place THERE = new Place(Identifier.withDefaultNamespace("the_end"), new BlockPos(-5, 70, 9));

    private final VoidDragons dragons = new VoidDragons();

    @Test
    void aDragonGrownUpRestsWhereItGrewAtFullHealth() {
        var dragon = dragons.add(ALICE, HERE);
        assertEquals(ALICE, dragon.owner());
        assertEquals(HERE, dragon.rest());
        assertEquals(VoidDragonRecord.FULL_HEALTH, dragon.health());
        assertEquals(dragon, dragons.get(dragon.id()).orElseThrow());
    }

    @Test
    void eachDragonIsItsOwn() {
        var first = dragons.add(ALICE, HERE);
        var second = dragons.add(ALICE, HERE);
        assertNotEquals(first.id(), second.id());
        assertEquals(List.of(first, second).size(), dragons.all().size());
    }

    @Test
    void aSaveAndReloadKeepsEveryDragon() {
        var first = dragons.add(ALICE, HERE);
        var second = dragons.add(UUID.randomUUID(), THERE);
        var saved = VoidDragons.CODEC.encodeStart(JsonOps.INSTANCE, dragons).getOrThrow();
        var read = VoidDragons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(first, read.get(first.id()).orElseThrow());
        assertEquals(second, read.get(second.id()).orElseThrow());
        assertEquals(2, read.all().size());
    }

    @Test
    void anAddIsReported() {
        int[] changes = {0};
        dragons.onChange(() -> changes[0]++);
        dragons.add(ALICE, HERE);
        assertTrue(changes[0] == 1);
    }
}
