// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** A Void Dragon saved as its chunk saves it, and read back as its chunk loads it, never added to the world. */
final class DragonSaves {

    private DragonSaves() {
    }

    /** @return {@code dragon} as it is saved */
    static CompoundTag saved(GameTestHelper helper, VoidDragon dragon) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        dragon.saveWithoutId(output);
        return output.buildResult();
    }

    /** @return a dragon read from {@code saved} */
    static VoidDragon read(GameTestHelper helper, CompoundTag saved) {
        var reloaded = VoidworksEntities.VOID_DRAGON.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        return reloaded;
    }

    /** @return {@code dragon} saved and read back */
    static VoidDragon reloaded(GameTestHelper helper, VoidDragon dragon) {
        return read(helper, saved(helper, dragon));
    }
}
