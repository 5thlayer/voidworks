// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.MatterLoad;
import io.github._5thlayer.voidworks.dragon.MoteLoad;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.Showing;
import io.github._5thlayer.voidworks.dragon.StackWares;
import io.github._5thlayer.voidworks.dragon.Trip;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.dragon.VoidDragonRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A dragon shows its Line by what it carries: a Matter-line dragon's presence hangs its first carried
 * stack under its body, a filled shulker box with its contents, whatever it is doing, and nothing while
 * it carries nothing; a Matter-line Dragonling holds a chest, and a Mote-line one nothing. What it
 * hangs is in its synced data, so a client sees it too.
 */
final class HangingTests {

    /** Where the dragon rests, on no Well, and the Dragonlings hatch. */
    private static final BlockPos HERE = new BlockPos(4, 2, 4);

    private HangingTests() {
    }

    static void register(VoidworksGameTests.Registrar tests) {
        tests.test("hanging_a_matter_line_dragons_presence_hangs_its_first_stack_resting_working_and_diving_and_nothing_when_empty", 1,
                HangingTests::hangsItsFirstStack);
        tests.test("hanging_a_matter_line_dragonling_holds_a_chest_across_a_save_and_a_mote_line_one_nothing", 1, HangingTests::dragonlingsChest);
    }

    private static void hangsItsFirstStack(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var registries = helper.getLevel().registryAccess();
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND_BLOCK, 64))));
        var load = new MatterLoad(List.of(StackWares.of(box, registries), StackWares.of(new ItemStack(Items.COBBLESTONE, 64), registries)));
        var dragons = SavedVoidDragons.of(server);
        var here = Place.of(helper.getLevel(), helper.absolutePos(HERE));
        var dragon = dragons.add(UUID.randomUUID(), Line.MATTER, here);
        var id = dragon.id();

        DragonPresences.show(server);
        var presence = DragonPresences.presence(server, id).orElseThrow();
        helper.assertTrue(presence.hung().isEmpty(), "the dragon carrying nothing hangs " + presence.hung());

        DragonLoads.carrying(dragons, dragon, Line.MATTER, load, Optional.empty());
        DragonPresences.show(server);
        assertHangs(helper, presence, box, Showing.RESTING);

        long now = server.overworld().getGameTime();
        int stop = VoidworksConfig.dragonStopTicks();
        DragonLoads.carrying(dragons, dragon, Line.MATTER, load, Optional.of(DragonLoads.trip(here, load, Trip.Leg.LOADING, now + stop - Showing.EMERGE_TICKS - 1)));
        DragonPresences.show(server);
        assertHangs(helper, presence, box, Showing.WORKING);

        DragonLoads.carrying(dragons, dragon, Line.MATTER, load, Optional.of(DragonLoads.trip(here, load, Trip.Leg.TO_DESTINATION, now + 1_000)));
        DragonPresences.show(server);
        assertHangs(helper, presence, box, Showing.DIVING);

        // Taken away, a presence shown anew hangs the load at once.
        DragonLoads.carrying(dragons, dragon, Line.MATTER, load, Optional.empty());
        presence.discard();
        DragonPresences.show(server);
        DragonPresences.show(server);
        var anew = DragonPresences.presence(server, id).orElseThrow();
        helper.assertTrue(anew != presence, "the presence taken away is still shown");
        assertHangs(helper, anew, box, Showing.RESTING);

        DragonLoads.carrying(dragons, dragon, Line.MATTER, MatterLoad.NONE, Optional.empty());
        DragonPresences.show(server);
        helper.assertTrue(anew.hung().isEmpty(), "the dragon having unloaded still hangs " + anew.hung());
        helper.assertTrue(DragonClients.seen(helper, anew).hung().isEmpty(), "a client sees the empty dragon hang " + DragonClients.seen(helper, anew).hung());

        // A Mote-line dragon carrying motes hangs nothing.
        dragons.update(new VoidDragonRecord(id, dragon.owner(), Line.MOTE, dragon.health(), here, MoteLoad.of(Map.of(4, 100L)), Optional.empty(), 0, 0));
        DragonPresences.show(server);
        helper.assertTrue(anew.hung().isEmpty(), "a Mote-line dragon hangs " + anew.hung());
        dragons.remove(id);
        DragonPresences.show(server);
        helper.succeed();
    }

    private static void dragonlingsChest(GameTestHelper helper) {
        var matter = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
        matter.hatchFor(UUID.randomUUID(), Line.MATTER);
        helper.assertTrue(matter.hung().is(Items.CHEST), "a Matter-line Dragonling holds " + matter.hung());
        helper.assertTrue(DragonClients.seen(helper, matter).hung().is(Items.CHEST), "a client sees the Matter-line Dragonling hold no chest");
        var mote = helper.spawn(VoidworksEntities.VOID_DRAGON.get(), HERE);
        mote.hatchFor(UUID.randomUUID(), Line.MOTE);
        helper.assertTrue(mote.hung().isEmpty(), "a Mote-line Dragonling holds " + mote.hung());
        helper.assertTrue(DragonSaves.reloaded(helper, matter).hung().is(Items.CHEST), "a Matter-line Dragonling reloads holding no chest");
        helper.assertTrue(DragonSaves.reloaded(helper, mote).hung().isEmpty(), "a Mote-line Dragonling reloads holding something");
        matter.discard();
        mote.discard();
        helper.succeed();
    }

    /** The presence shows {@code showing} and hangs {@code stack}, and a client given its synced data sees it so. */
    private static void assertHangs(GameTestHelper helper, VoidDragon presence, ItemStack stack, Showing showing) {
        helper.assertTrue(presence.showing() == showing, "the presence shows " + presence.showing() + ", not " + showing);
        helper.assertTrue(ItemStack.isSameItemSameComponents(presence.hung(), stack),
                "the presence " + showing + " hangs " + presence.hung() + " holding " + presence.hung().get(DataComponents.CONTAINER) + ", not " + stack);
        var seen = DragonClients.seen(helper, presence).hung();
        helper.assertTrue(ItemStack.isSameItemSameComponents(seen, stack), "a client sees the presence " + showing + " hang " + seen + ", not " + stack);
    }
}
