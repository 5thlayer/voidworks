// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
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

/**
 * Draws a Void Dragon as the vanilla Ender Dragon's model, baked from the player's own game, at
 * {@link #DRAGONLING_SCALE} of its size, posed from its flight history and wingbeat as the Ender
 * Dragon is. Nothing of Mojang's ships in the jar: until it has a texture of its own, it wears the
 * vanilla texture, read from the player's game, tinted the void's purple.
 */
final class VoidDragonRenderer extends EntityRenderer<VoidDragon, EnderDragonRenderState> {

    /** A Dragonling is about a fifth of the Ender Dragon's size. */
    static final float DRAGONLING_SCALE = 0.2F;

    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png"));
    /** The placeholder's tint, the void's purple, until the dragon has a texture of its own. */
    private static final int TINT = ARGB.color(255, 190, 130, 255);

    private final EnderDragonModel model;

    VoidDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 8.0F * DRAGONLING_SCALE;
        model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
    }

    @Override
    public EnderDragonRenderState createRenderState() {
        return new EnderDragonRenderState();
    }

    @Override
    public void extractRenderState(VoidDragon dragon, EnderDragonRenderState state, float partialTicks) {
        super.extractRenderState(dragon, state, partialTicks);
        state.flapTime = Mth.lerp(partialTicks, dragon.oFlapTime, dragon.flapTime);
        state.partialTicks = partialTicks;
        state.flightHistory.copyFrom(dragon.flightHistory);
    }

    /** As the Ender Dragon's renderer poses its model, scaled down to a Dragonling's size. */
    @Override
    public void submit(EnderDragonRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(DRAGONLING_SCALE, DRAGONLING_SCALE, DRAGONLING_SCALE);
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
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
