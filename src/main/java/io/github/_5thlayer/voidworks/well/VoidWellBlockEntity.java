// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.well;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.network.StoreKind;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A Void Well core's block entity. It looks over the well around it once every
 * {@link VoidworksConfig#soakInterval()} ticks. Whole and Dormant, it soaks its cost; whole and
 * awake, it is a store of its network. No longer whole, it leaves the network and keeps the motes it
 * held, saved with it, until it is whole again. Awake and in a network, its motes are the network's,
 * saved with the dimension's networks.
 *
 * <p>It keeps its owner, the player who placed it, its {@link FillTarget} and its name, whole or not.
 * A core from before owners existed has none, and is claimed by the first player to set its target or
 * name it ({@link #ownsOrClaims}).
 */
public final class VoidWellBlockEntity extends BlockEntity implements Dormancy.Holder {

    private static final String KEPT_KEY = "kept";
    private static final String OWNER_KEY = "owner";
    private static final String TARGET_KEY = "target";
    private static final String NAME_KEY = "name";
    private static final Codec<Map<Integer, Long>> KEPT_CODEC = Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.LONG);

    private final Dormancy dormancy = new Dormancy(this);
    /** The motes it held when it was last no longer whole, which go back in once it is again. */
    private Map<Integer, Long> kept = new TreeMap<>();
    private boolean whole;
    private @Nullable NameAndId owner;
    private FillTarget target = FillTarget.NONE;
    private @Nullable Component name;
    private int sinceLook = Integer.MAX_VALUE - 1;

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

    /** @return the name a name tag gave it, if one has */
    public Optional<Component> name() {
        return Optional.ofNullable(name);
    }

    /** @return its name, or unnamed, its dimension and position */
    public Component label() {
        if (name != null) {
            return name;
        }
        var dimension = level == null ? "" : level.dimension().identifier().toString();
        return Component.translatable("voidworks.well.unnamed", dimension, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    /** Makes {@code player}, who placed it, its owner. */
    void claim(Player player) {
        owner = new NameAndId(player.getGameProfile());
        setChanged();
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

    /** @return for a player who does not own it: whose it is */
    Component ownedBy() {
        return Component.translatable("voidworks.well.owned_by", owner == null ? "" : owner.name());
    }

    /** Moves its target on to the next, none after full. */
    void cycleTarget() {
        target = target.next();
        setChanged();
    }

    /** Names it, as a name tag does. */
    void rename(Component name) {
        this.name = name;
        setChanged();
    }

    /** @return for a player: that the well around it is not whole, else its payment, or that it is awake */
    public Component status(Level level) {
        return whole(level) ? dormancy.status() : Component.translatable("voidworks.well.not_whole");
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
        if (++sinceLook >= VoidworksConfig.soakInterval()) {
            sinceLook = 0;
            whole = whole(level);
        }
        var networks = VoidstoneNetworks.of(level);
        if (!whole) {
            if (networks.store(worldPosition).isPresent()) {
                kept = networks.withdraw(worldPosition);
                setChanged();
            }
            return;
        }
        if (dormancy.soak(level) && networks.store(worldPosition).isEmpty()) {
            networks.rejoin(worldPosition, StoreKind.WELL, kept);
            kept = new TreeMap<>();
            setChanged();
        }
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
}
