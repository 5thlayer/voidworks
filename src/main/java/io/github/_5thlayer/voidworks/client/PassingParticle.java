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
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * An item seen moving between a Well hatch and a Matter-line dragon's paws ({@link PassingShown}): one of
 * it, drawn as the dragon draws what it hangs, as big and turned the same way, with no shadow, its
 * middle where it is. It rises from the top of the hatch, arcs over the Well's rim and comes in to where
 * the dragon hangs its stack, following it, in {@link Handling#PASS_TICKS}, or goes the other way, and
 * lands with a quiet item pickup pop. Vanilla's item pickup is a straight line of three ticks, so it has a
 * particle group of its own.
 */
final class PassingParticle extends Particle {

    /** The group its particles are drawn in. */
    static final ParticleRenderType GROUP = new ParticleRenderType("VOIDWORKS_PASSING");

    /** How loud it pops as it lands. */
    private static final float POP_VOLUME = 0.15F;
    /** How high it pops, give or take a little each time, as vanilla's pickup does. */
    private static final float POP_PITCH = 1.6F;

    /** How it is drawn, before its dragon has drawn what it hangs: as big as its item model is, upright. */
    private static final Matrix4fc UNPOSED = new Matrix4f();

    private final ItemStackRenderState item = new ItemStackRenderState();
    private final PassingShown shown;
    /** Where the dragon's paws are, where it hangs its stack, followed while it is seen. */
    private Vec3 paws;
    /** How big and turned which way it is drawn, as its dragon draws what it hangs, with no move. */
    private Matrix4fc turned;
    /** How many ticks it has been on its way, counting those before it was shown. */
    private int ticks;
    /** Where it is this tick, and where it was the last, between which it is drawn. */
    private Vec3 at;
    private Vec3 was;

    private PassingParticle(ClientLevel level, PassingShown shown, VoidDragon dragon) {
        super(level, shown.hatch().x, shown.hatch().y, shown.hatch().z);
        this.shown = shown;
        Minecraft.getInstance().getItemModelResolver().updateForNonLiving(item, shown.stack().copyWithCount(1), ItemDisplayContext.FIXED, dragon);
        follow(dragon);
        this.ticks = Math.min(shown.elapsed(), Handling.PASS_TICKS);
        lifetime = Handling.PASS_TICKS;
        at = along(ticks);
        was = at;
    }

    /** Shows {@code shown}, from where it is on its way, unless its dragon is not here to be seen. */
    static void show(ClientLevel level, PassingShown shown, ParticleEngine engine) {
        if (level.getEntity(shown.dragon()) instanceof VoidDragon dragon && !shown.stack().isEmpty()) {
            engine.add(new PassingParticle(level, shown, dragon));
        }
    }

    /** It goes to where {@code dragon} last drew what it hangs, drawn as that was. */
    private void follow(VoidDragon dragon) {
        var pose = HungPoses.of(dragon);
        var offset = pose.map(drawn -> drawn.getTranslation(new Vector3f())).orElseGet(() -> new Vector3f(0.0F, dragon.getBbHeight() / 3, 0.0F));
        paws = dragon.position().add(offset.x(), offset.y(), offset.z());
        turned = pose.<Matrix4fc>map(drawn -> new Matrix4f(drawn).setTranslation(0.0F, 0.0F, 0.0F)).orElse(UNPOSED);
    }

    @Override
    public void tick() {
        if (level.getEntity(shown.dragon()) instanceof VoidDragon dragon) {
            follow(dragon);
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

    /** Its particles, each drawn as its dragon draws what it hangs, where it is between ticks. */
    static final class Group extends ParticleGroup<PassingParticle> {

        Group(ParticleEngine engine) {
            super(engine);
        }

        @Override
        public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
            var camAt = camera.position();
            return new State(particles.stream()
                    .map(particle -> new Instance(particle.item, particle.was.lerp(particle.at, partialTickTime).subtract(camAt), particle.turned,
                            particle.getLightCoords(partialTickTime)))
                    .toList());
        }
    }

    private record Instance(ItemStackRenderState item, Vec3 offset, Matrix4fc turned, int light) {
    }

    private record State(List<Instance> instances) implements ParticleGroupRenderState {

        @Override
        public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
            var poseStack = new PoseStack();
            for (var instance : instances) {
                poseStack.pushPose();
                poseStack.translate(instance.offset().x, instance.offset().y, instance.offset().z);
                poseStack.mulPose(instance.turned());
                instance.item().submit(poseStack, submitNodeCollector, instance.light(), OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }
}
