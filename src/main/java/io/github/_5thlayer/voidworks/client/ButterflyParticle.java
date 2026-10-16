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
 * ({@link #drift}), under its fluid's surface, until it fades. Its wings flap through the sprite's frames, its
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
    /** The motes a drifting butterfly is sized as, though it stands for none. */
    private static final int DRIFT_SIZE = 8;

    /** Where a butterfly drifts: within {@code room}, under a {@code surface} that rises and falls. */
    private record Drift(AABB room, DoubleSupplier surface) {

        /** @return how far it keeps from the floor and the surface: its margin, or half the fluid's depth where that is less */
        double margin(double surface) {
            return Math.min(DRIFT_MARGIN, (surface - room.minY) / 2);
        }
    }

    private final SpriteSet sprites;
    private final int targetEntity;
    private Vec3 target;
    private final int phase;
    private final boolean crawling;
    private final @Nullable Drift drift;

    ButterflyParticle(ClientLevel level, Vec3 source, Vec3 target, int targetEntity, int grade, int motes, SpriteSet sprites) {
        this(level, source, target, targetEntity, grade, motes, sprites, false, null);
    }

    private ButterflyParticle(ClientLevel level, Vec3 source, Vec3 target, int targetEntity, int grade, int motes, SpriteSet sprites,
            boolean crawling, @Nullable Drift drift) {
        super(level, source.x, source.y, source.z, sprites.first());
        this.crawling = crawling;
        this.drift = drift;
        this.sprites = sprites;
        this.target = target;
        this.targetEntity = targetEntity;
        this.phase = random.nextInt(WINGBEAT);
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1;
        this.lifetime = MAX_LIFETIME;
        this.quadSize = Butterflies.scale(motes);
        int rgb = GradeColor.of(grade);
        setColor((rgb >> 16 & 0xFF) / 255F, (rgb >> 8 & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        if (drift != null) {
            this.lifetime = DRIFT_LIFETIME;
            this.xd = (random.nextDouble() - 0.5) * 2 * DRIFT_SPEED;
            this.yd = (random.nextDouble() - 0.5) * 2 * DRIFT_SPEED;
            this.zd = (random.nextDouble() - 0.5) * 2 * DRIFT_SPEED;
        } else if (crawling) {
            this.quadSize *= CRAWL_SCALE;
            var step = target.subtract(source).normalize().scale(CRAWL_SPEED);
            this.xd = step.x;
            this.yd = step.y;
            this.zd = step.z;
        } else {
            // A scattered take-off, so a harvest's butterflies part before they converge.
            this.xd = (random.nextDouble() - 0.5) * 0.3;
            this.yd = random.nextDouble() * 0.2 + 0.05;
            this.zd = (random.nextDouble() - 0.5) * 0.3;
        }
        setSprite(frame());
    }

    /** Shows a step of conduction's butterflies, one per size its count splits into, walking. */
    static void crawl(ClientLevel level, MoteCrawl crawl, SpriteSet sprites, Consumer<ButterflyParticle> add) {
        for (int motes : Butterflies.sizes(crawl.count())) {
            add.accept(new ButterflyParticle(level, crawl.source(), crawl.target(), MoteFlight.NO_ENTITY, crawl.grade(), motes, sprites,
                    true, null));
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
        return new ButterflyParticle(level, at, at, MoteFlight.NO_ENTITY, grade, DRIFT_SIZE, sprites, false, drift);
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
        if (drift != null) {
            drift(drift);
            return;
        }
        if (targetEntity != MoteFlight.NO_ENTITY) {
            var entity = level.getEntity(targetEntity);
            if (entity != null) {
                target = MoteFlight.aimAt(entity);
            }
        }
        var toTarget = target.subtract(x, y, z);
        double distance = toTarget.length();
        if (crawling) {
            // Straight along the face, at a walk, until it is there.
            if (distance <= CRAWL_SPEED) {
                remove();
                return;
            }
            move(xd, yd, zd);
            setSprite(frame());
            return;
        }
        if (distance < 0.3) {
            remove();
            return;
        }
        // Steer ever harder toward the target, with a flutter on top.
        double pull = Math.min(1, age / 20.0);
        var wanted = toTarget.scale(Math.min(SPEED, distance) / distance);
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
