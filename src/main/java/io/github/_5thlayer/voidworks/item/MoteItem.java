// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.item;

import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
     * The grade of the motes in {@code stack}, which must be a stack of motes: a mote nothing has
     * graded has {@link VoidEnergy#MIN_GRADE}.
     */
    public static int gradeOf(ItemStack stack) {
        return stack.getOrDefault(VoidworksDataComponents.GRADE.get(), VoidEnergy.MIN_GRADE);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId() + ".graded", gradeOf(stack));
    }
}
