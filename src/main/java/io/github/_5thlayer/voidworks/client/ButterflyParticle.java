// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import io.github._5thlayer.voidworks.butterfly.Butterflies;
import io.github._5thlayer.voidworks.butterfly.GradeColor;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * One butterfly of a {@link MoteFlight}: it flutters out of the source, then flies to the target,
 * following the target entity if it has one, and vanishes on arrival. Its wings flap through the
 * sprite's frames, its grade tints it, and the motes it stands for size it.
 */
final class ButterflyParticle extends SingleQuadParticle {

    /** Ticks a full wingbeat takes, through every frame and back. */
    private static final int WINGBEAT = 6;
    /** The longest a butterfly flies before it vanishes, in ticks, should it never arrive. */
    private static final int MAX_LIFETIME = 200;
    /** Blocks per tick at full speed. */
    private static final double SPEED = 0.35;

    private final SpriteSet sprites;
    private final int targetEntity;
    private Vec3 target;
    private final int phase;

    ButterflyParticle(ClientLevel level, Vec3 source, Vec3 target, int targetEntity, int grade, int motes, SpriteSet sprites) {
        super(level, source.x, source.y, source.z, sprites.first());
        this.sprites = sprites;
        this.target = target;
        this.targetEntity = targetEntity;
        this.phase = random.nextInt(WINGBEAT);
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1;
        this.lifetime = MAX_LIFETIME;
        // 1, 8 and 64 motes read small, middling and large: half a block at most.
        this.quadSize = switch (motes) {
            case 64 -> 0.5F;
            case 8 -> 0.3F;
            default -> 0.17F;
        };
        int rgb = GradeColor.of(grade);
        setColor((rgb >> 16 & 0xFF) / 255F, (rgb >> 8 & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        // A scattered take-off, so a harvest's butterflies part before they converge.
        this.xd = (random.nextDouble() - 0.5) * 0.3;
        this.yd = random.nextDouble() * 0.2 + 0.05;
        this.zd = (random.nextDouble() - 0.5) * 0.3;
        setSprite(frame());
    }

    /** Shows a flight's butterflies, one per size its count splits into. */
    static void show(ClientLevel level, MoteFlight flight, SpriteSet sprites, java.util.function.Consumer<ButterflyParticle> add) {
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
        if (targetEntity != MoteFlight.NO_ENTITY) {
            var entity = level.getEntity(targetEntity);
            if (entity != null) {
                target = entity.position().add(0, entity.getBbHeight() / 2, 0);
            }
        }
        var toTarget = target.subtract(x, y, z);
        double distance = toTarget.length();
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

    /** The wing frame now: down through the frames and back up, so the wings beat. */
    private net.minecraft.client.renderer.texture.TextureAtlasSprite frame() {
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
