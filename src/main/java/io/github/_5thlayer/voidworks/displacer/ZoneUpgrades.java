// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.Optional;
import java.util.Set;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.energy.TouchingMotes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Void Displacer's Zone upgrades: how many levels it has bought, and the one it is soaking, if
 * any. A committed upgrade soaks its price as a Dormant machine soaks its cost, from the stores
 * touching the Displacer, once every {@link VoidworksConfig#soakInterval()} ticks, and a payment in
 * part is kept across a save. Its price and step are fixed when it is committed, so a datapack
 * reloaded meanwhile changes only the levels after it.
 */
final class ZoneUpgrades {

    private static final String KEY = "zone_upgrades";
    private static final String LEVELS_KEY = "levels";
    private static final String STEP_KEY = "step";
    private static final String PRICE_KEY = "price";
    private static final String PAID_KEY = "paid";

    /** The quote a player is waiting to confirm, held only while the game runs. */
    final Confirmation confirmation = new Confirmation();

    private final BlockEntity displacer;
    private int levels;
    /** The soaking level's step, 0 while none soaks. */
    private int step;
    private long price;
    private long paid;
    private int sinceSoak;

    ZoneUpgrades(BlockEntity displacer) {
        this.displacer = displacer;
    }

    /** @return how many levels it has bought */
    int levels() {
        return levels;
    }

    /** @return the level it is soaking, or empty while none is */
    Optional<Soaking> soaking() {
        return step == 0 ? Optional.empty() : Optional.of(new Soaking(step, price, Math.min(paid, price)));
    }

    /**
     * A committed upgrade, soaking its price.
     *
     * @param step the positions it adds to the Zone once paid
     * @param paid the void energy soaked so far, at most the price
     */
    record Soaking(int step, long price, long paid) {
    }

    /** Commits to the next level, by {@code upgrade}'s terms: its step and the price of the level after those bought. */
    void commit(ZoneUpgrade upgrade) {
        step = upgrade.step();
        price = upgrade.price(levels);
        paid = 0;
        sinceSoak = 0;
        displacer.setChanged();
    }

    /** Drops the level it is soaking; what it paid is spent. */
    void cancel() {
        step = 0;
        price = 0;
        paid = 0;
        displacer.setChanged();
    }

    /**
     * Soaks what it can of the soaking level's price from the stores touching the Displacer, once every
     * soak interval. Called each server tick while the Displacer is awake.
     *
     * @return the step the Zone grows by, once the level is paid and bought; empty until then, or while none soaks
     */
    Optional<Integer> soak(ServerLevel level) {
        if (step == 0 || ++sinceSoak < VoidworksConfig.soakInterval() && paid < price) {
            return Optional.empty();
        }
        sinceSoak = 0;
        if (paid < price) {
            long soaked = TouchingMotes.soak(level, displacer.getBlockPos(), Set.of(), price - paid);
            if (soaked > 0) {
                paid += soaked;
                displacer.setChanged();
            }
        }
        if (paid < price) {
            return Optional.empty();
        }
        int bought = step;
        levels++;
        cancel();
        return Optional.of(bought);
    }

    void save(ValueOutput output) {
        var child = output.child(KEY);
        child.putInt(LEVELS_KEY, levels);
        child.putInt(STEP_KEY, step);
        child.putLong(PRICE_KEY, price);
        child.putLong(PAID_KEY, paid);
    }

    void load(ValueInput input) {
        var child = input.childOrEmpty(KEY);
        levels = child.getIntOr(LEVELS_KEY, 0);
        step = child.getIntOr(STEP_KEY, 0);
        price = child.getLongOr(PRICE_KEY, 0);
        paid = child.getLongOr(PAID_KEY, 0);
    }
}
