// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Void Well's shaft filled with its network's fill ({@link VoidWellBlockEntity#shaft}): the
 * surface of a translucent void fluid across the shaft, as high as the network is full, easing to a
 * new level. It is drawn only, so the shaft stays open air: the casing walls hide its sides, and seen
 * from below, from in the shaft, its surface is a ceiling of void. Water's moving surface, darkened to
 * the void's purple-black, and drawn emissive, so it glows faintly at night without lighting anything.
 */
final class VoidWellRenderer implements BlockEntityRenderer<VoidWellBlockEntity, VoidWellRenderer.RenderState> {

    /** Deep void purple-black, about 80% opaque. */
    private static final int COLOUR = 0xCC_2A_12_45;
    private static final Identifier SURFACE = Identifier.withDefaultNamespace("block/water_still");
    /** How far below the rim's course the surface of a full shaft stands, so it never meets the rim's faces. */
    private static final float BELOW_TOP = 0.01F;

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(VoidWellBlockEntity well, RenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(well, state, partialTicks, cameraPosition, breakProgress);
        state.fluid = null;
        var level = well.getLevel();
        if (level == null) {
            return;
        }
        double shown = well.shownLevel(level.getGameTime(), partialTicks);
        if (shown <= 0) {
            return;
        }
        var core = well.getBlockPos();
        state.fluid = well.fluidRoom().move(-core.getX(), -core.getY(), -core.getZ());
        state.height = (float) shown;
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        var fluid = state.fluid;
        if (fluid == null) {
            return;
        }
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(SURFACE);
        float y = (float) (fluid.minY + (fluid.getYsize() - BELOW_TOP) * state.height);
        float x0 = (float) fluid.minX;
        float x1 = (float) fluid.maxX;
        float z0 = (float) fluid.minZ;
        float z1 = (float) fluid.maxZ;
        // The sprite once over the whole shaft, one quad, seen from below too as the render type does not cull. It
        // writes no depth, so the butterflies under it, drawn after it with the particles, still show through.
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS, false), (pose, buffer) -> {
            vertex(pose, buffer, x0, y, z0, sprite.getU0(), sprite.getV0());
            vertex(pose, buffer, x0, y, z1, sprite.getU0(), sprite.getV1());
            vertex(pose, buffer, x1, y, z1, sprite.getU1(), sprite.getV1());
            vertex(pose, buffer, x1, y, z0, sprite.getU1(), sprite.getV0());
        });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(COLOUR).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /** The fluid fills the shaft, so it is drawn while any of the shaft is in sight. */
    @Override
    public AABB getRenderBoundingBox(VoidWellBlockEntity well) {
        return well.fluidRoom();
    }

    static final class RenderState extends BlockEntityRenderState {
        /** The room the fluid fills when full, around the core; null when the shaft shows nothing. */
        private @Nullable AABB fluid;
        /** How high the fluid stands, as a share of the room's height. */
        private float height;
    }
}
