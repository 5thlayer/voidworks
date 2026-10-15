// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.jade;

import java.util.List;
import java.util.function.Function;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a creature says about itself in Jade, as libworks-runtime's {@code MachineReport} does for a
 * block entity: the server reads {@code D} from the entity, null when it has nothing to say, and
 * sends it with its codec, and a {@link Client} adds the lines it reads as. This half names no
 * client type, so a dedicated server never loads Jade's drawing.
 */
final class CreatureReport<E extends Entity, D> implements StreamServerDataProvider<EntityAccessor, D> {

    private final Identifier uid;
    private final Class<E> type;
    private final Function<E, @Nullable D> read;
    private final StreamCodec<RegistryFriendlyByteBuf, D> codec;

    CreatureReport(Identifier uid, Class<E> type, Function<E, @Nullable D> read, StreamCodec<RegistryFriendlyByteBuf, D> codec) {
        this.uid = uid;
        this.type = type;
        this.read = read;
        this.codec = codec;
    }

    @Override
    public @Nullable D streamData(EntityAccessor accessor) {
        return type.isInstance(accessor.getEntity()) ? read.apply(type.cast(accessor.getEntity())) : null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, D> streamCodec() {
        return codec;
    }

    @Override
    public Identifier getUid() {
        return uid;
    }

    /**
     * Adds the lines a creature's data reads as, when the server sent any, in place of Jade's hearts:
     * a Void Dragon's health is its record's, not its presence's, and nothing hurts a Dragonling.
     * Client-only.
     */
    static final class Client<D> implements IEntityComponentProvider {

        private final CreatureReport<?, D> report;
        private final Function<D, List<Component>> lines;

        /** On {@code report}'s uid, decoding with its codec, adding the lines {@code lines} words its data as. */
        Client(CreatureReport<?, D> report, Function<D, List<Component>> lines) {
            this.report = report;
            this.lines = lines;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            report.decodeFromData(accessor).ifPresent(data -> {
                // Jade's hearts are added first, at a lower priority than this.
                tooltip.remove(JadeIds.MC_ENTITY_HEALTH);
                lines.apply(data).forEach(tooltip::add);
            });
        }

        @Override
        public Identifier getUid() {
            return report.getUid();
        }
    }
}
