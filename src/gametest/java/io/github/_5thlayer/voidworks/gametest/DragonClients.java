// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;

/** A Void Dragon as a client sees it: given only its synced data, as a client joining is, never added to the world. */
final class DragonClients {

    private DragonClients() {
    }

    /** @return a dragon holding only what {@code dragon}'s synced data gives a client */
    static VoidDragon seen(GameTestHelper helper, VoidDragon dragon) {
        var client = VoidworksEntities.VOID_DRAGON.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        var values = dragon.getEntityData().getNonDefaultValues();
        if (values != null) {
            client.getEntityData().assignValues(values);
        }
        return client;
    }
}
