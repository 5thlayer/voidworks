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

import io.github._5thlayer.voidworks.VoidworksConfig;
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
 * Well it is bound for, what it carries, a Mote-line dragon's motes by grade and in total and a
 * Matter-line one's items, one line each, and its stacks of its load, its health of
 * {@link VoidDragonRecord#FULL_HEALTH}, shown as Jade's hearts, its life stage, and its owner. Anyone looking sees it all. Read on the
 * server each time Jade asks, so it follows the record, and sent to the client, which words it with
 * {@link #lines}.
 *
 * @param doing what it is doing, as its trip's leg says
 * @param bound the name of the Well it is bound for, or unnamed, its dimension and position; empty
 *        while it rests, unloads at that Well, waits for its owner or sets its rider down
 * @param load the motes a Mote-line dragon carries, grade to count
 * @param line its Line, which says how its load is shown, never named itself
 * @param items the items a Matter-line dragon carries, which Jade shows with their icons
 * @param most the most stacks a Matter-line dragon carries now; none for a Mote-line one
 * @param health its health, of {@link VoidDragonRecord#FULL_HEALTH}
 * @param stage its life stage
 * @param owner the name of the player who owns it, empty when the server has none for them
 */
public record DragonReport(Doing doing, Optional<Component> bound, SortedMap<Integer, Long> load, Line line, List<Ware> items, long most, long toll, int health,
                           LifeStage stage, Optional<String> owner) {

    private static final StreamCodec<ByteBuf, Doing> DOING_CODEC = ByteBufCodecs.idMapper(ordinal -> Doing.values()[ordinal], Doing::ordinal);
    private static final StreamCodec<ByteBuf, LifeStage> STAGE_CODEC = ByteBufCodecs.idMapper(ordinal -> LifeStage.values()[ordinal], LifeStage::ordinal);
    private static final StreamCodec<ByteBuf, Line> LINE_CODEC = ByteBufCodecs.idMapper(ordinal -> Line.values()[ordinal], Line::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonReport> STREAM_CODEC = StreamCodec.composite(
            DOING_CODEC, DragonReport::doing,
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), DragonReport::bound,
            ByteBufCodecs.<ByteBuf, Integer, Long, SortedMap<Integer, Long>>map(size -> new TreeMap<>(), ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_LONG)
                    .cast(), DragonReport::load,
            LINE_CODEC, DragonReport::line,
            ByteBufCodecs.fromCodec(Ware.CODEC).apply(ByteBufCodecs.list()), DragonReport::items,
            ByteBufCodecs.VAR_LONG, DragonReport::most,
            ByteBufCodecs.VAR_LONG, DragonReport::toll,
            ByteBufCodecs.VAR_INT, DragonReport::health,
            STAGE_CODEC, DragonReport::stage,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), DragonReport::owner,
            DragonReport::new);

    public DragonReport {
        load = Collections.unmodifiableSortedMap(new TreeMap<>(load));
        items = List.copyOf(items);
    }

    /**
     * @param labels what the Well at a place goes by: its name, or unnamed, its dimension and position
     * @param owner the name of {@code dragon}'s owner, if the server has one
     * @param stages how dragons age
     * @param matterLoad the most stacks a young Matter-line dragon carries
     * @return what {@code dragon} shows
     */
    public static DragonReport of(VoidDragonRecord dragon, Function<Place, Component> labels, Optional<String> owner, LifeStages stages,
            long matterLoad) {
        var doing = Doing.of(dragon.trip());
        // Stopped at a Well other than where it loads, it is there for that Well, or for its owner.
        var bound = dragon.trip().filter(trip -> doing != Doing.UNLOADING && doing != Doing.WAITING && doing != Doing.SETTING_DOWN)
                .map(Trip::destination).map(labels);
        var motes = switch (dragon.load()) {
            case MoteLoad load -> load.motes();
            case MatterLoad load -> MoteLoad.NONE.motes();
        };
        var items = switch (dragon.load()) {
            case MoteLoad load -> List.<Ware>of();
            case MatterLoad load -> load.wares();
        };
        long toll = dragon.load() instanceof MatterLoad load ? load.toll() : 0;
        long most = dragon.line() == Line.MATTER ? stages.load(matterLoad, dragon.cargo()) : 0;
        return new DragonReport(doing, bound, motes, dragon.line(), items, most, toll, dragon.health(), stages.of(dragon.cargo()), owner);
    }

    /** @return what the dragon whose presence {@code presence} is shows, empty for a Dragonling or a presence whose dragon is gone */
    public static Optional<DragonReport> of(VoidDragon presence, MinecraftServer server) {
        return presence.dragonId().flatMap(SavedVoidDragons.of(server)::get)
                .map(dragon -> of(dragon, place -> DragonRiding.label(server, place), nameOf(server, dragon.owner()), VoidworksConfig.lifeStages(),
                        VoidworksConfig.dragonMatterLoad()));
    }

    /** @return the translation key of {@code stage}'s name, as Jade shows it */
    private static String stageKey(LifeStage stage) {
        return "voidworks.jade.dragon." + stage.id();
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

    /** @return how many stacks a Matter-line dragon's items fill */
    public long stacks() {
        return new MatterLoad(items).amount();
    }

    /**
     * @return what it is doing; the Well it is bound for, if any; a line per grade it carries, tinted
     *         like that grade's butterflies, then their total, or that it carries nothing; its life
     *         stage; and whose it is. A Matter-line dragon's items are no lines: Jade draws them, with
     *         their icons, between {@link #head} and {@link #tail}. Its health is no line: Jade draws it
     *         as hearts, as the Ender Dragon's
     */
    public List<Component> lines() {
        var lines = new ArrayList<>(head());
        lines.addAll(tail());
        return lines;
    }

    /** @return what it is doing, the Well it is bound for, if any, and a line per grade of motes it carries */
    public List<Component> head() {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(doing.key));
        bound.ifPresent(well -> lines.add(Component.translatable("voidworks.jade.dragon.bound", well)));
        load.forEach((grade, count) -> lines.add(Component.translatable("voidworks.jade.dragon.grade", grade, count).withColor(GradeColor.of(grade))));
        return lines;
    }

    /**
     * @return how much it carries, a Mote-line dragon's motes in total and a Matter-line one's stacks of
     *         its load with the Toll it paid as it loaded, or that it carries nothing; its life stage; and whose it is
     */
    public List<Component> tail() {
        var lines = new ArrayList<Component>();
        if (load.isEmpty() && items.isEmpty()) {
            lines.add(Component.translatable("voidworks.jade.dragon.carrying_nothing"));
        } else {
            lines.add(line == Line.MATTER
                    ? Component.translatable("voidworks.jade.dragon.stacks", stacks(), most, toll)
                    : Component.translatable("voidworks.jade.dragon.carrying", carried()));
        }
        lines.add(Component.translatable(stageKey(stage)));
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
