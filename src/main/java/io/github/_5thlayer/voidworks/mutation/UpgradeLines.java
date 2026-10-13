// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The lines every upgrading block, the Mutation Chamber and the Void Crucible, shows in Jade alike:
 * that it holds nothing, and the stalls of what it holds before it works. Its report words the rest.
 */
final class UpgradeLines {

    private UpgradeLines() {
    }

    /** @return that it holds nothing to upgrade, in gray */
    static Component empty() {
        return Component.translatable("voidworks.jade.upgrade.empty").withStyle(ChatFormatting.GRAY);
    }

    /** @return that what it holds climbs no Family */
    static MutableComponent noUpgrade() {
        return Component.translatable("voidworks.jade.upgrade.stall.no_upgrade");
    }

    /** @return that it waits for {@code needed} more items */
    static MutableComponent tooFew(int needed) {
        return Component.translatable("voidworks.jade.upgrade.stall.too_few", needed);
    }
}
