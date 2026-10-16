// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidNetworks;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A Void Well core's block entity. It looks over the well around it once every
 * {@link VoidworksConfig#soakInterval()} ticks. Whole and Dormant, it soaks its cost; whole and
 * awake, it is a store of its network. Either way it touches through every block of the well, its
 * casing and rim as well as the core ({@link #parts}), so Voidstone beside any of them reaches it
 * and the core's face stays free for a player. No longer whole, it leaves the network and keeps the motes it
 * held, saved with it, until it is whole again. Awake and in a network, its motes are the network's,
 * saved with the dimension's networks.
 *
 * <p>It keeps its owner, the player who placed it, its {@link FillTarget} and its name, whole or not.
 * A core from before owners existed has none, and is claimed by the first player to set its target or
 * name it ({@link #ownsOrClaims}). Once it has an owner, it keeps itself in the server's
 * {@link VoidWells} with its target, so its owner's Void Dragons find it with no chunk loaded.
 *
 * <p>Its shaft shows its network's fill ({@link ShaftFill}): looking over the well, it works out
 * what the shaft shows, nothing while it is Dormant or not whole, and tells a watcher only when that
 * changes. A watcher eases the fluid to a new level ({@link #shownLevel}).
 */
public final class VoidWellBlockEntity extends BlockEntity implements Dormancy.Holder {

    private static final String KEPT_KEY = "kept";
    private static final String OWNER_KEY = "owner";
    private static final String TARGET_KEY = "target";
    private static final String NAME_KEY = "name";
    private static final String SHAFT_KEY = "shaft";
    /** Ticks a watcher takes to ease its shaft's fluid to a new level. */
    private static final int EASE_TICKS = 20;
    /** What a watcher does each tick for a Well it has loaded: has its shaft's butterflies drift, once the client says how. */
    private static Consumer<VoidWellBlockEntity> watcherTick = well -> {
    };
    private static final Codec<Map<Integer, Long>> KEPT_CODEC = Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.LONG);

    private final Dormancy dormancy = new Dormancy(this);
    /** The motes it held when it was last no longer whole, which go back in once it is again. */
    private Map<Integer, Long> kept = new TreeMap<>();
    private boolean whole;
    private @Nullable NameAndId owner;
    private FillTarget target = FillTarget.NONE;
    private @Nullable Component name;
    private int sinceLook = Integer.MAX_VALUE - 1;
    /** Whether, since it loaded, it has rejoined its network with its parts, which a Well saved before they touched for it lacks. */
    private boolean joinedWithParts;
    /** Whether, since it loaded, it has put itself in the server's {@link VoidWells}, which a Well saved before them lacks. */
    private boolean listed;
    /** What its shaft shows: on the server, what it last worked out; for a watcher, what it was last told. */
    private ShaftFill shaft = ShaftFill.EMPTY;
    /** For a watcher: the level its fluid eases from, and the game time it began to. */
    private double easedFrom;
    private long easingSince;

    public VoidWellBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.VOID_WELL_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    /** @return the player who placed it, by the name they had when they claimed it; empty for a core from before owners */
    public Optional<NameAndId> owner() {
        return Optional.ofNullable(owner);
    }

    /** @return how full it asks to be kept */
    public FillTarget target() {
        return target;
    }

    /** @return what its shaft shows of its network: what it last worked out, or, for a watcher, what it was told */
    public ShaftFill shaft() {
        return shaft;
    }

    /**
     * @param gameTime    the game time now
     * @param partialTick how far through the tick it is
     * @return for a watcher, how high its shaft's fluid stands now, as a share of the shaft's height:
     *         easing from where it stood toward what it was last told
     */
    public double shownLevel(long gameTime, float partialTick) {
        double eased = Mth.clamp((gameTime - easingSince + partialTick) / EASE_TICKS, 0, 1);
        return Mth.lerp(eased * eased * (3 - 2 * eased), easedFrom, shaft.level());
    }

    /** @return the room its shaft's fluid fills when its network is full */
    public AABB fluidRoom() {
        return WellStructure.fluid(worldPosition, getBlockState().getValue(VoidWellBlock.FACING));
    }

    /** Has a watcher do {@code tick} each tick for every Well it has loaded, as the client does for their butterflies. */
    public static void onWatcherTick(Consumer<VoidWellBlockEntity> tick) {
        watcherTick = tick;
    }

    void watcherTick() {
        watcherTick.accept(this);
    }

    /** @return the name a name tag gave it, if one has */
    public Optional<Component> name() {
        return Optional.ofNullable(name);
    }

    /** @return its name, or unnamed, its dimension and position */
    public Component label() {
        return VoidWells.label(Optional.ofNullable(name), level == null ? "" : level.dimension().identifier().toString(), worldPosition);
    }

    /** Makes {@code player}, who placed it, its owner. */
    void claim(Player player) {
        owner = new NameAndId(player.getGameProfile());
        setChanged();
        list();
    }

    /**
     * @return whether {@code player} may set its target or name: its owner may, and so may anyone while
     *         it has none, which makes them its owner
     */
    boolean ownsOrClaims(Player player) {
        if (owner == null) {
            claim(player);
        }
        return owner.id().equals(player.getUUID());
    }

    /**
     * Readies it for {@code owner}, as an operator's command does: theirs, with {@code target}, its
     * Dormant cost paid, and, when the well around it is whole, in its network now rather than after
     * its next look at the well.
     */
    public void ready(ServerLevel level, Player owner, FillTarget target) {
        this.target = target;
        claim(owner);
        dormancy.payInFull();
        lookNow();
        serverTick(level);
    }

    /** Has its next tick look whether the well around it is whole, as it does every soak interval. */
    private void lookNow() {
        sinceLook = VoidworksConfig.soakInterval();
    }

    /** @return for a player who does not own it: whose it is */
    Component ownedBy() {
        return Component.translatable("voidworks.well.owned_by", owner == null ? "" : owner.name());
    }

    /** Moves its target on to the next, none after full. */
    void cycleTarget() {
        target = target.next();
        setChanged();
        list();
    }

    /** Puts it in the server's {@link VoidWells}, as it is now, once it has an owner. */
    private void list() {
        if (owner != null && level instanceof ServerLevel server) {
            SavedVoidWells.of(server.getServer()).put(new VoidWells.Entry(Place.of(server, worldPosition), owner.id(), target, Optional.ofNullable(name)));
            listed = true;
        }
    }

    /** Names it, as a name tag does. */
    void rename(Component name) {
        this.name = name;
        setChanged();
        list();
    }

    /** @return for a player: that the well around it is not whole, else its payment, or that it is awake */
    public Component status(Level level) {
        return whole(level) ? dormancy.status() : Component.translatable("voidworks.well.not_whole");
    }

    /** @return the well's casing and rim around it, which touch for it what it soaks from and the network it joins */
    @Override
    public Set<BlockPos> parts() {
        return WellStructure.layout(worldPosition, getBlockState().getValue(VoidWellBlock.FACING)).entrySet().stream()
                .filter(entry -> entry.getValue() == WellStructure.Part.CASING || entry.getValue() == WellStructure.Part.RIM)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** A Void Siphon pays the core only once the well around it is whole, as its soak does. */
    @Override
    public Optional<Component> refusesPayment() {
        return level != null && !whole(level) ? Optional.of(Component.translatable("voidworks.well.not_whole")) : Optional.empty();
    }

    /** @return whether the well around it is whole now */
    public boolean whole(Level level) {
        var facing = getBlockState().getValue(VoidWellBlock.FACING);
        return WellStructure.whole(worldPosition, facing, pos -> part(level.getBlockState(pos)));
    }

    private static WellStructure.Part part(BlockState state) {
        if (state.is(VoidworksBlocks.WELL_CASING.get())) {
            return WellStructure.Part.CASING;
        }
        if (state.is(VoidworksBlocks.WELL_RIM.get())) {
            return WellStructure.Part.RIM;
        }
        return state.isAir() ? WellStructure.Part.OPEN : WellStructure.Part.NONE;
    }

    void serverTick(ServerLevel level) {
        if (!listed) {
            list();
        }
        boolean looking = ++sinceLook >= VoidworksConfig.soakInterval();
        if (looking) {
            sinceLook = 0;
            whole = whole(level);
        }
        var networks = VoidstoneNetworks.of(level);
        boolean inNetwork = keepStoreAwake(level, networks);
        if (looking) {
            show(level, inNetwork
                    ? networks.at(worldPosition).map(ShaftFill::of).orElse(ShaftFill.EMPTY)
                    : ShaftFill.EMPTY);
        }
    }

    /** Has its shaft show {@code fill}, telling watchers when that changes what they see. */
    private void show(ServerLevel level, ShaftFill fill) {
        if (!fill.equals(shaft)) {
            shaft = fill;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** @return whether, having soaked, joined or left as the well around it is, it is awake in its network */
    private boolean keepStoreAwake(ServerLevel level, VoidNetworks networks) {
        if (!whole) {
            if (networks.store(worldPosition).isPresent()) {
                kept = networks.withdraw(worldPosition);
                setChanged();
            }
            return false;
        }
        if (!dormancy.soak(level)) {
            return false;
        }
        if (networks.store(worldPosition).isEmpty()) {
            networks.rejoin(worldPosition, StoreKind.WELL, parts(), kept);
            kept = new TreeMap<>();
            setChanged();
        } else if (!joinedWithParts) {
            networks.rejoin(worldPosition, StoreKind.WELL, parts(), networks.withdraw(worldPosition));
        }
        joinedWithParts = true;
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        dormancy.save(output);
        if (!kept.isEmpty()) {
            output.store(KEPT_KEY, KEPT_CODEC, kept);
        }
        output.storeNullable(OWNER_KEY, NameAndId.CODEC, owner);
        output.store(TARGET_KEY, FillTarget.CODEC, target);
        output.storeNullable(NAME_KEY, ComponentSerialization.CODEC, name);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dormancy.load(input);
        kept = new TreeMap<>(input.read(KEPT_KEY, KEPT_CODEC).orElse(Map.of()));
        owner = input.read(OWNER_KEY, NameAndId.CODEC).orElse(null);
        target = input.read(TARGET_KEY, FillTarget.CODEC).orElse(FillTarget.NONE);
        name = parseCustomNameSafe(input, NAME_KEY);
    }

    /** A watcher is sent only what its shaft shows, as the chunk arrives. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store(SHAFT_KEY, ShaftFill.CODEC, shaft);
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as the chunk arrives: no easing, as nothing stood there before. */
    @Override
    public void handleUpdateTag(ValueInput input) {
        shaft = input.read(SHAFT_KEY, ShaftFill.CODEC).orElse(ShaftFill.EMPTY);
        easedFrom = shaft.level();
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as it changes, easing its fluid from where it stands. */
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        long now = level == null ? 0 : level.getGameTime();
        easedFrom = shownLevel(now, 0);
        easingSince = now;
        shaft = input.read(SHAFT_KEY, ShaftFill.CODEC).orElse(ShaftFill.EMPTY);
    }
}
