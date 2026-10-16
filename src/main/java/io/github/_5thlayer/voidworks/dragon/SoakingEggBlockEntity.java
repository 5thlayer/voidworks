// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksEntities;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A soaking egg's block entity. It soaks its price through {@link Dormancy}, by the spend rule at
 * its own block, from the mote stores touching it, and once paid hatches a Dragonling its owner
 * owns, of the Line its egg fixes. Its price is fixed when it starts soaking ({@link Hatchery}); an egg with no owner, which no
 * player placed, never soaks.
 */
public final class SoakingEggBlockEntity extends BlockEntity implements Dormancy.Holder {

    private static final String OWNER_KEY = "owner";

    private final Dormancy dormancy = new Dormancy(this);
    private @Nullable UUID owner;

    public SoakingEggBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.SOAKING_EGG_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    /** @return the player who placed it, who owns what hatches; empty for an egg no player placed */
    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    /**
     * Starts it soaking for {@code owner}, who placed it, at the price the {@link Hatchery} fixes now,
     * once it has forgotten any egg of theirs that no longer stands.
     */
    void startSoaking(ServerLevel level, UUID owner) {
        var hatchery = SavedHatchery.of(level.getServer());
        // Before it takes its owner, so an egg of theirs that stood here before it is forgotten too.
        hatchery.forgetGone(owner, egg -> gone(level.getServer(), egg, owner));
        this.owner = owner;
        dormancy.setPrice(hatchery.startSoaking(egg(level), owner, EggPrice.of(getBlockState())));
        setChanged();
    }

    /** @return whether {@code owner}'s egg at {@code egg} is known to stand no longer; one in an unloaded chunk may still */
    private static boolean gone(MinecraftServer server, Place egg, UUID owner) {
        var level = egg.level(server);
        if (level == null) {
            return true;
        }
        if (!level.isLoaded(egg.pos())) {
            return false;
        }
        return !(level.getBlockEntity(egg.pos()) instanceof SoakingEggBlockEntity soaking && soaking.owner().equals(Optional.of(owner)));
    }

    void serverTick(ServerLevel level) {
        if (owner == null) {
            return;
        }
        if (dormancy.soak(level)) {
            hatch(level, owner);
            return;
        }
        int stage = SoakingEggBlock.stage(dormancy.paid(), dormancy.cost());
        if (getBlockState().getValue(SoakingEggBlock.STAGE) != stage) {
            level.setBlock(worldPosition, getBlockState().setValue(SoakingEggBlock.STAGE, stage), Block.UPDATE_ALL);
        }
    }

    private void hatch(ServerLevel level, UUID owner) {
        SavedHatchery.of(level.getServer()).hatch(egg(level));
        var pos = worldPosition;
        level.removeBlock(pos, false);
        var dragonling = VoidworksEntities.VOID_DRAGON.get().create(level, EntitySpawnReason.BREEDING);
        if (dragonling != null) {
            dragonling.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            dragonling.hatchFor(owner, getBlockState().getValue(SoakingEggBlock.EGG).line());
            level.addFreshEntity(dragonling);
        }
        level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64, 0.5, 0.5, 0.5, 0.1);
    }

    /** Broken, or otherwise gone before it hatched, it no longer counts toward its owner's price. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server && owner != null) {
            SavedHatchery.of(server.getServer()).lose(egg(server));
        }
    }

    private Place egg(ServerLevel level) {
        return Place.of(level, worldPosition);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        dormancy.save(output);
        if (owner != null) {
            output.store(OWNER_KEY, UUIDUtil.CODEC, owner);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dormancy.load(input);
        owner = input.read(OWNER_KEY, UUIDUtil.CODEC).orElse(null);
    }
}
