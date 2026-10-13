// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Void Dragon as a creature, {@code voidworks:void_dragon}. Only its young, the Dragonling, is
 * built yet, so every one is a Dragonling and is named so: an ordinary creature until it grows,
 * where a grown Void Dragon is server state, not an entity (ADR 0005). It hatches from a soaking egg
 * ({@link SoakingEggBlockEntity}), owned by the player who placed the egg, and hovers where it
 * hatched. Nothing hurts it, but a command's kill and the void below the world, which nothing is
 * spared.
 *
 * <p>It wears the vanilla Ender Dragon's model ({@code VoidDragonRenderer}), whose neck and tail
 * follow where the body has been: it keeps the {@link #flightHistory} and the wingbeat the model
 * reads, as the Ender Dragon does.
 */
public final class VoidDragon extends PathfinderMob {

    /** The name every one goes by while it is young. */
    public static final String DRAGONLING_KEY = "entity." + Voidworks.MOD_ID + ".dragonling";

    private static final String OWNER_KEY = "owner";

    /** Where its body has been, for its model's neck and tail. */
    public final DragonFlightHistory flightHistory = new DragonFlightHistory();
    /** Its wingbeat, a full beat a unit, last tick and now. */
    public float oFlapTime;
    public float flapTime;

    private @Nullable UUID owner;

    public VoidDragon(EntityType<? extends VoidDragon> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /** @return the player who placed the egg it hatched from */
    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        oFlapTime = flapTime;
        flapTime += 0.1F;
        // The Ender Dragon's model faces the other way from a mob's heading, as the Ender Dragon flies.
        flightHistory.record(getY(), Mth.wrapDegrees(yBodyRot + 180.0F));
    }

    /** Nothing hurts it, a creative player included; only what spares nothing, a command's kill or the void, does. */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable(DRAGONLING_KEY);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (owner != null) {
            output.store(OWNER_KEY, UUIDUtil.CODEC, owner);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read(OWNER_KEY, UUIDUtil.CODEC).orElse(null);
    }
}
