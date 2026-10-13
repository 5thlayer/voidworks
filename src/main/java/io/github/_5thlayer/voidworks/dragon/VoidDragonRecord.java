// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

/**
 * A grown Void Dragon, as server state rather than an entity (ADR 0005): who owns it, its health
 * and where it rests, which is where it grew up until it has stopped at a Well. The dragon a player
 * sees is only its presence ({@link DragonPresences}).
 *
 * @param id the dragon's own, which its presence names
 * @param health its health, of {@link #FULL_HEALTH}
 */
public record VoidDragonRecord(UUID id, UUID owner, int health, Place rest) {

    /** A Void Dragon's health while it is whole. Provisional, like every number. */
    public static final int FULL_HEALTH = 100;

    public static final Codec<VoidDragonRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(VoidDragonRecord::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(VoidDragonRecord::owner),
            Codec.INT.fieldOf("health").forGetter(VoidDragonRecord::health),
            Place.CODEC.fieldOf("rest").forGetter(VoidDragonRecord::rest)
    ).apply(instance, VoidDragonRecord::new));
}
