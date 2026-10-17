// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.butterfly.Butterflies;
import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.github._5thlayer.voidworks.butterfly.MoteCrawl;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * One butterfly of a {@link MoteFlight}: it flutters out of the source, then flies to the target,
 * following the target entity if it has one, and vanishes on arrival. Or one of a {@link MoteCrawl},
 * smaller, walking straight along a network's faces. Or one drifting in a Void Well's shaft
 * ({@link #drift}), under its fluid's surface, until it fades, or one following a Mote-line dragon
 * ({@link #follow}), keeping its place about it. Its wings flap through the sprite's frames, its
 * grade tints it, and the motes it stands for size it.
 */
final class ButterflyParticle extends SingleQuadParticle {

    /** Ticks a full wingbeat takes, through every frame and back. */
    private static final int WINGBEAT = 6;
    /** The longest a butterfly flies before it vanishes, in ticks, should it never arrive. */
    private static final int MAX_LIFETIME = 200;
    /** Blocks per tick at full speed. */
    private static final double SPEED = 0.35;
    /** Blocks per tick a walking butterfly goes: a block in a step of conduction, by default. */
    private static final double CRAWL_SPEED = 0.1;
    /** How much smaller a walking butterfly is than a flying one of as many motes. */
    private static final float CRAWL_SCALE = 0.5F;
    /** How long a butterfly drifts in a Well's shaft, in ticks. */
    static final int DRIFT_LIFETIME = 100;
    /** Blocks per tick a drifting butterfly goes, at most. */
    private static final double DRIFT_SPEED = 0.03;
    /** How far a drifting butterfly keeps from the shaft's walls, floor and the fluid's surface, at most: half the fluid's depth where it is shallower. */
    private static final double DRIFT_MARGIN = 0.15;
    /** How much a drifting butterfly's speed wanders each tick, at most, each way. */
    private static final double DRIFT_WANDER = 0.005;
    /** How much its flutter lifts and drops it each tick, at most. */
    private static final double DRIFT_FLUTTER = 0.005;
    /** How fast its flutter goes, in radians a tick. */
    private static final double FLUTTER_RATE = 0.9;
    /** The motes a butterfly that stands for none, drifting or following a dragon, is sized as. */
    private static final int UNCOUNTED_SIZE = 8;
    /** How long a butterfly follows a Mote-line dragon, in ticks, before it vanishes. */
    private static final int FOLLOW_LIFETIME = 60;
    /** Blocks per tick a butterfly following a dragon goes at most: faster than the dragon dives, so it keeps up. */
    private static final double FOLLOW_SPEED = 0.7;
    /** How far, in blocks, a dragon must leap from a butterfly following it to leave it behind, as when it is moved at once. */
    private static final double FOLLOW_LOST = 8.0;

    /** How a butterfly goes, each kind its own way. */
    private sealed interface Way permits Flight, Crawl, Drift, Follow {
    }

    /** A flight's: to {@code target}, following {@code targetEntity} there if it has one, which moves the target. */
    private static final class Flight implements Way {

        private Vec3 target;
        private final int targetEntity;

        Flight(Vec3 target, int targetEntity) {
            this.target = target;
            this.targetEntity = targetEntity;
        }
    }

    /** A step of conduction's: walking straight to {@code target}. */
    private record Crawl(Vec3 target) implements Way {
    }

    /** A shaft's: within {@code room}, under a {@code surface} that rises and falls. */
    private record Drift(AABB room, DoubleSupplier surface) implements Way {

        /** @return how far it keeps from the floor and the surface: its margin, or half the fluid's depth where that is less */
        double margin(double surface) {
            return Math.min(DRIFT_MARGIN, (surface - room.minY) / 2);
        }
    }

    /** A Mote-line dragon's follower's: keeping {@code offset} off the position of the entity {@code dragon}. */
    private record Follow(int dragon, Vec3 offset) implements Way {
    }

    private final SpriteSet sprites;
    private final int phase;
    private final Way way;

    ButterflyParticle(ClientLevel level, Vec3 source, Vec3 target, int targetEntity, int grade, int motes, SpriteSet sprites) {
        this(level, source, grade, motes, sprites, new Flight(target, targetEntity));
    }

    private ButterflyParticle(ClientLevel level, Vec3 source, int grade, int motes, SpriteSet sprites, Way way) {
        super(level, source.x, source.y, source.z, sprites.first());
        this.way = way;
        this.sprites = sprites;
        this.phase = random.nextInt(WINGBEAT);
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1;
        this.lifetime = MAX_LIFETIME;
        this.quadSize = Butterflies.scale(motes);
        int rgb = GradeColor.of(grade);
        setColor((rgb >> 16 & 0xFF) / 255F, (rgb >> 8 & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        switch (way) {
            case Follow follow -> this.lifetime = FOLLOW_LIFETIME;
            case Drift drift -> {
                this.lifetime = DRIFT_LIFETIME;
                this.xd = (random.nextDouble() - 0.5) * 2 * DRIFT_SPEED;
                this.yd = (random.nextDouble() - 0.5) * 2 * DRIFT_SPEED;
                this.zd = (random.nextDouble() - 0.5) * 2 * DRIFT_SPEED;
            }
            case Crawl crawl -> {
                this.quadSize *= CRAWL_SCALE;
                var step = crawl.target().subtract(source).normalize().scale(CRAWL_SPEED);
                this.xd = step.x;
                this.yd = step.y;
                this.zd = step.z;
            }
            case Flight flight -> {
                // A scattered take-off, so a harvest's butterflies part before they converge.
                this.xd = (random.nextDouble() - 0.5) * 0.3;
                this.yd = random.nextDouble() * 0.2 + 0.05;
                this.zd = (random.nextDouble() - 0.5) * 0.3;
            }
        }
        setSprite(frame());
    }

    /** Shows a step of conduction's butterflies, one per size its count splits into, walking. */
    static void crawl(ClientLevel level, MoteCrawl crawl, SpriteSet sprites, Consumer<ButterflyParticle> add) {
        for (int motes : Butterflies.sizes(crawl.count())) {
            add.accept(new ButterflyParticle(level, crawl.source(), crawl.grade(), motes, sprites, new Crawl(crawl.target())));
        }
    }

    /**
     * @return one butterfly of {@code grade} drifting in a Well's shaft: somewhere in {@code room}
     *         under {@code surface}, the height its fluid stands at, which it stays under as it rises
     *         and falls, and fading once the fluid is gone; none while there is no fluid
     */
    static @Nullable ButterflyParticle drift(ClientLevel level, AABB room, DoubleSupplier surface, int grade, SpriteSet sprites) {
        var drift = new Drift(room, surface);
        double top = surface.getAsDouble();
        if (top <= room.minY) {
            return null;
        }
        double margin = drift.margin(top);
        var random = level.getRandom();
        var at = new Vec3(Mth.lerp(random.nextDouble(), room.minX + DRIFT_MARGIN, room.maxX - DRIFT_MARGIN),
                Mth.lerp(random.nextDouble(), room.minY + margin, top - margin),
                Mth.lerp(random.nextDouble(), room.minZ + DRIFT_MARGIN, room.maxZ - DRIFT_MARGIN));
        return new ButterflyParticle(level, at, grade, UNCOUNTED_SIZE, sprites, drift);
    }

    /**
     * @return one butterfly of {@code grade} following {@code dragon}, from {@code offset} off its
     *         position, where it keeps as the dragon flies, until it vanishes, the dragon is gone or
     *         out of sight, or the dragon leaps beyond {@link #FOLLOW_LOST} of it
     */
    static ButterflyParticle follow(ClientLevel level, Entity dragon, Vec3 offset, int grade, SpriteSet sprites) {
        return new ButterflyParticle(level, dragon.position().add(offset), grade, UNCOUNTED_SIZE, sprites, new Follow(dragon.getId(), offset));
    }

    /** Shows a flight's butterflies, one per size its count splits into. */
    static void show(ClientLevel level, MoteFlight flight, SpriteSet sprites, Consumer<ButterflyParticle> add) {
        for (int motes : Butterflies.sizes(flight.count())) {
            add.accept(new ButterflyParticle(level, flight.source(), flight.target(), flight.targetEntity(),
                    flight.grade(), motes, sprites));
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        switch (way) {
            case Drift drift -> drift(drift);
            case Follow follow -> follow(follow);
            case Crawl crawl -> crawl(crawl);
            case Flight flight -> fly(flight);
        }
    }

    /** Keeps its place about the dragon it follows; gone with the dragon, out of sight with it, or left behind by a leap. */
    private void follow(Follow follow) {
        var dragon = level.getEntity(follow.dragon());
        if (dragon == null || dragon.isRemoved() || dragon.isInvisible()) {
            remove();
            return;
        }
        var toPlace = dragon.position().add(follow.offset()).subtract(x, y, z);
        if (toPlace.length() > FOLLOW_LOST) {
            remove();
            return;
        }
        flyToward(toPlace, FOLLOW_SPEED, 1);
    }

    /** Straight along the face, at a walk, until it is there. */
    private void crawl(Crawl crawl) {
        if (crawl.target().distanceTo(new Vec3(x, y, z)) <= CRAWL_SPEED) {
            remove();
            return;
        }
        move(xd, yd, zd);
        setSprite(frame());
    }

    /** Flies to its target, steering ever harder, and vanishes on arrival. */
    private void fly(Flight flight) {
        if (flight.targetEntity != MoteFlight.NO_ENTITY) {
            var entity = level.getEntity(flight.targetEntity);
            if (entity != null) {
                flight.target = MoteFlight.aimAt(entity);
            }
        }
        var toTarget = flight.target.subtract(x, y, z);
        if (toTarget.length() < 0.3) {
            remove();
            return;
        }
        flyToward(toTarget, SPEED, Math.min(1, age / 20.0));
    }

    /** Steers along {@code toTarget} at up to {@code speed}, as hard as {@code pull}, with a flutter on top, and flies on. */
    private void flyToward(Vec3 toTarget, double speed, double pull) {
        double distance = toTarget.length();
        var wanted = distance > 0 ? toTarget.scale(Math.min(speed, distance) / distance) : Vec3.ZERO;
        xd += (wanted.x - xd) * 0.25 * pull;
        yd += (wanted.y - yd) * 0.25 * pull + Math.sin((age + phase) * 0.9) * 0.02;
        zd += (wanted.z - zd) * 0.25 * pull;
        move(xd, yd, zd);
        setSprite(frame());
    }

    /** Wanders slowly about the shaft, turning back from its walls, its floor and the fluid's surface, which it never rises above; gone with the fluid. */
    private void drift(Drift drift) {
        var room = drift.room();
        double surface = drift.surface().getAsDouble();
        if (surface <= room.minY) {
            remove();
            return;
        }
        xd = Mth.clamp(xd + (random.nextDouble() - 0.5) * 2 * DRIFT_WANDER, -DRIFT_SPEED, DRIFT_SPEED);
        yd = Mth.clamp(yd + (random.nextDouble() - 0.5) * 2 * DRIFT_WANDER + Math.sin((age + phase) * FLUTTER_RATE) * DRIFT_FLUTTER, -DRIFT_SPEED,
                DRIFT_SPEED);
        zd = Mth.clamp(zd + (random.nextDouble() - 0.5) * 2 * DRIFT_WANDER, -DRIFT_SPEED, DRIFT_SPEED);
        double margin = drift.margin(surface);
        setPos(turnBack(x, xd, room.minX + DRIFT_MARGIN, room.maxX - DRIFT_MARGIN, v -> xd = v),
                turnBack(y, yd, room.minY + margin, surface - margin, v -> yd = v),
                turnBack(z, zd, room.minZ + DRIFT_MARGIN, room.maxZ - DRIFT_MARGIN, v -> zd = v));
        setSprite(frame());
    }

    /** @return where {@code at} goes, moving {@code by}, kept between {@code low} and {@code high}; its speed turned back as it meets either */
    private static double turnBack(double at, double by, double low, double high, DoubleConsumer speed) {
        double next = at + by;
        if (next < low || next > high) {
            speed.accept(-by);
        }
        return Mth.clamp(next, low, high);
    }

    /** The wing frame now: down through the frames and back up, so the wings beat. */
    private TextureAtlasSprite frame() {
        int t = (age + phase) % WINGBEAT;
        int half = WINGBEAT / 2;
        int step = t <= half ? t : WINGBEAT - t;
        return sprites.get(step, half);
    }

    @Override
    public int getLightCoords(float partialTick) {
        // Void glows: a butterfly reads in the dark.
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
