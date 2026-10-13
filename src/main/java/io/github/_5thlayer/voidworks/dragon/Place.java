// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * A position in some dimension: where an egg soaks, or where a Void Dragon rests. Plain data, so the
 * records that hold it are tested with no game running.
 *
 * @param dimension the dimension's id, such as {@code minecraft:overworld}
 */
public record Place(Identifier dimension, BlockPos pos) {

    public static final Codec<Place> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(Place::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Place::pos)
    ).apply(instance, Place::new));

    /** @return {@code pos} in {@code level}'s dimension */
    public static Place of(ServerLevel level, BlockPos pos) {
        return new Place(level.dimension().identifier(), pos.immutable());
    }

    /** @return its dimension's level on {@code server}, or null when the server has no such dimension */
    public @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }
}
