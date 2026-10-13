// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github._5thlayer.voidworks.mutation.VoidCrucibleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The result a Void Crucible works on, floating above it, turning and bobbing, lit as the block
 * above it is, while it works and while it waits with it, done
 * ({@link VoidCrucibleBlockEntity#floating}).
 */
final class VoidCrucibleRenderer implements BlockEntityRenderer<VoidCrucibleBlockEntity, VoidCrucibleRenderer.RenderState> {

    /** Where the item's centre floats, in block units above the Crucible's base. */
    private static final double HEIGHT = 1.3;
    /** How far it bobs above and below {@link #HEIGHT}, in block units. */
    private static final double BOB = 0.06;
    /** Ticks per bob, up and back down. */
    private static final float BOB_TICKS = 40;
    /** Degrees it turns each tick. */
    private static final float TURN = 3;
    /** Ticks after which both the bob and the turn are back where they began, so the time drawn stays small. */
    private static final long CYCLE_TICKS = 120;
    /** Its size, against an item's own. */
    private static final float SCALE = 0.5F;

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(VoidCrucibleBlockEntity crucible, RenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(crucible, state, partialTicks, cameraPosition, breakProgress);
        state.item = null;
        var level = crucible.getLevel();
        var floating = crucible.floating();
        if (level == null || floating.isEmpty()) {
            return;
        }
        var item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, floating, ItemDisplayContext.FIXED, level, null, 0);
        state.item = item;
        // A small time that loops, as a float holding the whole game time would lose the partial tick.
        state.time = level.getGameTime() % CYCLE_TICKS + partialTicks;
        state.light = LevelRenderer.getLightCoords(level, crucible.getBlockPos().above());
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (state.item == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, HEIGHT + BOB * Mth.sin(state.time * Mth.TWO_PI / BOB_TICKS), 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.time * TURN % 360));
        poseStack.scale(SCALE, SCALE, SCALE);
        state.item.submit(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** The item floats over the block, so it is drawn while the space above is in sight. */
    @Override
    public AABB getRenderBoundingBox(VoidCrucibleBlockEntity crucible) {
        return new AABB(crucible.getBlockPos()).expandTowards(0, 1, 0);
    }

    static final class RenderState extends BlockEntityRenderState {
        private @Nullable ItemStackRenderState item;
        private float time;
        private int light;
    }
}
