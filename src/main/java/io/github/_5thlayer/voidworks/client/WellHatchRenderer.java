// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github._5thlayer.voidworks.well.WellHatchBlock;
import io.github._5thlayer.voidworks.well.WellHatchBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The item a Well hatch shows ({@link WellHatchBlockEntity#shown}), a requester's request or a
 * provider's first stack, flat on its front in the shulker window, as an item frame holds one, lit as
 * the block in front of it is.
 */
final class WellHatchRenderer implements BlockEntityRenderer<WellHatchBlockEntity, WellHatchRenderer.RenderState> {

    /** How far out from the front its middle stands, as an item frame's stands off its wall. */
    private static final float OFF_FACE = 1 / 16F;
    /** Its size, against an item's own: an item frame's. */
    private static final float SCALE = 0.5F;

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(WellHatchBlockEntity hatch, RenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(hatch, state, partialTicks, cameraPosition, breakProgress);
        state.item = null;
        var level = hatch.getLevel();
        var shown = hatch.shown();
        if (level == null || shown.isEmpty()) {
            return;
        }
        var item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, shown, ItemDisplayContext.FIXED, level, null, 0);
        state.item = item;
        state.yRot = hatch.getBlockState().getValue(WellHatchBlock.FACING).toYRot();
        state.light = LevelRenderer.getLightCoords(level, hatch.getBlockPos().relative(hatch.getBlockState().getValue(WellHatchBlock.FACING)));
    }

    /** Turned as an item frame on its front would turn it, so its own front faces out. */
    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (state.item == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - state.yRot));
        poseStack.translate(0, 0, -(0.5F + OFF_FACE));
        poseStack.scale(SCALE, SCALE, SCALE);
        state.item.submit(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    static final class RenderState extends BlockEntityRenderState {
        private @Nullable ItemStackRenderState item;
        private float yRot;
        private int light;
    }
}
