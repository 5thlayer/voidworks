// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a Void Dragon as the vanilla Ender Dragon's model, baked from the player's own game, at
 * {@link #DRAGONLING_SCALE} of its size for a Dragonling and {@link VoidDragon#GROWN_SCALE} times that
 * for a grown dragon's presence, posed from its flight history and wingbeat as the Ender Dragon is,
 * and sitting, landing and taking off as the Ender Dragon does on its portal. Nothing of Mojang's
 * ships in the jar: it wears the vanilla texture, read from the player's game, tinted the void's
 * purple, and has no texture of its own. A grown presence is sized by its dragon's life stage, and
 * dying of old age it shows beams of light as the Ender Dragon does.
 */
final class VoidDragonRenderer extends EntityRenderer<VoidDragon, VoidDragonRenderer.State> {

    /** A Dragonling is about a fifth of the Ender Dragon's size. */
    static final float DRAGONLING_SCALE = 0.2F;

    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png"));
    /** The void's purple, which tints the vanilla texture. */
    private static final int TINT = ARGB.color(255, 190, 130, 255);

    /** The most beams a dying dragon shows, at the end of its death. */
    private static final int MOST_BEAMS = 40;
    /** The same beams each frame, so they turn rather than flicker. */
    private static final long BEAM_SEED = 54L;
    /** How long a beam is at the least, and how much longer at most by the end of its death, in model units. */
    private static final float BEAM_LENGTH = 6.0F;
    private static final float BEAM_GROWTH = 18.0F;
    /** How wide a beam's fan is at the least, and how much wider at most. */
    private static final float BEAM_WIDTH = 0.6F;
    private static final float BEAM_WIDENING = 1.5F;
    /** How much of its white a beam's heart has faded by the end of its death. */
    private static final float BEAM_FADE = 0.5F;
    /** The void's purple, clear, at a beam's tip. */
    private static final int BEAM_TIP = ARGB.color(0, 190, 130, 255);
    /** Where beams come out of the body, from the model's origin. */
    private static final float BEAM_ORIGIN_Y = -1.0F;
    private static final float BEAM_ORIGIN_Z = -2.0F;

    /** How many of its flight history's latest samples its model's neck and head read. */
    private static final int NECK = 6;

    /** How far, in degrees, a sitting presence turns its head from its body toward what it looks at. */
    private static final float MOST_LOOK = 50.0F;

    private final EnderDragonModel model;

    /** The Ender Dragon's, and how much smaller this one is drawn. */
    static final class State extends EnderDragonRenderState {

        float scale = DRAGONLING_SCALE;
    }

    VoidDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VoidDragon dragon, State state, float partialTicks) {
        super.extractRenderState(dragon, state, partialTicks);
        state.scale = dragon.grown() ? DRAGONLING_SCALE * VoidDragon.GROWN_SCALE * VoidDragon.size(dragon.stage()) : DRAGONLING_SCALE;
        state.deathTime = dragon.deathTime(partialTicks);
        state.flapTime = Mth.lerp(partialTicks, dragon.oFlapTime, dragon.flapTime);
        state.partialTicks = partialTicks;
        state.flightHistory.copyFrom(dragon.flightHistory);
        var perch = dragon.perch();
        state.isSitting = perch == VoidDragon.Perch.SITTING;
        state.isLandingOrTakingOff = perch.settling();
        // How far it is from its seat, as the Ender Dragon's from its portal, at the Ender Dragon's size, which its neck eases by.
        var at = dragon.getPosition(partialTicks);
        state.distanceToEgg = dragon.seatPos().map(seat -> Vec3.atBottomCenterOf(seat).distanceToSqr(at)).orElse(0.0) / (state.scale * state.scale);
        if (state.isSitting) {
            lookFromTheSeat(state, Mth.rotLerp(partialTicks, dragon.yHeadRotO, dragon.yHeadRot));
        }
    }

    /**
     * Sitting, its body keeps still along the Well and its neck turns its head toward {@code look}, a mob's
     * heading: its model reads the neck from the latest samples of its flight history, so these are made
     * to bend from its body's heading to where it looks.
     */
    private static void lookFromTheSeat(State state, float look) {
        var now = state.flightHistory.get(0);
        float heading = now.yRot();
        // The Ender Dragon's model faces the other way from a mob's heading.
        float turn = Mth.clamp(Mth.wrapDegrees(look + 180.0F - heading), -MOST_LOOK, MOST_LOOK);
        for (int i = 0; i < DragonFlightHistory.LENGTH - NECK; i++) {
            state.flightHistory.record(now.y(), heading);
        }
        for (int i = 0; i < NECK; i++) {
            state.flightHistory.record(now.y(), heading + turn * i / (NECK - 1));
        }
    }

    /**
     * Not drawn while it is invisible, holding its rider out of sight down a shaft through a dive, nor
     * to the rider it has taken into the shaft, who sees only the void.
     */
    @Override
    public boolean shouldRender(VoidDragon dragon, Frustum culler, double camX, double camY, double camZ) {
        if (dragon.isInvisible() || dragon.inTheVoid() && dragon.hasPassenger(entity -> entity == Minecraft.getInstance().getCameraEntity())) {
            return false;
        }
        return super.shouldRender(dragon, culler, camX, camY, camZ);
    }

    /**
     * Beams of light out of a dying dragon, as out of the Ender Dragon dying, more of them and longer as
     * its death, {@code dying} from 0 to 1, goes on: each a thin fan from its body, turning slowly, from
     * white at its heart to the void's purple at its tip.
     */
    private static void submitBeams(PoseStack poseStack, float dying, SubmitNodeCollector submitNodeCollector, RenderType renderType) {
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            int heart = ARGB.colorFromFloat(1.0F - dying * BEAM_FADE, 1.0F, 1.0F, 1.0F);
            int tip = BEAM_TIP;
            var random = RandomSource.create(BEAM_SEED);
            int beams = Mth.ceil(dying * MOST_BEAMS);
            var origin = new Vector3f();
            var turn = new Quaternionf();
            for (int i = 0; i < beams; i++) {
                turn.rotationXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI + dying * Mth.HALF_PI);
                pose.rotate(turn);
                float length = BEAM_LENGTH + random.nextFloat() * BEAM_GROWTH * dying;
                float width = BEAM_WIDTH + random.nextFloat() * BEAM_WIDENING;
                var left = new Vector3f(-width, length, 0.0F);
                var right = new Vector3f(width, length, 0.0F);
                var back = new Vector3f(0.0F, length, width);
                buffer.addVertex(pose, origin).setColor(heart);
                buffer.addVertex(pose, left).setColor(tip);
                buffer.addVertex(pose, right).setColor(tip);
                buffer.addVertex(pose, origin).setColor(heart);
                buffer.addVertex(pose, right).setColor(tip);
                buffer.addVertex(pose, back).setColor(tip);
                buffer.addVertex(pose, origin).setColor(heart);
                buffer.addVertex(pose, back).setColor(tip);
                buffer.addVertex(pose, left).setColor(tip);
            }
        });
    }

    /** The Ender Dragon's shadow, scaled as the dragon is. */
    @Override
    protected float getShadowRadius(State state) {
        return 8.0F * state.scale;
    }

    /** As the Ender Dragon's renderer poses its model, scaled down to its own size. */
    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(state.scale, state.scale, state.scale);
        float yRot = state.getHistoricalPos(7).yRot();
        float climb = (float) (state.getHistoricalPos(5).y() - state.getHistoricalPos(10).y());
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(climb * 10.0F));
        poseStack.translate(0.0F, 0.0F, 1.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        submitNodeCollector.submitModel(model, state, poseStack, model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, TINT, null,
                state.outlineColor, null);
        submitNodeCollector.submitModel(model, state, poseStack, EYES, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        if (state.deathTime > 0.0F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, BEAM_ORIGIN_Y, BEAM_ORIGIN_Z);
            float dying = state.deathTime / VoidDragon.DEATH_TICKS;
            submitBeams(poseStack, dying, submitNodeCollector, RenderTypes.dragonRays());
            submitBeams(poseStack, dying, submitNodeCollector, RenderTypes.dragonRaysDepth());
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
