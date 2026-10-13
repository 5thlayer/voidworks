// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.network;

import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.butterfly.MoteCrawl;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Runs conduction in every dimension: a step of {@link VoidNetworks#conduct} once every
 * {@link VoidworksConfig#conductionInterval()} ticks, its motes seen walking where a player is near.
 * Motes conduct in unloaded chunks too, since a network is saved data, not blocks.
 */
public final class Conduction {

    private Conduction() {
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % VoidworksConfig.conductionInterval() == 0) {
            step(level);
        }
    }

    /** One step of conduction in {@code level}'s dimension. */
    public static void step(ServerLevel level) {
        for (var flow : VoidstoneNetworks.of(level).conduct(VoidworksConfig.conductionRate())) {
            if (level.isLoaded(flow.from())) {
                MoteCrawl.between(level, flow.from(), flow.to(), flow.grade(), flow.count());
            }
        }
    }
}
