// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.siphon;

import io.github._5thlayer.voidworks.VoidworksItems;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A Void Siphon's motes as a mote store, through the {@link ItemAccess} that holds the Siphon: one
 * slot per grade, from {@link VoidEnergy#MIN_GRADE} up, sharing the Siphon's capacity, so a spend,
 * a charge and an emptying reach it like any other store. Its changes go to the Siphon's
 * {@code voidworks:siphon_motes} when their transaction commits.
 *
 * <p>No block offers it, as no block may charge or empty a Siphon (ADR 0002): only the Siphon's
 * holder does, by harvesting, standing on Voidstone, or paying a Dormant machine.
 */
final class SiphonMoteHandler extends ItemAccessResourceHandler<MoteResource> {

    private static final int SLOTS = VoidEnergy.MAX_GRADE - VoidEnergy.MIN_GRADE + 1;

    private final long capacity;

    /**
     * @param siphon where the Siphon is, such as {@code ItemAccess.forPlayerSlot(player, slot)}
     * @param capacity how many motes the Siphon holds, of every grade together, at most
     */
    SiphonMoteHandler(ItemAccess siphon, long capacity) {
        super(siphon, SLOTS);
        this.capacity = capacity;
    }

    private static SiphonMotes motes(ItemResource siphon) {
        return siphon.getOrDefault(VoidworksItems.SIPHON_MOTES.get(), SiphonMotes.EMPTY);
    }

    private static int grade(int index) {
        return index + VoidEnergy.MIN_GRADE;
    }

    @Override
    protected MoteResource getResourceFrom(ItemResource accessResource, int index) {
        return motes(accessResource).count(grade(index)) > 0 ? MoteResource.of(grade(index)) : MoteResource.EMPTY;
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return motes(accessResource).count(grade(index));
    }

    @Override
    protected ItemResource update(ItemResource accessResource, int index, MoteResource newResource, int newAmount) {
        return accessResource.with(VoidworksItems.SIPHON_MOTES.get(), motes(accessResource).with(grade(index), newAmount));
    }

    @Override
    public boolean isValid(int index, MoteResource resource) {
        return itemAccess.getResource().getItem() instanceof VoidSiphonItem && resource.grade() == grade(index);
    }

    /** What the slot holds and the room the Siphon has left, which every grade shares. */
    @Override
    protected int getCapacity(int index, MoteResource resource) {
        var motes = motes(itemAccess.getResource());
        long room = Math.max(0, capacity - motes.total());
        return (int) Math.min(Integer.MAX_VALUE, motes.count(grade(index)) + room);
    }
}
