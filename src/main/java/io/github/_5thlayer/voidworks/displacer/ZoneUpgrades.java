// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.energy.TouchingMotes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A Void Displacer's Zone upgrades: how many levels it has bought, the one it is soaking, if any,
 * and the quote a player is waiting to confirm. A committed upgrade soaks its price as a Dormant
 * machine soaks its cost, from the stores touching the Displacer, once every
 * {@link VoidworksConfig#soakInterval()} ticks, and a payment in part is kept across a save. Its
 * price and step are fixed when it is committed, so a datapack reloaded meanwhile changes only the
 * levels after it.
 */
final class ZoneUpgrades {

    private static final String KEY = "zone_upgrades";
    private static final String LEVELS_KEY = "levels";
    private static final String SOAKING_KEY = "soaking";
    private static final String PRICE_KEY = "price";
    private static final String PAID_KEY = "paid";

    private final BlockEntity displacer;
    /** Held only while the game runs. */
    private final Confirmation confirmation = new Confirmation();
    private int levels;
    private @Nullable Soaking soaking;
    private int sinceSoak;

    ZoneUpgrades(BlockEntity displacer) {
        this.displacer = displacer;
    }

    /**
     * The level it has committed to and is soaking.
     *
     * @param upgrade its terms when it was committed, which raise the Zone once it is paid
     * @param paid    the void energy soaked so far, which may pass the price by part of a mote
     */
    private record Soaking(ZoneUpgrade upgrade, long price, long paid) {

        private static final Codec<Soaking> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ZoneUpgrade.CODEC.fieldOf("upgrade").forGetter(Soaking::upgrade),
                Codec.LONG.fieldOf(PRICE_KEY).forGetter(Soaking::price),
                Codec.LONG.fieldOf(PAID_KEY).forGetter(Soaking::paid)
        ).apply(instance, Soaking::new));

        DisplacerBlockEntity.Payment payment() {
            return new DisplacerBlockEntity.Payment(Math.min(paid, price), price);
        }
    }

    /** @return how many levels it has bought */
    int levels() {
        return levels;
    }

    /** @return what the level it is soaking has paid of its price, or empty while none is */
    Optional<DisplacerBlockEntity.Payment> payment() {
        return soaking == null ? Optional.empty() : Optional.of(soaking.payment());
    }

    /**
     * A player's sneak-use: while a level soaks, a first offers to cancel it and the same player's
     * second within {@link Confirmation#TICKS} ticks cancels it, what it paid spent; otherwise a
     * first quotes the next level and such a second commits to it, so long as the Displacer's block
     * buys any, it can still reach something to harvest, and the networks touching it are worth the
     * price at its block.
     *
     * @param zone      its Zone now
     * @param reachable whether it can still reach something to harvest
     * @return what the player is told, above the hotbar
     */
    Component sneakUse(ServerLevel level, Player player, int zone, boolean reachable) {
        long now = level.getGameTime();
        if (soaking != null) {
            if (confirmation.confirms(player.getUUID(), Confirmation.Offer.CANCEL, now)) {
                cancel();
                return Component.translatable("voidworks.displacer.upgrade.cancelled");
            }
            confirmation.arm(player.getUUID(), Confirmation.Offer.CANCEL, now);
            var payment = soaking.payment();
            return Component.translatable("voidworks.displacer.upgrade.cancel", payment.paid(), payment.price());
        }
        var upgrade = ZoneUpgrade.of(displacer.getBlockState());
        if (upgrade.isEmpty()) {
            confirmation.lapse();
            return Component.translatable("voidworks.displacer.upgrade.none");
        }
        if (!reachable) {
            confirmation.lapse();
            return Component.translatable("voidworks.displacer.upgrade.unreachable");
        }
        int to = upgrade.get().raise(zone);
        long price = upgrade.get().price(levels);
        boolean confirmed = confirmation.confirms(player.getUUID(), Confirmation.Offer.BUY, now);
        long worth = TouchingMotes.networkWorth(level, displacer.getBlockPos());
        if (worth < price) {
            return Component.translatable("voidworks.displacer.upgrade.unaffordable", zone, to, price, worth);
        }
        if (confirmed) {
            soaking = new Soaking(upgrade.get(), price, 0);
            sinceSoak = 0;
            displacer.setChanged();
            return Component.translatable("voidworks.displacer.upgrade.committed", zone, to, price);
        }
        confirmation.arm(player.getUUID(), Confirmation.Offer.BUY, now);
        return Component.translatable("voidworks.displacer.upgrade.quote", zone, to, price);
    }

    /** Drops any quote waiting on a confirmation, as any use but a sneak-use does. */
    void lapse() {
        confirmation.lapse();
    }

    private void cancel() {
        soaking = null;
        displacer.setChanged();
    }

    /**
     * Soaks what it can of the soaking level's price from the stores touching the Displacer, once
     * every soak interval. Called each server tick while the Displacer is awake.
     *
     * @param zone the Displacer's Zone now
     * @return its Zone raised by the level, once the level is paid and bought; empty until then, or while none soaks
     */
    OptionalInt soak(ServerLevel level, int zone) {
        if (soaking == null) {
            return OptionalInt.empty();
        }
        sinceSoak++;
        if (sinceSoak < VoidworksConfig.soakInterval() && soaking.paid() < soaking.price()) {
            return OptionalInt.empty();
        }
        sinceSoak = 0;
        long owed = soaking.price() - soaking.paid();
        if (owed > 0) {
            long soaked = TouchingMotes.soak(level, displacer.getBlockPos(), Set.of(), owed);
            if (soaked > 0) {
                soaking = new Soaking(soaking.upgrade(), soaking.price(), soaking.paid() + soaked);
                displacer.setChanged();
            }
        }
        if (soaking.paid() < soaking.price()) {
            return OptionalInt.empty();
        }
        int raised = soaking.upgrade().raise(zone);
        levels++;
        cancel();
        return OptionalInt.of(raised);
    }

    void save(ValueOutput output) {
        var child = output.child(KEY);
        child.putInt(LEVELS_KEY, levels);
        child.storeNullable(SOAKING_KEY, Soaking.CODEC, soaking);
    }

    void load(ValueInput input) {
        var child = input.childOrEmpty(KEY);
        levels = child.getIntOr(LEVELS_KEY, 0);
        soaking = child.read(SOAKING_KEY, Soaking.CODEC).orElse(null);
    }
}
