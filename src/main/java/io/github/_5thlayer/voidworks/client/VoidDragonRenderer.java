// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a Void Dragon as the vanilla Ender Dragon's model, baked from the player's own game, at
 * {@link #DRAGONLING_SCALE} of its size for a Dragonling and {@link VoidDragon#GROWN_SCALE} times that
 * for a grown dragon's presence, posed from its flight history and wingbeat as the Ender Dragon is,
 * and sitting, landing and taking off as the Ender Dragon does on its portal. Nothing of Mojang's
 * ships in the jar: it wears the vanilla texture, read from the player's game, tinted by its Line,
 * the void's purple for the Mote line and a pale amber for the Matter line, whose eyes are tinted
 * amber too, and has no texture of its own. A grown presence is sized by its dragon's life stage, and
 * dying of old age it shows beams of light as the Ender Dragon does. What it hangs under its body
 * ({@link VoidDragon#hung()}), a Matter-line dragon's first carried stack or a Matter-line Dragonling's
 * chest, is drawn there from that stack's own item model.
 */
final class VoidDragonRenderer extends EntityRenderer<VoidDragon, VoidDragonRenderer.State> {

    /** A Dragonling is about a fifth of the Ender Dragon's size. */
    static final float DRAGONLING_SCALE = 0.2F;

    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png"));
    /** The void's purple, which tints the vanilla texture for a Mote-line dragon. */
    private static final int MOTE_TINT = ARGB.color(255, 190, 130, 255);
    /**
     * A pale amber, which tints it for a Matter-line dragon: paler than the Matter Dragon Egg's, since a
     * tint only darkens the near-black texture, and the more of each channel it keeps the brighter it is.
     */
    private static final int MATTER_TINT = ARGB.color(255, 255, 210, 120);
    /** What leaves a texture as it is: a Mote-line dragon's eyes. */
    private static final int UNTINTED = -1;
    /** What tints a Matter-line dragon's magenta eyes amber: their red kept, their blue cut, as they have little green to keep. */
    private static final int MATTER_EYES_TINT = ARGB.color(255, 255, 255, 30);

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

    /** How wide a sitting presence's shadow is, as a share of its flying one's, its wings folded on the Well. */
    private static final float SITTING_SHADOW = 0.5F;

    /**
     * Where what it hangs is drawn, in the model's units from its body's pivot, the model's down being
     * positive: under the middle of its body's box, which runs 1 to 25 down and -16 to 48 along it.
     */
    private static final float HUNG_Y = 26.0F / 16.0F;
    private static final float HUNG_Z = 16.0F / 16.0F;
    /** How big what it hangs is drawn, against its item model's fixed size: a block about as wide as the body. */
    private static final float HUNG_SIZE = 3.0F;

    private final EnderDragonModel model;
    /** Its model's body, which what it hangs follows. */
    private final ModelPart body;
    /** Turns what it hangs into its item model. */
    private final ItemModelResolver itemModelResolver;

    /** The Ender Dragon's, how much smaller this one is drawn, how its body and eyes are tinted by its Line, what it hangs under its body and how far it has settled onto its seat. */
    static final class State extends EnderDragonRenderState {

        float scale = DRAGONLING_SCALE;
        int tint = MOTE_TINT;
        int eyesTint = UNTINTED;
        final ItemStackRenderState hung = new ItemStackRenderState();
        float settled;
    }

    VoidDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
        body = model.root().getChild("body");
        itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VoidDragon dragon, State state, float partialTicks) {
        super.extractRenderState(dragon, state, partialTicks);
        state.scale = dragon.grown() ? DRAGONLING_SCALE * VoidDragon.GROWN_SCALE * VoidDragon.size(dragon.stage()) : DRAGONLING_SCALE;
        tint(state, dragon.line());
        state.deathTime = dragon.deathTime(partialTicks);
        state.flapTime = Mth.lerp(partialTicks, dragon.oFlapTime, dragon.flapTime);
        state.partialTicks = partialTicks;
        state.flightHistory.copyFrom(dragon.flightHistory);
        itemModelResolver.updateForNonLiving(state.hung, dragon.hung(), ItemDisplayContext.FIXED, dragon);
        var perch = dragon.perch();
        state.isSitting = perch == VoidDragon.Perch.SITTING;
        state.isLandingOrTakingOff = perch.settling();
        state.settled = dragon.settled(partialTicks);
        // How far it is from its seat, as the Ender Dragon's from its portal, at the Ender Dragon's size, which its neck eases by.
        var at = dragon.getPosition(partialTicks);
        double fromSeat = dragon.fromSeat(at);
        state.distanceToEgg = fromSeat * fromSeat / (state.scale * state.scale);
        if (state.isSitting) {
            lookFromTheSeat(state, Mth.rotLerp(partialTicks, dragon.yHeadRotO, dragon.yHeadRot));
        }
    }

    /** Tints {@code state} as a dragon of {@code line} is: its body over the vanilla texture, and its eyes, a Mote-line one's untinted. */
    private static void tint(State state, Line line) {
        switch (line) {
            case MOTE -> {
                state.tint = MOTE_TINT;
                state.eyesTint = UNTINTED;
            }
            case MATTER -> {
                state.tint = MATTER_TINT;
                state.eyesTint = MATTER_EYES_TINT;
            }
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

    /**
     * What it hangs, under its body as the model poses it, bobbing and banking with it, upright and
     * turned side on, so a flat item shows its face to a player beside the dragon.
     */
    private void submitHung(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        model.setupAnim(state);
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, HUNG_Y + HUNG_SIZE / 4.0F, HUNG_Z);
        // The model is drawn upside down and mirrored; the item is turned back upright.
        poseStack.scale(-HUNG_SIZE, -HUNG_SIZE, HUNG_SIZE);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        state.hung.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    /** The Ender Dragon's shadow, scaled as the dragon is, and narrower as it settles onto its seat, its wings folded. */
    @Override
    protected float getShadowRadius(State state) {
        return 8.0F * state.scale * Mth.lerp(state.settled, 1.0F, SITTING_SHADOW);
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
        submitNodeCollector.submitModel(model, state, poseStack, model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, state.tint, null,
                state.outlineColor, null);
        submitNodeCollector.submitModel(model, state, poseStack, EYES, state.lightCoords, OverlayTexture.NO_OVERLAY, state.eyesTint, null,
                state.outlineColor, null);
        if (!state.hung.isEmpty()) {
            submitHung(state, poseStack, submitNodeCollector);
        }
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
