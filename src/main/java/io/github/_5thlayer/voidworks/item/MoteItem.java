// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.item;

import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.primitives.Ints;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The mote, the unit of void. Its grade is a data component on the stack, so motes of one grade
 * stack and motes of different grades never merge: the game merges stacks only when their
 * components are equal.
 */
public final class MoteItem extends Item {

    public MoteItem(Properties properties) {
        super(properties);
    }

    /**
     * A stack of {@code count} motes of {@code grade}.
     *
     * @throws IllegalArgumentException when no mote has {@code grade}, outside
     *         {@link VoidEnergy#MIN_GRADE} to {@link VoidEnergy#MAX_GRADE}
     */
    public static ItemStack stack(int grade, int count) {
        if (grade < VoidEnergy.MIN_GRADE || grade > VoidEnergy.MAX_GRADE) {
            throw new IllegalArgumentException("no mote has grade " + grade + ": grades run from "
                    + VoidEnergy.MIN_GRADE + " to " + VoidEnergy.MAX_GRADE);
        }
        var stack = new ItemStack(VoidworksItems.MOTE.get(), count);
        stack.set(VoidworksDataComponents.GRADE.get(), grade);
        return stack;
    }

    /**
     * The grade of {@code motes}, a stack or an inventory's resource that must be motes: a mote
     * nothing has graded has {@link VoidEnergy#MIN_GRADE}.
     */
    public static int gradeOf(DataComponentHolder motes) {
        return motes.getOrDefault(VoidworksDataComponents.GRADE.get(), VoidEnergy.MIN_GRADE);
    }

    /**
     * The motes in {@code inventory}, any NeoForge item inventory, as grade to count, lowest grade
     * first; whatever is not a mote is left out. A count past {@link Integer#MAX_VALUE} stays there.
     */
    public static SortedMap<Integer, Integer> countByGrade(ResourceHandler<ItemResource> inventory) {
        var counts = new TreeMap<Integer, Integer>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var resource = inventory.getResource(slot);
            if (resource.is(VoidworksItems.MOTE.get())) {
                counts.merge(gradeOf(resource), inventory.getAmountAsInt(slot), (a, b) -> Ints.saturatedCast((long) a + b));
            }
        }
        return counts;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId() + ".graded", gradeOf(stack));
    }
}
