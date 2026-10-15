// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

/**
 * What a Void Dragon's presence shows a player looking at it, in Jade, read from its record, which
 * is server state (ADR 0005), as a Well's {@code WellReport} is from its core: what it is doing, the
 * Well it is bound for, the motes it carries by grade and in total, its health of
 * {@link VoidDragonRecord#FULL_HEALTH}, and its owner. Anyone looking sees it all. Read on the
 * server each time Jade asks, so it follows the record, and sent to the client, which words it with
 * {@link #lines}.
 *
 * @param doing what it is doing, as its trip's leg says
 * @param bound the name of the Well it is bound for, or unnamed, its dimension and position; empty
 *        while it rests, unloads at that Well, waits for its owner or sets its rider down
 * @param load the motes it carries, grade to count
 * @param health its health, of {@link VoidDragonRecord#FULL_HEALTH}
 * @param owner the name of the player who owns it, empty when the server has none for them
 */
public record DragonReport(Doing doing, Optional<Component> bound, SortedMap<Integer, Long> load, int health, Optional<String> owner) {

    private static final StreamCodec<ByteBuf, Doing> DOING_CODEC = ByteBufCodecs.idMapper(ordinal -> Doing.values()[ordinal], Doing::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonReport> STREAM_CODEC = StreamCodec.composite(
            DOING_CODEC, DragonReport::doing,
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), DragonReport::bound,
            ByteBufCodecs.<ByteBuf, Integer, Long, SortedMap<Integer, Long>>map(size -> new TreeMap<>(), ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_LONG)
                    .cast(), DragonReport::load,
            ByteBufCodecs.VAR_INT, DragonReport::health,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), DragonReport::owner,
            DragonReport::new);

    public DragonReport {
        load = Collections.unmodifiableSortedMap(new TreeMap<>(load));
    }

    /**
     * @param labels what the Well at a place goes by: its name, or unnamed, its dimension and position
     * @param owner the name of {@code dragon}'s owner, if the server has one
     * @return what {@code dragon} shows
     */
    public static DragonReport of(VoidDragonRecord dragon, Function<Place, Component> labels, Optional<String> owner) {
        var doing = Doing.of(dragon.trip());
        // Stopped at a Well other than where it loads, it is there for that Well, or for its owner.
        var bound = dragon.trip().filter(trip -> doing != Doing.UNLOADING && doing != Doing.WAITING && doing != Doing.SETTING_DOWN)
                .map(Trip::destination).map(labels);
        return new DragonReport(doing, bound, dragon.load(), dragon.health(), owner);
    }

    /** @return what the dragon whose presence {@code presence} is shows, empty for a Dragonling or a presence whose dragon is gone */
    public static Optional<DragonReport> of(VoidDragon presence, MinecraftServer server) {
        return presence.dragonId().flatMap(SavedVoidDragons.of(server)::get)
                .map(dragon -> of(dragon, place -> DragonRiding.label(server, place), nameOf(server, dragon.owner())));
    }

    /** @return the name of the player {@code id}, online or once seen on {@code server}, if it has one */
    static Optional<String> nameOf(MinecraftServer server, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        return online != null ? Optional.of(online.getGameProfile().name()) : server.services().nameToIdCache().get(id).map(NameAndId::name);
    }

    /** @return how many motes it carries, of every grade together */
    public long carried() {
        return load.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * @return what it is doing; the Well it is bound for, if any; a line per grade it carries, tinted
     *         like that grade's butterflies, then their total, or that it carries nothing; its health;
     *         and whose it is
     */
    public List<Component> lines() {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(doing.key));
        bound.ifPresent(well -> lines.add(Component.translatable("voidworks.jade.dragon.bound", well)));
        load.forEach((grade, count) -> lines.add(Component.translatable("voidworks.jade.dragon.grade", grade, count).withColor(GradeColor.of(grade))));
        lines.add(load.isEmpty()
                ? Component.translatable("voidworks.jade.dragon.carrying_nothing")
                : Component.translatable("voidworks.jade.dragon.carrying", carried()));
        lines.add(Component.translatable("voidworks.jade.dragon.health", health, VoidDragonRecord.FULL_HEALTH));
        lines.add(ownerLine(owner));
        return lines;
    }

    /** @return whose a dragon or Dragonling is, by {@code owner}'s name, or that no name is known for them */
    static Component ownerLine(Optional<String> owner) {
        return owner.<Component>map(name -> Component.translatable("voidworks.jade.dragon.owner", name))
                .orElseGet(() -> Component.translatable("voidworks.jade.dragon.unknown_owner"));
    }

    /** What a dragon is doing, as its trip's leg says: every dive is diving, and with no trip it rests. */
    public enum Doing {

        RESTING("voidworks.jade.dragon.resting"),
        DIVING("voidworks.jade.dragon.diving"),
        LOADING("voidworks.jade.dragon.loading"),
        UNLOADING("voidworks.jade.dragon.unloading"),
        WAITING("voidworks.jade.dragon.waiting"),
        SETTING_DOWN("voidworks.jade.dragon.setting_down");

        private final String key;

        Doing(String key) {
            this.key = key;
        }

        /** @return what a dragon on {@code trip}, or on none, is doing */
        public static Doing of(Optional<Trip> trip) {
            return trip.map(Trip::leg).map(leg -> switch (leg) {
                case TO_SOURCE, TO_DESTINATION, TO_CALL, TO_PICKED -> DIVING;
                case LOADING -> LOADING;
                case UNLOADING -> UNLOADING;
                case WAITING -> WAITING;
                case SETTING_DOWN -> SETTING_DOWN;
            }).orElse(RESTING);
        }
    }
}
