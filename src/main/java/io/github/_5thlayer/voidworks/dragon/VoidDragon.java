// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.EnumSet;
import java.util.Objects;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 *       from a Well's shaft, up out of its mouth, as its dragon stops there, flies close about
 *       the Well while it loads or unloads, lands and sits astride its rim while it rests
 *       ({@link Perch}), and takes off, dives into the shaft and is gone as its dragon sets off.
 *       Its owner rides it from a Well, where it hovers until they dive or get off, and it holds
 *       them down the shaft through the dive ({@link DragonRiding}).</li>
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

    /** How long, in ticks, a presence carrying its rider dives on once their eyes are below the shaft's mouth, before they see only the void. */
    public static final int VOID_DELAY_TICKS = 10;

    /** The most ticks a presence takes to emerge or dive, should something keep it from getting there. */
    private static final int MOST_PASSAGE_TICKS = 100;

    /** How much bigger a presence is than a Dragonling: half the Ender Dragon's size to a fifth. */
    public static final float GROWN_SCALE = 2.5F;

    /** Ticks its presence takes to die of old age, rising over its Well in the Ender Dragon's death, shortened. */
    public static final int DEATH_TICKS = 100;

    /** How far it rises each tick as it dies, as the Ender Dragon does. */
    private static final double DEATH_RISE = 0.1;

    /**
     * Where a presence's rider sits, in blocks from where it stands: on top of its body, where its wings
     * meet. The Ender Dragon's model, which its flapping lifts 2 blocks, has its body's top about 3.2
     * blocks over its feet and its wings meeting 0.3 blocks behind them toward its tail, at full size; a
     * presence is half that size. Its box stands far higher.
     */
    private static final Vec3 SEAT = new Vec3(0.0, 1.6, 0.45);

    /** How far through a wingbeat its model's wings hang lowest, the tips bent down too: where a sitting presence holds them. */
    private static final float WINGS_DOWN = 0.75F;

    /** How likely a loaded Mote-line presence is to gain a butterfly of each grade it carries following it in a tick. */
    private static final float FOLLOWER_CHANCE = 0.1F;

    /** How far, in degrees, the heading its model follows turns in a tick, as gently as the Ender Dragon turns. */
    private static final float MAX_TURN = 3.0F;

    /** How fast, in blocks a tick, a presence comes down to its seat. */
    private static final double LAND_SPEED = 0.25;

    /** How fast, in blocks a tick, a presence rises off its seat. */
    private static final double TAKE_OFF_SPEED = 0.3;

    /** How far above its seat a presence comes in to land, before it comes straight down. */
    private static final int LANDING_HEIGHT = 2;

    /** How near, in blocks across, a presence coming in to land must be over its seat before it comes down onto it. */
    private static final double OVER_THE_SEAT = 0.5;

    /** The most ticks a presence takes to land, should something keep it from its seat; it flies about again and tries later. */
    private static final int MOST_LANDING_TICKS = 200;

    /** The most ticks a presence takes to take off, should something keep it down; it goes on as if it were up, so a dive is never held up. */
    private static final int MOST_TAKE_OFF_TICKS = 40;

    /** How fast, in blocks a tick, a presence goes to where it holds still over the shaft while items move to or from its paws. */
    private static final double HOLD_SPEED = 0.25;

    private static final EntityDataAccessor<Boolean> DATA_GROWN = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BOOLEAN);
    /** Whether a presence carries its rider through its dragon's dive, which it holds them in until it sets them down: synced, so every client seats them down the shaft. */
    private static final EntityDataAccessor<Boolean> DATA_CARRYING = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BOOLEAN);
    /** Whether a presence carrying its rider has taken them down into the shaft, their eyes below its rim, where they see only the void: synced, for their own client to draw. */
    private static final EntityDataAccessor<Boolean> DATA_IN_THE_VOID = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BOOLEAN);
    /** Where a presence is as to its seat ({@link Perch}): synced, so every client poses it sitting, landing or taking off. */
    private static final EntityDataAccessor<Byte> DATA_PERCH = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BYTE);
    /** The heading, as a mob's, a presence keeps as it lands, sits and takes off: synced, since its model follows it rather than its body. */
    private static final EntityDataAccessor<Float> DATA_PERCH_YAW = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.FLOAT);
    /** Where a presence lands and sits, while it does: synced, for how far its model's neck has to settle. */
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_SEAT = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** Its dragon's life stage, by which a grown presence is sized. */
    private static final EntityDataAccessor<Byte> DATA_STAGE = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BYTE);
    /** What its rider is shown of the dive it carries them through ({@link DiveShown}): synced, for their own client to draw. */
    private static final EntityDataAccessor<Component> DATA_DIVE_LABEL = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Long> DATA_DIVE_FROM = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_DIVE_UNTIL = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_DIVE_COLOUR = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.INT);
    /** Whether it plays its dragon's death of old age. */
    private static final EntityDataAccessor<Boolean> DATA_DYING = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BOOLEAN);
    /** What it hangs under its body: a Matter-line presence's first carried stack, a Matter-line Dragonling's chest. */
    private static final EntityDataAccessor<ItemStack> DATA_HUNG = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.ITEM_STACK);
    /** Its Line: a Dragonling's, which its egg fixed, or a presence's dragon's; synced, so every client tints it by its Line. */
    private static final EntityDataAccessor<Byte> DATA_LINE = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.BYTE);
    /** The grades of the butterflies following a Mote-line presence ({@link Followers#bits()}): which it carries, not how many. */
    private static final EntityDataAccessor<Integer> DATA_FOLLOWERS = SynchedEntityData.defineId(VoidDragon.class, EntityDataSerializers.INT);

    private static Follower follower = (dragon, grade, offset) -> {};

    private static Passer passer = PassingShown::send;

    private static final String OWNER_KEY = "owner";
    private static final String LINE_KEY = "line";
    private static final String TICKS_TO_GROW_KEY = "ticks_to_grow";

    /** Ticks it has been dying, on either side, counted from when it began. */
    private int deathTicks;

    /** Where its body has been, for its model's neck and tail. */
    public final DragonFlightHistory flightHistory = new DragonFlightHistory();
    /** Its wingbeat, a full beat a unit, last tick and now. */
    public float oFlapTime;
    public float flapTime;
    /** How far it has settled onto its seat ({@link Settling}), last tick and now; unknown before its first tick. */
    private float oSettled = Float.NaN;
    private float settled = Float.NaN;
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
    /** How long a presence carrying its rider has dived since their eyes went below the shaft's mouth; -1 until they do. */
    private int belowTheMouth = -1;
    /** How long a presence has been landing or taking off. */
    private int perchTicks;
    /** Whether a presence taking off dives once it is up, its dragon having set off. */
    private boolean diveOnceUp;
    /** Where a presence lands, sits and takes off from; null while it flies. */
    private @Nullable Seat seat;
    /** The heading, as a mob's, a presence sits at on its seat, along the core's wall; NaN until it is picked, over the seat. */
    private float seatYaw = Float.NaN;
    /** The stop a presence's dragon is at, as it is seen loading or unloading there; null while it is at none, or neither loads nor unloads there. */
    private @Nullable StopShown stopShown;
    /** How many of that stop's passings it has shown, or let go by. */
    private int passingsShown;
    /** The first stack its dragon carries, which it hangs off a stop. */
    private Optional<Ware> carried = Optional.empty();
    /** Whether a presence holds still over the shaft while items move to or from its paws. */
    private boolean holdingStill;
    /** Whether a presence emerging brings up the rider its dragon carried through its dive, to set them down once out. */
    private boolean bringing;
    /** The Well its rider has picked to dive to, by strafing; null until they pick one. */
    @Nullable Place picked;
    /** What its rider was pressing last tick, so each strafe and jump acts once as it is pressed. */
    Input input = Input.EMPTY;

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

    /**
     * It flies, up and down too, as an Allay does, at the speed its flight gives it; a presence emerging or diving goes straight through anything
     * in its way, and one landing, sitting or taking off moves as it is set to, never through a block.
     */
    @Override
    public void travel(Vec3 input) {
        if (passing() || perch() != Perch.FLYING || holdingStill) {
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
        entityData.define(DATA_CARRYING, false);
        entityData.define(DATA_IN_THE_VOID, false);
        entityData.define(DATA_PERCH, (byte) Perch.FLYING.ordinal());
        entityData.define(DATA_PERCH_YAW, 0.0F);
        entityData.define(DATA_SEAT, Optional.empty());
        entityData.define(DATA_STAGE, (byte) LifeStage.YOUNG.ordinal());
        entityData.define(DATA_DYING, false);
        entityData.define(DATA_DIVE_LABEL, Component.empty());
        entityData.define(DATA_DIVE_FROM, 0L);
        entityData.define(DATA_DIVE_UNTIL, 0L);
        entityData.define(DATA_DIVE_COLOUR, DiveColour.VOID);
        entityData.define(DATA_HUNG, ItemStack.EMPTY);
        entityData.define(DATA_LINE, (byte) Line.MOTE.ordinal());
        entityData.define(DATA_FOLLOWERS, Followers.NONE.bits());
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

    /** Hatches it for {@code owner}, of the Mote line, where it stands: that is its home, and it starts growing. */
    public void hatchFor(UUID owner) {
        hatchFor(owner, Line.MOTE);
    }

    /** Hatches it for {@code owner}, of {@code line}, where it stands: that is its home, and it starts growing. */
    public void hatchFor(UUID owner, Line line) {
        this.owner = owner;
        setLine(line);
        holdForLine();
        setHomeTo(blockPosition(), HOME_RADIUS);
        ticksToGrow = VoidworksConfig.dragonlingGrowTicks();
    }

    /**
     * Makes it the presence of {@code record}, holding nothing that is saved or that the record reads, where its dragon is: emerging
     * at the spot's {@code emerges}, or already flying about its {@code over}.
     *
     * @param showing what the record shows, never {@link Showing#DIVING}
     */
    void showPresenceOf(VoidDragonRecord record, Showing showing, DragonPresences.Spot spot, LifeStage stage) {
        showStage(stage);
        showLoad(record);
        dragon = record.id();
        owner = record.owner();
        setLine(record.line());
        shownAt = record.rest();
        this.spot = spot;
        entityData.set(DATA_GROWN, true);
        var at = Vec3.atBottomCenterOf(showing == Showing.EMERGING ? spot.emerges() : spot.over());
        snapTo(at.x, at.y, at.z, getRandom().nextFloat() * 360.0F, 0.0F);
        if (showing == Showing.EMERGING) {
            pass(Showing.EMERGING);
            return;
        }
        // Back at a Well its dragon rests at, it is sitting already.
        var free = spot.seat().filter(this::roomToSit);
        if (showing == Showing.RESTING && free.isPresent()) {
            settle(Showing.RESTING);
            perchOn(free.get(), Perch.SITTING);
            seatYaw = free.get().headWay(getYRot()).toYRot();
            var on = Vec3.atBottomCenterOf(free.get().pos());
            snapTo(on.x, on.y, on.z, seatYaw, 0.0F);
            face(seatYaw);
            return;
        }
        follow(showing);
    }

    /** @return where the dragon whose presence it is was when it was shown; empty for a Dragonling */
    Optional<Place> shownAt() {
        return Optional.ofNullable(shownAt);
    }

    /** @return whether it is the presence of a dragon at {@code place} */
    boolean shows(Place place) {
        return place.equals(shownAt);
    }

    /**
     * Follows what its dragon's record shows now: it flies close about the Well while its dragon is at
     * work there, lands on it while it rests, room allowing, else flies about wider, and dives away as
     * its dragon dives, taking off first if it sits. Emerging, it carries on until it is out, and
     * diving, until it is gone.
     */
    void follow(Showing next) {
        if (showing == Showing.DIVING || next == Showing.DIVING) {
            if (showing != Showing.DIVING) {
                if (perch() == Perch.FLYING) {
                    pass(Showing.DIVING);
                } else {
                    takeOff(true);
                }
            }
            return;
        }
        if (showing == Showing.EMERGING) {
            return;
        }
        settle(next == Showing.RESTING ? Showing.RESTING : Showing.WORKING);
        boolean room = showing == Showing.RESTING && spot.seat().filter(this::roomToSit).isPresent();
        if (perch() == Perch.FLYING && room && !isVehicle()) {
            perchOn(spot.seat().get(), Perch.LANDING);
        } else if (perch().onTheSeat() && !room) {
            takeOff(false);
        }
    }

    /** @return what it shows of its dragon now: for a presence that has stopped following it, {@link Showing#DIVING} */
    public Showing showing() {
        return showing;
    }

    /** @return whether it is emerging or diving, flying straight through anything in its way */
    boolean passing() {
        return showing == Showing.EMERGING || showing == Showing.DIVING;
    }

    /** @return where it is as to its seat: flying, or landing on it, sitting on it or taking off from it */
    public Perch perch() {
        return Perch.values()[entityData.get(DATA_PERCH)];
    }

    /** @return the heading, as a mob's, it keeps as it lands, sits and takes off */
    public float perchYaw() {
        return entityData.get(DATA_PERCH_YAW);
    }

    /** @return where it lands and sits, while it lands, sits or takes off */
    public Optional<BlockPos> seatPos() {
        return entityData.get(DATA_SEAT);
    }

    /** @return how far, in blocks, {@code at} is from where it lands and sits, or infinitely far while it has no seat */
    public double fromSeat(Vec3 at) {
        return seatPos().map(seat -> Vec3.atBottomCenterOf(seat).distanceTo(at)).orElse(Double.POSITIVE_INFINITY);
    }

    private void setPerch(Perch perch) {
        entityData.set(DATA_PERCH, (byte) perch.ordinal());
    }

    /** @return whether nothing but what can be built over, such as air and grass, fills where its body would be on {@code seat} */
    private boolean roomToSit(Seat seat) {
        var body = getDimensions(getPose()).makeBoundingBox(Vec3.atBottomCenterOf(seat.pos())).deflate(1.0E-4);
        return BlockPos.betweenClosedStream(body).allMatch(pos -> level().getBlockState(pos).canBeReplaced());
    }

    /** It keeps to {@code seat}, coming in to land on it or sitting on it already, as {@code perch} says, its wandering set aside. */
    private void perchOn(Seat seat, Perch perch) {
        this.seat = seat;
        seatYaw = Float.NaN;
        perchTicks = 0;
        entityData.set(DATA_SEAT, Optional.of(seat.pos()));
        setPerch(perch);
        getNavigation().stop();
        moveControl.setWait();
        goalSelector.disableControlFlag(Goal.Flag.MOVE);
    }

    /** It rises off its seat, then dives if {@code dive} or its dragon set off already, else hovers for its rider or flies about. */
    private void takeOff(boolean dive) {
        if (perch() != Perch.TAKING_OFF) {
            perchTicks = 0;
            diveOnceUp = false;
        }
        diveOnceUp |= dive;
        setPerch(Perch.TAKING_OFF);
    }

    /**
     * Landing, it flies to over its seat and comes straight down onto it, or gives up and flies about if
     * it cannot get there; sitting, it stays put; taking off, it rises straight up to its spot's {@code risen},
     * and then dives, hovers for its rider or flies about.
     */
    private void keepPerch() {
        if (seat == null) {
            return;
        }
        var on = Vec3.atBottomCenterOf(seat.pos());
        switch (perch()) {
            case LANDING -> {
                if (++perchTicks > MOST_LANDING_TICKS) {
                    fly();
                    return;
                }
                boolean over = position().subtract(on).horizontalDistance() < OVER_THE_SEAT;
                var way = (over ? on : on.add(0, LANDING_HEIGHT, 0)).subtract(position());
                // It flies in head first, and once over its seat comes down along the wall, its head the way nearer its heading.
                if (over && Float.isNaN(seatYaw)) {
                    seatYaw = seat.headWay(getYRot()).toYRot();
                }
                face(over || way.horizontalDistanceSqr() < 1.0E-6 ? seatYaw : (float) (Mth.atan2(way.z, way.x) * Mth.RAD_TO_DEG) - 90.0F);
                if (over && way.lengthSqr() < 1.0E-4) {
                    setDeltaMovement(Vec3.ZERO);
                    setPerch(Perch.SITTING);
                    return;
                }
                setDeltaMovement(way.length() > LAND_SPEED ? way.normalize().scale(LAND_SPEED) : way);
            }
            case SITTING -> {
                face(seatYaw);
                setDeltaMovement(Vec3.ZERO);
            }
            case TAKING_OFF -> {
                if (!Float.isNaN(seatYaw)) {
                    face(seatYaw);
                }
                double up = spot.risen().getY() - getY();
                if (up > TAKE_OFF_SPEED && ++perchTicks <= MOST_TAKE_OFF_TICKS) {
                    setDeltaMovement(0, TAKE_OFF_SPEED, 0);
                    return;
                }
                setDeltaMovement(Vec3.ZERO);
                if (diveOnceUp) {
                    pass(Showing.DIVING);
                } else {
                    fly();
                }
            }
            case FLYING -> {
            }
        }
    }

    /** It turns its body and heading to {@code yaw}, as a mob's. */
    private void face(float yaw) {
        entityData.set(DATA_PERCH_YAW, yaw);
        setYRot(yaw);
        setYBodyRot(yaw);
    }

    /** It flies again, off its seat: about its spot, or hovering where it is while ridden. */
    private void fly() {
        leaveTheSeat();
        if (!isVehicle()) {
            goalSelector.enableControlFlag(Goal.Flag.MOVE);
        }
    }

    /** It is on the wing, its seat let go. */
    private void leaveTheSeat() {
        setPerch(Perch.FLYING);
        entityData.set(DATA_SEAT, Optional.empty());
        seat = null;
        seatYaw = Float.NaN;
        diveOnceUp = false;
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
        belowTheMouth = -1;
        leaveTheSeat();
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

    /**
     * Emerging, it rises to its spot's {@code risen} and then flies about its {@code over} at work;
     * diving, it goes down to its {@code under} and is gone into the void, unless it carries its rider,
     * whom it holds there, out of sight, until it sets them down. Its rider watches it start down, and
     * sees the void {@link #VOID_DELAY_TICKS} after their eyes go below the shaft's mouth, the first
     * open air over its rim, as it dives on, straight down past its {@code under} if it gets there first.
     */
    private void passOn(ServerLevel level) {
        if (showing == Showing.DIVING && carrying() && isVehicle() && !inTheVoid()) {
            diveIntoTheVoid();
            return;
        }
        var way = Vec3.atBottomCenterOf(showing == Showing.DIVING ? spot.under() : spot.risen()).subtract(position());
        double speed = showing == Showing.DIVING ? DIVE_SPEED : EMERGE_SPEED;
        if (way.length() > speed && ++passageTicks < MOST_PASSAGE_TICKS) {
            setDeltaMovement(way.normalize().scale(speed));
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        if (showing == Showing.DIVING) {
            if (carrying() && isVehicle()) {
                setInvisible(true);
                return;
            }
            burst(level);
            discard();
            return;
        }
        noPhysics = false;
        goalSelector.enableControlFlag(Goal.Flag.MOVE);
        goalSelector.enableControlFlag(Goal.Flag.LOOK);
        settle(Showing.WORKING);
        if (bringing) {
            // Out of the shaft, it sets the rider it brought down on the rim.
            bringing = false;
            ejectPassengers();
        }
    }

    /** @return how much a grown presence of a dragon at {@code stage} is scaled from {@link #GROWN_SCALE} */
    public static float size(LifeStage stage) {
        return switch (stage) {
            case YOUNG -> 0.8F;
            case ADULT -> 1.0F;
            case SENIOR -> 1.1F;
            case OLD -> 1.2F;
        };
    }

    /** @return its dragon's life stage, by which it is sized */
    public LifeStage stage() {
        return LifeStage.values()[entityData.get(DATA_STAGE)];
    }

    /** It shows its dragon at {@code stage}. */
    void showStage(LifeStage stage) {
        if (stage() != stage) {
            entityData.set(DATA_STAGE, (byte) stage.ordinal());
        }
    }

    /**
     * It shows what {@code record} carries: it is followed by butterflies of the grades in it, and hangs
     * its first stack under its body, nothing for motes or no load. At a stop where a Matter-line dragon
     * loads or unloads, it is seen to ({@link Handling}), as far through the stop as its record is.
     */
    void showLoad(VoidDragonRecord record) {
        entityData.set(DATA_FOLLOWERS, record.load().followers().bits());
        carried = record.load().firstStack();
        var stop = record.trip().filter(trip -> trip.divingTo().isEmpty())
                .flatMap(trip -> trip.handling().map(handling -> new StopShown(trip.began(VoidworksConfig.dragonStopTicks()), handling)))
                .orElse(null);
        if (!Objects.equals(stop, stopShown)) {
            stopShown = stop;
            passingsShown = 0;
        }
        if (level() instanceof ServerLevel level) {
            hang(stopShown == null ? carried : stopShown.handling().hangs(into(level), carried, VoidworksConfig.dragonStopTicks()));
        }
    }

    /**
     * A tick of its stop, as its dragon's record times it: it hangs what it hangs then, shows each item
     * that starts to move, or one on its way, if it was not here to show it start, and holds still over the
     * shaft while they move, unless it is ridden or not at work there.
     */
    private void showHandling(ServerLevel level) {
        if (stopShown == null) {
            hang(carried);
            holdingStill = false;
            return;
        }
        var handling = stopShown.handling();
        int stopTicks = VoidworksConfig.dragonStopTicks();
        long into = into(level);
        hang(handling.hangs(into, carried, stopTicks));
        var passing = handling.passing();
        while (passingsShown < passing.size() && handling.start(passingsShown, stopTicks) <= into) {
            long since = into - handling.start(passingsShown, stopTicks);
            if (since < Handling.PASS_TICKS && !isInvisible()) {
                passer.show(this, passing.get(passingsShown), handling.loading(), (int) since);
            }
            passingsShown++;
        }
        holdingStill = handling.holdsStill(into, stopTicks) && showing == Showing.WORKING && perch() == Perch.FLYING && !isVehicle();
    }

    /** @return how many ticks into its stop its dragon is, by the game time its record runs on */
    private long into(ServerLevel level) {
        return level.getServer().overworld().getGameTime() - Objects.requireNonNull(stopShown).began();
    }

    /** It goes to over the middle of the shaft, along the core's wall, and holds still there. */
    private void holdStill() {
        getNavigation().stop();
        var way = Vec3.atBottomCenterOf(spot.over()).subtract(position());
        setDeltaMovement(way.length() > HOLD_SPEED ? way.normalize().scale(HOLD_SPEED) : way);
        spot.seat().ifPresent(seat -> {
            float yaw = seat.headWay(getYRot()).toYRot();
            setYRot(yaw);
            setYBodyRot(yaw);
        });
    }

    /** @return the middle of the mouth of the shaft it emerges from and dives into: the first open air over its Well's rim */
    Vec3 mouth() {
        return Vec3.atBottomCenterOf(spot.emerges());
    }

    /** @return how it shows each item seen moving to or from its paws */
    public static Passer passer() {
        return passer;
    }

    /** It shows each item seen moving to or from its paws with {@code show}: to the players watching it, unless a watcher hears it first. */
    public static void onPassing(Passer show) {
        passer = show;
    }

    /** Shows {@code passing} moving between its hatch and {@code dragon}'s paws, {@code elapsed} ticks on already. */
    @FunctionalInterface
    public interface Passer {
        void show(VoidDragon dragon, Passing passing, boolean loading, int elapsed);
    }

    /** The stop its dragon is at: when it {@code began}, and how it is seen loading or unloading there. */
    private record StopShown(long began, Handling handling) {
    }

    /** @return the butterflies following it, synced to every client: of each grade its dragon carries, or none */
    public Followers followers() {
        return new Followers(entityData.get(DATA_FOLLOWERS));
    }

    /** On a client, a butterfly of each grade it carries now and then joins those following it about its body, while it is seen and not dying of old age. */
    private void gainFollowers() {
        if (isInvisible() || dying()) {
            return;
        }
        var box = getBoundingBox();
        for (int grade : followers().grades()) {
            if (random.nextFloat() < FOLLOWER_CHANCE) {
                follower.show(this, grade, new Vec3(box.minX + random.nextDouble() * box.getXsize(), box.minY + random.nextDouble() * box.getYsize(),
                        box.minZ + random.nextDouble() * box.getZsize()).subtract(position()));
            }
        }
    }

    /** Has a watcher show the butterflies following a Mote-line presence with {@code show}, as the client does. */
    public static void onFollower(Follower show) {
        follower = show;
    }

    /** Shows one butterfly of {@code grade} following a presence, keeping {@code offset} off its position. */
    @FunctionalInterface
    public interface Follower {
        void show(VoidDragon dragon, int grade, Vec3 offset);
    }

    /** As a Dragonling, which is all that is ever saved, it holds a chest under its body if it is of the Matter line, and nothing else. */
    private void holdForLine() {
        hang(line() == Line.MATTER ? new ItemStack(Items.CHEST) : ItemStack.EMPTY);
    }

    /** It hangs a stack of {@code ware}, or nothing. */
    private void hang(Optional<Ware> ware) {
        hang(ware.map(hung -> StackWares.stack(hung, registryAccess())).orElse(ItemStack.EMPTY));
    }

    /** It hangs {@code stack}, synced only if it is not what it hangs already. */
    private void hang(ItemStack stack) {
        if (!ItemStack.matches(hung(), stack)) {
            entityData.set(DATA_HUNG, stack);
        }
    }

    /** @return what it hangs under its body, synced to every client: its dragon's first carried stack, a Dragonling's chest, or nothing */
    public ItemStack hung() {
        return entityData.get(DATA_HUNG);
    }

    /** @return whether it plays its dragon's death of old age */
    public boolean dying() {
        return entityData.get(DATA_DYING);
    }

    /**
     * @return how far it has settled onto its seat, {@code partialTicks} on, 0 on the wing and 1 sitting,
     *         easing as it lands or takes off; before its first tick, as far as where it is says, so one
     *         seen first sitting is wholly settled
     */
    public float settled(float partialTicks) {
        return Float.isNaN(settled) ? Settling.toward(perch(), fromSeat(position())) : Mth.lerp(partialTicks, oSettled, settled);
    }

    /** @return how many ticks it has been dying, {@code partialTicks} on; 0 while it is not */
    public float deathTime(float partialTicks) {
        return dying() ? deathTicks + partialTicks : 0.0F;
    }

    /**
     * Its dragon has died of old age: it holds still over its Well, rises in the Ender Dragon's death,
     * beams and sound, and is gone, leaving nothing.
     */
    void dieOfOldAge() {
        entityData.set(DATA_DYING, true);
        ejectPassengers();
        leaveTheSeat();
        noPhysics = true;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        goalSelector.disableControlFlag(Goal.Flag.MOVE);
        goalSelector.disableControlFlag(Goal.Flag.LOOK);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.NEUTRAL, 5.0F, 1.0F);
        }
    }

    /** A tick of its death: it rises, and at the end is gone. */
    private void dieOn() {
        deathTicks++;
        setDeltaMovement(Vec3.ZERO);
        setPos(getX(), getY() + DEATH_RISE, getZ());
        if (deathTicks >= DEATH_TICKS && level() instanceof ServerLevel) {
            discard();
        }
    }

    /** It dives now, carrying its rider, whom it holds through the dive, showing them {@code shown}. */
    void carry(DiveShown shown) {
        entityData.set(DATA_DIVE_LABEL, shown.label());
        entityData.set(DATA_DIVE_FROM, shown.from());
        entityData.set(DATA_DIVE_UNTIL, shown.until());
        entityData.set(DATA_DIVE_COLOUR, shown.colour());
        entityData.set(DATA_CARRYING, true);
        getPassengers().forEach(DragonRiding::hide);
        pass(Showing.DIVING);
    }

    /** It brings the riders handed to it up out of its Well's shaft as it emerges, and sets them down on the rim once out. */
    void bring() {
        bringing = true;
    }

    /** It lets its rider go, as it sets them down. */
    void release() {
        entityData.set(DATA_CARRYING, false);
        entityData.set(DATA_IN_THE_VOID, false);
        bringing = false;
    }

    /** @return whether it holds its rider, who cannot get off: through its dragon's dive, or coming up out of it */
    boolean holding() {
        return carrying() || bringing;
    }

    /** @return whether it carries its rider through its dragon's dive */
    public boolean carrying() {
        return entityData.get(DATA_CARRYING);
    }

    /** @return whether it has taken the rider it carries down into the shaft, where they see only the void */
    public boolean inTheVoid() {
        return entityData.get(DATA_IN_THE_VOID);
    }

    /**
     * A tick of its dive with its rider still seeing the world: it goes down toward its {@code under},
     * then on straight down, until {@link #VOID_DELAY_TICKS} after their eyes went below the shaft's
     * mouth, when it holds still, out of sight, and they see only the void.
     */
    private void diveIntoTheVoid() {
        if (belowTheMouth < 0 && getFirstPassenger() instanceof Entity rider && eyesOf(rider) < spot.emerges().getY()) {
            belowTheMouth = 0;
        }
        if (belowTheMouth >= 0 && belowTheMouth++ >= VOID_DELAY_TICKS || ++passageTicks >= MOST_PASSAGE_TICKS) {
            entityData.set(DATA_IN_THE_VOID, true);
            setDeltaMovement(Vec3.ZERO);
            setInvisible(true);
            return;
        }
        var way = Vec3.atBottomCenterOf(spot.under()).subtract(position());
        setDeltaMovement(way.length() > DIVE_SPEED ? way.normalize().scale(DIVE_SPEED) : new Vec3(0, -DIVE_SPEED, 0));
    }

    /** @return how high {@code rider}'s eyes are on its back, where it has moved to this tick, as vanilla seats a passenger */
    private double eyesOf(Entity rider) {
        return getPassengerRidingPosition(rider).y - rider.getVehicleAttachmentPoint(this).y + rider.getEyeHeight();
    }

    /** @return what its rider is shown of the dive it carries them through, while it does */
    public DiveShown diveShown() {
        return new DiveShown(entityData.get(DATA_DIVE_LABEL), entityData.get(DATA_DIVE_FROM), entityData.get(DATA_DIVE_UNTIL),
                entityData.get(DATA_DIVE_COLOUR));
    }

    /**
     * Its rider sits on its back where its wings meet, turned as its model is, and once in the void
     * stands at its feet, down the shaft with it.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        if (inTheVoid()) {
            return passenger.getVehicleAttachmentPoint(this);
        }
        // Its model is turned by the heading its flight history keeps, half a turn from its own, so its tail lies that way.
        float heading = Float.isNaN(flightYaw) ? yBodyRot + 180.0F : flightYaw;
        return SEAT.yRot(-heading * Mth.DEG_TO_RAD);
    }

    /** @return whether its owner rides it at its dragon's stop, before any dive */
    boolean ridden() {
        return !passing() && owner != null && getFirstPassenger() instanceof Player rider && rider.getUUID().equals(owner);
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

    /** @return a Dragonling's Line, which its egg fixed; a presence's is its dragon record's; synced to every client */
    public Line line() {
        return Line.values()[entityData.get(DATA_LINE)];
    }

    private void setLine(Line line) {
        entityData.set(DATA_LINE, (byte) line.ordinal());
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
        flapTime = flap(flapTime, perch() == Perch.SITTING);
        oSettled = settled(1.0F);
        settled = Settling.settle(oSettled, perch(), fromSeat(position()));
        recordFlight();
        if (level().isClientSide()) {
            gainFollowers();
        }
        if (dying()) {
            dieOn();
            return;
        }
        if (level() instanceof ServerLevel level) {
            if (carrying()) {
                getPassengers().forEach(DragonRiding::hide);
            }
            if (grown()) {
                showHandling(level);
            }
            if (passing()) {
                passOn(level);
                return;
            }
            keepPerch();
            if (passing()) {
                return;
            }
            if (holdingStill) {
                holdStill();
            }
            if (getFirstPassenger() instanceof ServerPlayer rider) {
                DragonRiding.steer(level, this, rider);
            } else if (!grown() && owner != null && --ticksToGrow <= 0) {
                growUp(level);
            }
        }
    }

    /**
     * @return its wingbeat a tick on from {@code time}: beating steadily, as the Ender Dragon's, or
     *         sitting, finishing its beat to hold its wings down and still
     */
    static float flap(float time, boolean sitting) {
        if (!sitting) {
            return time + 0.1F;
        }
        return Math.min(time + 0.1F, Mth.ceil(time - WINGS_DOWN) + WINGS_DOWN);
    }

    /**
     * Records where its body has been, for its model, which banks and bends by how fast that turns and
     * climbs: a mob's body turns in jumps the Ender Dragon's never does, so the heading recorded turns
     * at most {@link #MAX_TURN} degrees a tick and the height eases toward its own.
     */
    private void recordFlight() {
        // The Ender Dragon's model faces the other way from a mob's heading, as the Ender Dragon flies; off the wing, it keeps to its perch's.
        float heading = Mth.wrapDegrees((perch() == Perch.FLYING ? yBodyRot : perchYaw()) + 180.0F);
        if (Float.isNaN(flightYaw)) {
            flightYaw = heading;
            flightY = getY();
        }
        flightYaw = Mth.wrapDegrees(flightYaw + Mth.clamp(Mth.wrapDegrees(heading - flightYaw), -MAX_TURN, MAX_TURN));
        flightY += (getY() - flightY) * 0.2;
        flightHistory.record(flightY, flightYaw);
    }

    /**
     * Grown, its owner mounts it at a Well of theirs ({@link DragonRiding}). Young, its owner, holding a
     * Void Siphon set to empty, feeds it; anyone else, or a Siphon set to charge, does not, and a Siphon
     * used on it never harvests.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (dying()) {
            return InteractionResult.PASS;
        }
        if (grown() && !player.isSecondaryUseActive()) {
            // Answered on the client, which does not know its owner, as feeding is.
            if (!(level() instanceof ServerLevel level)) {
                return InteractionResult.SUCCESS;
            }
            return DragonRiding.mount(level, this, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
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
        var record = SavedVoidDragons.of(level.getServer()).add(owner, line(), Place.of(level, blockPosition()));
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

    /** Only a presence is ridden, by one rider. */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return grown() && super.canAddPassenger(passenger);
    }

    /** Ridden, it hovers where it is, its wandering set aside until its rider gets off; sitting or landing, it takes off to hover. */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        getNavigation().stop();
        moveControl.setWait();
        setDeltaMovement(Vec3.ZERO);
        goalSelector.disableControlFlag(Goal.Flag.MOVE);
        if (perch().onTheSeat()) {
            takeOff(false);
        }
    }

    /**
     * Its goals move it only while it flies free, neither emerging, diving, landing, sitting, taking off
     * nor ridden, and turn its head but while it emerges or dives. A mob sets its goals free again every
     * few ticks, so they are set by what it is doing.
     */
    @Override
    protected void updateControlFlags() {
        super.updateControlFlags();
        goalSelector.setControlFlag(Goal.Flag.MOVE, !passing() && perch() == Perch.FLYING && !isVehicle() && !holdingStill);
        goalSelector.setControlFlag(Goal.Flag.LOOK, !passing());
    }

    /** Its rider off, it flies about again, and lands again if its dragon still rests. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!passing() && perch() == Perch.FLYING) {
            goalSelector.enableControlFlag(Goal.Flag.MOVE);
        }
    }

    /** A rider who gets off before the dive is set down on the rim of the Well its dragon is at. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (shownAt != null && level() instanceof ServerLevel level && level.dimension().identifier().equals(shownAt.dimension())) {
            return DragonRiding.rim(level, shownAt.pos()).at();
        }
        return super.getDismountLocationForPassenger(passenger);
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

    /** A presence pushes no one, so a player stands on the rim it sits astride or under it hovering; a Dragonling pushes as any mob does. */
    @Override
    protected void pushEntities() {
        if (!grown()) {
            super.pushEntities();
        }
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
        return grown() ? dimensions.scale(GROWN_SCALE * size(stage())) : dimensions;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_GROWN.equals(accessor) || DATA_STAGE.equals(accessor)) {
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
        output.store(LINE_KEY, Line.CODEC, line());
        output.putLong(TICKS_TO_GROW_KEY, ticksToGrow);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read(OWNER_KEY, UUIDUtil.CODEC).orElse(null);
        // One hatched before Lines is of the Mote line.
        setLine(input.read(LINE_KEY, Line.CODEC).orElse(Line.MOTE));
        holdForLine();
        // One hatched before Dragonlings grew starts growing now.
        ticksToGrow = input.getLong(TICKS_TO_GROW_KEY).orElseGet(() -> (long) VoidworksConfig.dragonlingGrowTicks());
    }

    /** Where a presence is as to its seat on a Well its dragon rests at. */
    public enum Perch {
        /** On the wing, as it flies about, emerges, dives or hovers for its rider. */
        FLYING,
        /** Coming in over its seat and down onto it. */
        LANDING,
        /** Sitting on its seat, in the Ender Dragon's sitting pose. */
        SITTING,
        /** Rising off its seat, to dive, hover for its rider or fly about. */
        TAKING_OFF;

        /** @return whether it is coming down onto its seat or sitting on it */
        public boolean onTheSeat() {
            return this == LANDING || this == SITTING;
        }

        /** @return whether it is landing or taking off, its neck easing between flight and sitting */
        public boolean settling() {
            return this == LANDING || this == TAKING_OFF;
        }
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
