// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * What a Void Dragon's rider hears through its dive: a Nether portal's hum, rising in pitch and
 * loudness as the dive goes, toward emerging. It plays on their own client, for them alone, and stops
 * once they are out of the dive.
 */
final class DiveSound extends AbstractTickableSoundInstance {

    private static final float QUIET = 0.2F;
    private static final float LOW = 0.5F;
    private static final float HIGH = 1.5F;

    /** The hum playing now, while its rider is in a dive. */
    private static DiveSound playing;

    private final LocalPlayer player;

    private DiveSound(LocalPlayer player) {
        super(SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        looping = true;
        delay = 0;
        relative = true;
        attenuation = Attenuation.NONE;
        follow();
    }

    /** Each client tick: a rider in a dive with no hum playing starts one. */
    static void tickClient() {
        var player = Minecraft.getInstance().player;
        if (player != null && dive(player) != null && (playing == null || playing.isStopped())) {
            playing = new DiveSound(player);
            Minecraft.getInstance().getSoundManager().play(playing);
        }
    }

    @Override
    public void tick() {
        if (player.isRemoved() || dive(player) == null) {
            stop();
            return;
        }
        follow();
    }

    /** Rises with the dive's progress. */
    private void follow() {
        var presence = dive(player);
        float progress = presence == null ? 0.0F : presence.diveShown().progress(player.level().getGameTime(), 0.0F);
        volume = Mth.lerp(progress, QUIET, 1.0F);
        pitch = Mth.lerp(progress, LOW, HIGH);
    }

    /** @return the dragon carrying {@code player} through its dive, if one is */
    private static VoidDragon dive(LocalPlayer player) {
        return player.getVehicle() instanceof VoidDragon presence && presence.carrying() ? presence : null;
    }
}
