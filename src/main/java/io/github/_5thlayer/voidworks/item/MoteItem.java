// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The mote, the unit of void. Its grade is a data component on the stack, so motes of one grade
 * stack and motes of different grades never merge: the game merges stacks only when their
 * components are equal.
 */
public final class MoteItem extends Item {

    /** The grade of a mote nothing has graded: the bare item, as {@code /give} makes it. */
    public static final int LOWEST_GRADE = 1;

    public MoteItem(Properties properties) {
        super(properties);
    }

    /** A stack of {@code count} motes of {@code grade}. */
    public static ItemStack stack(int grade, int count) {
        var stack = new ItemStack(VoidworksItems.MOTE.get(), count);
        stack.set(VoidworksDataComponents.GRADE.get(), grade);
        return stack;
    }

    /** The grade of the motes in {@code stack}, which must be a stack of motes. */
    public static int gradeOf(ItemStack stack) {
        return stack.getOrDefault(VoidworksDataComponents.GRADE.get(), LOWEST_GRADE);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId() + ".graded", gradeOf(stack));
    }
}
