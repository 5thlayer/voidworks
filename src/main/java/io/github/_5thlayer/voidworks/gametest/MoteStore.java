// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.energy.MoteResource;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.ResourceStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A plain mote store for the tests, standing in for a void machine's: nine slots, each holding up to
 * {@link #CAPACITY} motes of one grade.
 */
final class MoteStore extends ResourceStacksResourceHandler<MoteResource> {

    static final int CAPACITY = 1000;

    private static final Codec<ResourceStack<MoteResource>> STACK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MoteResource.CODEC.fieldOf("resource").forGetter(ResourceStack::resource),
            Codec.INT.fieldOf("amount").forGetter(ResourceStack::amount)
    ).apply(instance, ResourceStack::new));

    MoteStore() {
        super(9, MoteResource.EMPTY, STACK_CODEC);
    }

    @Override
    protected int getCapacity(int index, MoteResource resource) {
        return CAPACITY;
    }

    /** Puts {@code count} motes of {@code grade} in the store, stacking them with any of that grade. */
    MoteStore add(int grade, int count) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(this, MoteResource.of(grade), count, transaction);
            if (inserted != count) {
                throw new IllegalStateException("the store took " + inserted + " of " + count + " grade " + grade + " motes");
            }
            transaction.commit();
        }
        return this;
    }

    /** What each slot holds, to compare before and after. */
    List<ResourceStack<MoteResource>> snapshot() {
        var slots = new ArrayList<ResourceStack<MoteResource>>();
        for (int slot = 0; slot < size(); slot++) {
            slots.add(new ResourceStack<>(getResource(slot), getAmountAsInt(slot)));
        }
        return slots;
    }
}
