// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.energy.VoidSpender;
import io.github._5thlayer.voidworks.siphon.SiphonMode;
import io.github._5thlayer.voidworks.siphon.VoidSiphonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Void Dragon as a creature, {@code voidworks:void_dragon}: one creature that is young and then
 * grown, on the pattern of vanilla's Ghastling and Happy Ghast, in one of two forms.
 *
 * <ul>
 *   <li>A <b>Dragonling</b>, an ordinary creature that hatched from a soaking egg
 *       ({@link SoakingEggBlockEntity}), owned by the player who placed the egg. It stays near
 *       where it hatched, its home, floating about it as a Ghastling does, and follows a player holding a
 *       Void Siphon. It grows up over
 *       {@link VoidworksConfig#dragonlingGrowTicks()} while its chunk is loaded, and its owner
 *       feeds it with a Siphon set to empty, by the spend rule where it is, which takes time off its
 *       growing. One no player owns never grows.</li>
 *   <li>Grown, a Void Dragon is server state, not an entity (ADR 0005): growing up adds its
 *       {@link VoidDragonRecord}, and this creature is then only the record's <b>presence</b>,
 *       {@link DragonPresences}' to show, about half the Ender Dragon's size, which holds no state of
 *       its own and is never saved. It follows what its record shows ({@link Showing}): it emerges
 *       from a Well's shaft, up out of its mouth, as its dragon stops there, flies close about the Well while it loads or
 *       unloads and wider while it rests, and dives into the shaft and is gone as its dragon sets
 *       off.</li>
 * </ul>
 *
 * <p>Nothing hurts either, but a command's kill and the void below the world, which nothing is
 * spared. It wears the vanilla Ender Dragon's model ({@code VoidDragonRenderer}), whose neck and
 * tail follow where the body has been: it keeps the {@link #flightHistory} and the wingbeat the
 * model reads, as the Ender Dragon does.
 */
public final class VoidDragon extends PathfinderMob {

    /** The name it goes by while it is young. */
    public static final String DRAGONLING_KEY = "entity." + Voidworks.MOD_ID + ".dragonling";

    /** How far, in blocks, a Dragonling strays from its home before it heads back. */
    public static final int HOME_RADIUS = 6;

    /** How far, in blocks, a grown dragon's presence flies about where its dragon rests. */
    public static final int RESTING_RADIUS = 16;

    /** How far, in blocks, a presence flies about the Well its dragon loads or unloads at. */
    public static final int WORKING_RADIUS = 6;

    /** How fast, in blocks a tick, a presence rises out of a Well's shaft as it emerges. */
    private static final double EMERGE_SPEED = 0.25;

    /** How fast, in blocks a tick, a presence goes down into a Well's shaft as it dives. */
    private static final double DIVE_SPEED = 0.6;

    /** The most ticks a presence takes to emerge or dive, should something keep it from getting there. */
    private static final int MOST_PASSAGE_TICKS = 100;

    /** How much bigger a presence is than a Dragonling: half the Ender Dragon's size to a fifth. */
    public static final float GROWN_SCALE = 2.5F;

    /** How far, in degrees, the heading its model follows turns in a tick, as gently as the Ender Dragon turns. */
    private static final float MAX_TURN = 3.0F;

    private static final EntityDataAccessor<Boolean> DATA_GROWN = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BOOLEAN);

    private static final String OWNER_KEY = "owner";
    private static final String TICKS_TO_GROW_KEY = "ticks_to_grow";

    /** Where its body has been, for its model's neck and tail. */
    public final DragonFlightHistory flightHistory = new DragonFlightHistory();
    /** Its wingbeat, a full beat a unit, last tick and now. */
    public float oFlapTime;
    public float flapTime;
    /** The heading and height its flight history follows, eased toward its own, so its model banks and bends gently. */
    private float flightYaw = Float.NaN;
    private double flightY;

    private @Nullable UUID owner;
    /** A Dragonling's ticks left to grow, counted while its chunk is loaded. */
    private long ticksToGrow;
    /** A presence's dragon; null for a Dragonling. */
    private @Nullable UUID dragon;
    /** Where a presence's dragon is, which it shows; null for a Dragonling. */
    private @Nullable Place shownAt;
    /** Where a presence flies about, emerges and dives, over its dragon's Well or where its dragon rests elsewhere. */
    private DragonPresences.Spot spot = DragonPresences.Spot.NOWHERE;
    /** What a presence shows of its dragon now. */
    private Showing showing = Showing.RESTING;
    /** How long a presence has been emerging or diving. */
    private int passageTicks;

    public VoidDragon(EntityType<? extends VoidDragon> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.1)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.TEMPT_RANGE, 16.0);
    }

    /** It flies, up and down too, as an Allay does, at the speed its flight gives it; a presence emerging or diving goes straight through anything in its way. */
    @Override
    public void travel(Vec3 input) {
        if (passing()) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        travelFlying(input, getSpeed());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        var navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_GROWN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new YoungGoal(new TemptGoal(this, 1.0, stack -> stack.getItem() instanceof VoidSiphonItem, false, 3.0)));
        goalSelector.addGoal(2, new ReturnHomeGoal(this));
        // As a Ghastling floats about its home: a Dragonling where it hatched, a presence where its dragon rests.
        goalSelector.addGoal(3, new Ghast.RandomFloatAroundGoal(this, 16));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** Hatches it for {@code owner}, where it stands: that is its home, and it starts growing. */
    public void hatchFor(UUID owner) {
        this.owner = owner;
        setHomeTo(blockPosition(), HOME_RADIUS);
        ticksToGrow = VoidworksConfig.dragonlingGrowTicks();
    }

    /**
     * Makes it the presence of {@code record}, holding nothing that is saved or that the record reads, where its dragon is: emerging
     * at the spot's {@code emerges}, or already flying about its {@code over}.
     *
     * @param showing what the record shows, never {@link Showing#DIVING}
     */
    void showPresenceOf(VoidDragonRecord record, Showing showing, DragonPresences.Spot spot) {
        dragon = record.id();
        owner = record.owner();
        shownAt = record.rest();
        this.spot = spot;
        entityData.set(DATA_GROWN, true);
        var at = Vec3.atBottomCenterOf(showing == Showing.EMERGING ? spot.emerges() : spot.over());
        snapTo(at.x, at.y, at.z, getRandom().nextFloat() * 360.0F, 0.0F);
        if (showing == Showing.EMERGING) {
            pass(Showing.EMERGING);
        } else {
            follow(showing);
        }
    }

    /** @return whether it is the presence of a dragon at {@code place} */
    boolean shows(Place place) {
        return place.equals(shownAt);
    }

    /**
     * Follows what its dragon's record shows now: it flies close about the Well while its dragon is at
     * work there, wider while it rests, and dives away as its dragon dives. Emerging, it carries on
     * until it is out, and diving, until it is gone.
     */
    void follow(Showing next) {
        if (showing == Showing.DIVING || next == Showing.DIVING) {
            if (showing != Showing.DIVING) {
                pass(Showing.DIVING);
            }
            return;
        }
        if (showing != Showing.EMERGING) {
            settle(next == Showing.RESTING ? Showing.RESTING : Showing.WORKING);
        }
    }

    /** @return what it shows of its dragon now: for a presence that has stopped following it, {@link Showing#DIVING} */
    public Showing showing() {
        return showing;
    }

    /** @return whether it is emerging or diving, flying straight through anything in its way */
    private boolean passing() {
        return showing == Showing.EMERGING || showing == Showing.DIVING;
    }

    /** It flies about its spot's {@code over}, close while its dragon is at work and wider while it rests. */
    private void settle(Showing showing) {
        this.showing = showing;
        setHomeTo(spot.over(), showing == Showing.WORKING ? WORKING_RADIUS : RESTING_RADIUS);
    }

    /** It starts to emerge or to dive, flying straight, with its goals set aside until it is through. */
    private void pass(Showing passage) {
        showing = passage;
        passageTicks = 0;
        noPhysics = true;
        getNavigation().stop();
        moveControl.setWait();
        goalSelector.disableControlFlag(Goal.Flag.MOVE);
        goalSelector.disableControlFlag(Goal.Flag.LOOK);
        if (level() instanceof ServerLevel level) {
            if (passage == Showing.EMERGING) {
                burst(level);
            } else {
                level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.NEUTRAL, 2.0F, 0.8F);
            }
        }
    }

    /** Emerging, it rises to its spot's {@code risen} and then flies about its {@code over} at work; diving, it goes down to its {@code under} and is gone into the void. */
    private void passOn(ServerLevel level) {
        var way = Vec3.atBottomCenterOf(showing == Showing.DIVING ? spot.under() : spot.risen()).subtract(position());
        double speed = showing == Showing.DIVING ? DIVE_SPEED : EMERGE_SPEED;
        if (way.length() > speed && ++passageTicks < MOST_PASSAGE_TICKS) {
            setDeltaMovement(way.normalize().scale(speed));
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        if (showing == Showing.DIVING) {
            burst(level);
            discard();
            return;
        }
        noPhysics = false;
        goalSelector.enableControlFlag(Goal.Flag.MOVE);
        goalSelector.enableControlFlag(Goal.Flag.LOOK);
        settle(Showing.WORKING);
    }

    /** The void opening where a presence emerges or is gone. */
    private void burst(ServerLevel level) {
        level.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0, getZ(), 96, 1.5, 1.0, 1.5, 0.1);
    }

    /** @return the player who placed the egg it hatched from */
    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    /** @return whether it is grown: a Void Dragon's presence rather than a Dragonling */
    public boolean grown() {
        return entityData.get(DATA_GROWN);
    }

    /** @return the id of the dragon whose presence it is; empty for a Dragonling */
    public Optional<UUID> dragonId() {
        return Optional.ofNullable(dragon);
    }

    /** @return a Dragonling's ticks left to grow up, counted while its chunk is loaded */
    public long ticksToGrow() {
        return ticksToGrow;
    }

    /** Sets a Dragonling's ticks left to grow up, at least 0. */
    public void setTicksToGrow(long ticks) {
        ticksToGrow = Math.max(0, ticks);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        oFlapTime = flapTime;
        flapTime += 0.1F;
        recordFlight();
        if (level() instanceof ServerLevel level) {
            if (passing()) {
                passOn(level);
            } else if (!grown() && owner != null && --ticksToGrow <= 0) {
                growUp(level);
            }
        }
    }

    /**
     * Records where its body has been, for its model, which banks and bends by how fast that turns and
     * climbs: a mob's body turns in jumps the Ender Dragon's never does, so the heading recorded turns
     * at most {@link #MAX_TURN} degrees a tick and the height eases toward its own.
     */
    private void recordFlight() {
        // The Ender Dragon's model faces the other way from a mob's heading, as the Ender Dragon flies.
        float heading = Mth.wrapDegrees(yBodyRot + 180.0F);
        if (Float.isNaN(flightYaw)) {
            flightYaw = heading;
            flightY = getY();
        }
        flightYaw = Mth.wrapDegrees(flightYaw + Mth.clamp(Mth.wrapDegrees(heading - flightYaw), -MAX_TURN, MAX_TURN));
        flightY += (getY() - flightY) * 0.2;
        flightHistory.record(flightY, flightYaw);
    }

    /** Its owner, holding a Void Siphon set to empty, feeds it; anyone else, or a Siphon set to charge, does not, and a Siphon used on it never harvests. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (grown() || !(stack.getItem() instanceof VoidSiphonItem) || player.isSecondaryUseActive()) {
            return super.mobInteract(player, hand);
        }
        // Answered on the client, which does not know its owner, so the Siphon never harvests behind it instead.
        if (!(level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getUUID().equals(owner)) {
            return InteractionResult.PASS;
        }
        if (VoidSiphonItem.mode(stack) != SiphonMode.EMPTY) {
            player.sendOverlayMessage(Component.translatable("voidworks.dragonling.feed.charging"));
            return InteractionResult.FAIL;
        }
        if (feed(level, player, hand) == 0) {
            player.sendOverlayMessage(Component.translatable("voidworks.siphon.pay.nothing"));
            return InteractionResult.FAIL;
        }
        player.sendOverlayMessage(ticksToGrow > 0
                ? Component.translatable("voidworks.dragonling.grows_in", Mth.ceil(ticksToGrow / (float) Growth.TICKS_PER_MINUTE))
                : Component.translatable("voidworks.dragonling.grown"));
        if (ticksToGrow <= 0) {
            growUp(level);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Spends what the Siphon in {@code player}'s {@code hand} holds, by the spend rule where it is,
     * up to what finishing its growing takes, and takes the time it buys off its growing.
     *
     * @return the void energy it was fed
     */
    private long feed(ServerLevel level, Player player, InteractionHand hand) {
        long perMinute = VoidworksConfig.dragonlingFeedEnergy();
        var spend = VoidSpender.spendUpTo(VoidSiphonItem.motesInHand(player, hand), level, blockPosition(), Growth.energyToFinish(ticksToGrow, perMinute));
        if (spend.released() > 0) {
            setTicksToGrow(ticksToGrow - Growth.ticksOff(spend.released(), perMinute));
            var from = MoteFlight.aimAt(player);
            spend.motes().forEach((grade, count) -> MoteFlight.toEntity(level, from, this, grade, count));
        }
        return spend.released();
    }

    /** Grown, it becomes a Void Dragon's record, resting where it grew up, and the Dragonling gives way to its presence. */
    private void growUp(ServerLevel level) {
        var record = SavedVoidDragons.of(level.getServer()).add(owner, Place.of(level, blockPosition()));
        discard();
        level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0, getZ(), 128, 2.0, 1.0, 2.0, 0.1);
        DragonPresences.show(level.getServer());
        DragonPresences.presence(level.getServer(), record.id()).ifPresent(presence -> presence.snapTo(getX(), getY(), getZ(), getYRot(), 0.0F));
    }

    /** Nothing hurts it, a creative player included; only what spares nothing, a command's kill or the void, does. */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    /** Neither form is led on a leash: a Dragonling keeps to its home, and a presence to its dragon's resting place. */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** A presence never goes through a portal, which would leave its dragon showing it twice. */
    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return !grown() && super.canUsePortal(ignorePassenger);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    /** A presence is never saved: its dragon's record is, and shows it again once its place loads. */
    @Override
    public boolean shouldBeSaved() {
        return !grown() && super.shouldBeSaved();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        var dimensions = super.getDefaultDimensions(pose);
        return grown() ? dimensions.scale(GROWN_SCALE) : dimensions;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_GROWN.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    protected Component getTypeName() {
        return grown() ? super.getTypeName() : Component.translatable(DRAGONLING_KEY);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (owner != null) {
            output.store(OWNER_KEY, UUIDUtil.CODEC, owner);
        }
        output.putLong(TICKS_TO_GROW_KEY, ticksToGrow);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read(OWNER_KEY, UUIDUtil.CODEC).orElse(null);
        // One hatched before Dragonlings grew starts growing now.
        ticksToGrow = input.getLong(TICKS_TO_GROW_KEY).orElseGet(() -> (long) VoidworksConfig.dragonlingGrowTicks());
    }

    /** A Dragonling's goal, which a presence never pursues. */
    private final class YoungGoal extends Goal {

        private final Goal goal;

        YoungGoal(Goal goal) {
            this.goal = goal;
            setFlags(EnumSet.copyOf(goal.getFlags()));
        }

        @Override
        public boolean canUse() {
            return !grown() && goal.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !grown() && goal.canContinueToUse();
        }

        @Override
        public void start() {
            goal.start();
        }

        @Override
        public void stop() {
            goal.stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return goal.requiresUpdateEveryTick();
        }

        @Override
        public void tick() {
            goal.tick();
        }
    }

    /** Heads back to its home once it has strayed past its radius, flying straight there. */
    private static final class ReturnHomeGoal extends Goal {

        private final VoidDragon dragonling;

        ReturnHomeGoal(VoidDragon dragonling) {
            this.dragonling = dragonling;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return dragonling.hasHome() && !dragonling.isWithinHome();
        }

        @Override
        public boolean canContinueToUse() {
            return !dragonling.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos home = dragonling.getHomePosition();
            dragonling.getNavigation().moveTo(home.getX() + 0.5, home.getY() + 1.0, home.getZ() + 0.5, 1.0);
        }
    }
}
