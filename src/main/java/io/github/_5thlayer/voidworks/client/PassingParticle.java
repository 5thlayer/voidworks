// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github._5thlayer.voidworks.dragon.Handling;
import io.github._5thlayer.voidworks.dragon.PassingShown;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * An item seen moving between a Well hatch and a Matter-line dragon's paws ({@link PassingShown}): drawn
 * as it lies dropped, bigger, never bobbing, it rises from the top of the hatch, arcs over the Well's rim
 * and comes in to the paws, following the dragon, in {@link Handling#PASS_TICKS}, or goes the other way,
 * and lands with a quiet item pickup pop. Vanilla's item pickup is a straight line of three ticks, so it
 * has a particle group of its own.
 */
final class PassingParticle extends Particle {

    /** The group its particles are drawn in. */
    static final ParticleRenderType GROUP = new ParticleRenderType("VOIDWORKS_PASSING");

    /** How much bigger it is drawn than it lies dropped. Tuned in game. */
    private static final float SCALE = 2.0F;

    /** How loud and how high it pops as it lands. */
    private static final float POP_VOLUME = 0.15F;
    private static final float POP_PITCH = 1.6F;

    private final EntityRenderState item;
    private final PassingShown shown;
    /** Where the dragon's paws are, followed while it is seen. */
    private Vec3 paws;
    private int ticks;
    private Vec3 at;
    private Vec3 was;

    private PassingParticle(ClientLevel level, PassingShown shown, EntityRenderState item, Vec3 paws) {
        super(level, shown.hatch().x, shown.hatch().y, shown.hatch().z);
        this.shown = shown;
        this.item = item;
        this.paws = paws;
        this.ticks = Math.min(shown.elapsed(), Handling.PASS_TICKS);
        lifetime = Handling.PASS_TICKS;
        at = along(ticks);
        was = at;
    }

    /** Shows {@code shown}, from where it is on its way, unless its dragon is not here to be seen. */
    static void show(ClientLevel level, PassingShown shown, ParticleEngine engine) {
        if (!(level.getEntity(shown.dragon()) instanceof VoidDragon dragon) || shown.stack().isEmpty()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var dropped = new ItemEntity(level, shown.hatch().x, shown.hatch().y, shown.hatch().z, shown.stack().copy());
        var item = minecraft.getEntityRenderDispatcher().extractEntity(dropped, 1.0F);
        if (item instanceof ItemEntityRenderState lying) {
            lying.shouldBob = false;
        }
        engine.add(new PassingParticle(level, shown, item, dragon.paws()));
    }

    @Override
    public void tick() {
        if (level.getEntity(shown.dragon()) instanceof VoidDragon dragon) {
            paws = dragon.paws();
        }
        was = at;
        ticks++;
        at = along(ticks);
        if (ticks >= lifetime) {
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, POP_VOLUME,
                    POP_PITCH + (random.nextFloat() - random.nextFloat()) * 0.3F, false);
            remove();
        }
    }

    /** @return where it is {@code ticks} on its way: along a curve from where it starts, bending over the rim, to where it lands, easing in and out */
    private Vec3 along(int ticks) {
        float t = Mth.clamp((float) ticks / Handling.PASS_TICKS, 0.0F, 1.0F);
        double eased = t * t * (3 - 2 * t);
        var from = shown.loading() ? shown.hatch() : paws;
        var to = shown.loading() ? paws : shown.hatch();
        var near = from.lerp(shown.over(), eased);
        var far = shown.over().lerp(to, eased);
        return near.lerp(far, eased);
    }

    @Override
    public ParticleRenderType getGroup() {
        return GROUP;
    }

    /** Its particles, each drawn as its item lies dropped, where it is between ticks. */
    static final class Group extends ParticleGroup<PassingParticle> {

        Group(ParticleEngine engine) {
            super(engine);
        }

        @Override
        public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
            var camAt = camera.position();
            return new State(particles.stream().map(particle -> {
                var at = particle.was.lerp(particle.at, partialTickTime).subtract(camAt);
                return new Instance(particle.item, at);
            }).toList());
        }
    }

    private record Instance(EntityRenderState item, Vec3 offset) {
    }

    private record State(List<Instance> instances) implements ParticleGroupRenderState {

        @Override
        public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
            var poseStack = new PoseStack();
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            for (var instance : instances) {
                poseStack.pushPose();
                poseStack.translate(instance.offset().x, instance.offset().y, instance.offset().z);
                poseStack.scale(SCALE, SCALE, SCALE);
                dispatcher.submit(instance.item(), camera, 0.0, 0.0, 0.0, poseStack, submitNodeCollector);
                poseStack.popPose();
            }
        }
    }
}
