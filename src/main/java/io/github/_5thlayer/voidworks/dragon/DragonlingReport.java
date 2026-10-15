// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;

/**
 * What a Dragonling shows a player looking at it, in Jade: how far it has grown up, and its owner.
 * Read on the server, where its time left to grow is, and sent to the client, which words it with
 * {@link #lines}.
 *
 * @param growth how far it has grown, in whole percent of its growing, rounded down
 * @param owned whether a player owns it: one no player owns never grows
 * @param owner the name of the player who owns it, empty when it has none or the server has no name for them
 */
public record DragonlingReport(int growth, boolean owned, Optional<String> owner) {

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonlingReport> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DragonlingReport::growth,
            ByteBufCodecs.BOOL, DragonlingReport::owned,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), DragonlingReport::owner,
            DragonlingReport::new);

    /**
     * @param ticksToGrow its ticks left to grow up
     * @param growTicks how long a Dragonling grows for, unfed, above 0
     * @param owned whether a player owns it
     * @param owner the name of its owner, if the server has one
     * @return what a Dragonling with {@code ticksToGrow} left shows; one with more left than a Dragonling
     *         now grows for, the config having changed, has grown none
     */
    public static DragonlingReport of(long ticksToGrow, int growTicks, boolean owned, Optional<String> owner) {
        long gone = Math.max(0, growTicks - ticksToGrow);
        return new DragonlingReport((int) (gone * 100 / growTicks), owned, owner);
    }

    /** @return what {@code dragonling}, a Dragonling and not a presence, shows */
    public static DragonlingReport of(VoidDragon dragonling, MinecraftServer server) {
        return of(dragonling.ticksToGrow(), VoidworksConfig.dragonlingGrowTicks(), dragonling.owner().isPresent(),
                dragonling.owner().flatMap(owner -> DragonReport.nameOf(server, owner)));
    }

    /** @return how far it has grown, then whose it is, or that no one owns it */
    public List<Component> lines() {
        return List.of(Component.translatable("voidworks.jade.dragonling.grown", growth),
                owned ? DragonReport.ownerLine(owner) : Component.translatable("voidworks.jade.dragon.no_owner"));
    }
}
