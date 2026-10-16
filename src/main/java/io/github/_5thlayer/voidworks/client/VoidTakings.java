// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.taking.TakingMask;
import io.github._5thlayer.voidworks.taking.TakingStages;
import io.github._5thlayer.voidworks.taking.VoidTaking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The void seen taking blocks ({@link VoidTaking}): each block it takes is drawn over the Voidstone
 * now there, a hair larger, and eaten away through the {@link TakingMask} over
 * {@link TakingStages#DURATION} ticks, until only the Voidstone is left. A solid block is drawn as its
 * own model and sheds crumbs as it goes, a fluid as a full cube of its still texture, glowing if it
 * glows, and air as the End's starfield. A taking whose chunk unloads is dropped, and nothing of it
 * outlives the session.
 */
final class VoidTakings {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ContextKey<List<Drawn>> DRAWN = new ContextKey<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_takings"));

    private static final Vector3fc BLOCK_FROM = new Vector3f(0, 0, 0);
    private static final Vector3fc BLOCK_TO = new Vector3f(1, 1, 1);

    /** How much larger than the block it is drawn, so it never fights the Voidstone's faces. */
    private static final float GROWN = 1.002F;

    /** The crumbs a solid block sheds while it is taken: as many as it once burst into, spread over its stages. */
    private static final float CRUMBS_PER_TICK = 30F / TakingStages.DURATION;

    private static final List<VoidTaking> SHOWN = new ArrayList<>();

    /** The masks, once the client's resources have loaded them; nothing is drawn without them. */
    private static @Nullable TakingMask mask;

    private VoidTakings() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((AddClientReloadListenersEvent event) -> event.addListener(
                Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "void_taking_masks"), (ResourceManagerReloadListener) VoidTakings::loadMasks));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener(VoidTakings::extract);
        NeoForge.EVENT_BUS.addListener(VoidTakings::submit);
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel().isClientSide()) {
                var chunk = event.getChunk().getPos();
                SHOWN.removeIf(taking -> ChunkPos.containing(taking.pos()).equals(chunk));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> SHOWN.clear());
    }

    static void receive(VoidTaking taking) {
        SHOWN.add(taking);
    }

    private static void loadMasks(ResourceManager resources) {
        var masks = new ArrayList<boolean[]>();
        try {
            for (int stage = 1; stage <= TakingStages.STAGES; stage++) {
                try (var in = resources.open(TakingMask.path(stage)); var image = NativeImage.read(in)) {
                    var painted = new boolean[TakingMask.SIZE * TakingMask.SIZE];
                    for (int y = 0; y < TakingMask.SIZE; y++) {
                        for (int x = 0; x < TakingMask.SIZE; x++) {
                            painted[y * TakingMask.SIZE + x] = TakingMask.paints(image.getPixel(x * image.getWidth() / TakingMask.SIZE,
                                    y * image.getHeight() / TakingMask.SIZE));
                        }
                    }
                    masks.add(painted);
                }
            }
            mask = TakingMask.of(masks);
        } catch (IOException | IllegalArgumentException unreadable) {
            LOGGER.error("Voidworks could not read the void's stage masks, so the void takes blocks unseen", unreadable);
            mask = null;
        }
    }

    /** Drops what is over and has a solid block being taken shed its crumbs. */
    private static void tick() {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            SHOWN.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        SHOWN.removeIf(taking -> TakingStages.over(now - taking.start()));
        var random = level.getRandom();
        for (var taking : SHOWN) {
            if (solid(taking.taken())) {
                int crumbs = (int) CRUMBS_PER_TICK + (random.nextFloat() < CRUMBS_PER_TICK % 1 ? 1 : 0);
                for (int i = 0; i < crumbs; i++) {
                    var at = Vec3.atCenterOf(taking.pos()).add(random.nextFloat() - 0.5, random.nextFloat() - 0.5, random.nextFloat() - 0.5);
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, taking.taken()), at.x, at.y, at.z, 0, 0, 0);
                }
            }
        }
    }

    /** One face of what is being taken, drawn in {@link #type} over the runs of it the void has not reached. */
    private record Face(RenderType type, Vector3fc[] corners, float[] uvs, int colour, int light, List<TakingMask.Run> left) {
    }

    /** What one taking draws this frame, its faces around {@link #pos}. */
    private record Drawn(BlockPos pos, List<Face> faces) {
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var mask = VoidTakings.mask;
        var drawn = new ArrayList<Drawn>();
        if (mask != null) {
            var level = event.getLevel();
            float now = level.getGameTime() + event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for (var taking : SHOWN) {
                float elapsed = now - taking.start();
                if (!TakingStages.over(elapsed) && event.getFrustum().isVisible(new AABB(taking.pos()))) {
                    var faces = faces(level, taking, mask, TakingStages.reached(elapsed));
                    if (!faces.isEmpty()) {
                        drawn.add(new Drawn(taking.pos(), faces));
                    }
                }
            }
        }
        event.getRenderState().setRenderData(DRAWN, drawn);
    }

    private static List<Face> faces(ClientLevel level, VoidTaking taking, TakingMask mask, int reached) {
        var faces = new ArrayList<Face>();
        var pos = taking.pos();
        var taken = taking.taken();
        if (taken.isAir()) {
            for (var direction : Direction.values()) {
                if (shown(level, pos, taken, direction)) {
                    faces.add(cubeFace(RenderTypes.endPortal(), direction, null, -1, 0, mask.left(reached, TakingMask.orientation(pos, direction))));
                }
            }
        } else if (!taken.getFluidState().isEmpty()) {
            var fluid = taken.getFluidState();
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid);
            int tint = model.fluidTintSource() != null ? model.fluidTintSource().colorInWorld(fluid, taken, level, pos) : -1;
            boolean glows = taken.getLightEmission(level, pos) > 0;
            var type = model.layer().translucent() ? RenderTypes.translucentMovingBlock() : RenderTypes.cutoutMovingBlock();
            for (var direction : Direction.values()) {
                if (shown(level, pos, taken, direction)) {
                    int colour = glows ? tint : ARGB.scaleRGB(tint, level.cardinalLighting().byFace(direction));
                    int light = glows ? LightCoordsUtil.FULL_BRIGHT : LevelRenderer.getLightCoords(level, pos.relative(direction));
                    faces.add(cubeFace(type, direction, model.stillMaterial().sprite(), colour, light,
                            mask.left(reached, TakingMask.orientation(pos, direction))));
                }
            }
        } else {
            var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(taken);
            var parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(level, pos, taken, RandomSource.create(taken.getSeed(pos)), parts);
            var colours = Minecraft.getInstance().getBlockColors();
            for (var part : parts) {
                for (var culled : CULLED) {
                    if (culled != null && !shown(level, pos, taken, culled)) {
                        continue;
                    }
                    for (var quad : part.getQuads(culled)) {
                        var direction = quad.direction();
                        var material = quad.materialInfo();
                        int colour = -1;
                        if (material.isTinted()) {
                            var source = colours.getTintSource(taken, material.tintIndex());
                            colour = source != null ? ARGB.opaque(source.colorInWorld(taken, level, pos)) : -1;
                        }
                        if (material.shade()) {
                            colour = ARGB.scaleRGB(colour, level.cardinalLighting().byFace(direction));
                        }
                        var corners = new Vector3fc[4];
                        var uvs = new float[8];
                        for (int i = 0; i < 4; i++) {
                            corners[i] = quad.position(i);
                            uvs[2 * i] = UVPair.unpackU(quad.packedUV(i));
                            uvs[2 * i + 1] = UVPair.unpackV(quad.packedUV(i));
                        }
                        var type = material.layer().translucent() ? RenderTypes.translucentMovingBlock() : RenderTypes.cutoutMovingBlock();
                        faces.add(new Face(type, corners, uvs, colour, LevelRenderer.getLightCoords(level, pos.relative(direction)),
                                mask.left(reached, TakingMask.orientation(pos, direction))));
                    }
                }
            }
        }
        faces.removeIf(face -> face.left().isEmpty());
        return faces;
    }

    /** The faces a model's quads are culled by, and null for those never culled. */
    private static final @Nullable Direction[] CULLED = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** @return whether the face of {@code taken} at {@code pos} towards {@code direction} would show, were it still there */
    private static boolean shown(ClientLevel level, BlockPos pos, BlockState taken, Direction direction) {
        return Block.shouldRenderFace(taken, level.getBlockState(pos.relative(direction)), direction);
    }

    /**
     * The block's face towards {@code direction}, corners in the order a model's face has them, with
     * {@code sprite} over it whole, or no texture for a render type that takes none.
     */
    private static Face cubeFace(RenderType type, Direction direction, @Nullable TextureAtlasSprite sprite, int colour, int light, List<TakingMask.Run> left) {
        var info = FaceInfo.fromFacing(direction);
        var corners = new Vector3fc[4];
        for (int i = 0; i < 4; i++) {
            corners[i] = info.getVertexInfo(i).select(BLOCK_FROM, BLOCK_TO);
        }
        var uvs = sprite == null ? new float[8] : new float[] {
                sprite.getU0(), sprite.getV0(), sprite.getU0(), sprite.getV1(), sprite.getU1(), sprite.getV1(), sprite.getU1(), sprite.getV0()};
        return new Face(type, corners, uvs, colour, light, left);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        List<Drawn> drawn = event.getLevelRenderState().getRenderData(DRAWN);
        if (drawn == null) {
            return;
        }
        var camera = event.getLevelRenderState().cameraRenderState.pos;
        var poseStack = event.getPoseStack();
        for (var taking : drawn) {
            poseStack.pushPose();
            poseStack.translate(taking.pos().getX() - camera.x, taking.pos().getY() - camera.y, taking.pos().getZ() - camera.z);
            for (var face : taking.faces()) {
                event.getSubmitNodeCollector().submitCustomGeometry(poseStack, face.type(), (pose, buffer) -> draw(pose, buffer, face));
            }
            poseStack.popPose();
        }
    }

    /** Draws each run of the face the void has not reached, as the part of the face over those pixels. */
    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Face face) {
        boolean textured = face.type() != RenderTypes.endPortal();
        float size = TakingMask.SIZE;
        for (var run : face.left()) {
            float s0 = run.x0() / size;
            float s1 = run.x1() / size;
            float t0 = run.y() / size;
            float t1 = (run.y() + 1) / size;
            vertex(pose, buffer, face, textured, s0, t0);
            vertex(pose, buffer, face, textured, s0, t1);
            vertex(pose, buffer, face, textured, s1, t1);
            vertex(pose, buffer, face, textured, s1, t0);
        }
    }

    /**
     * The face's point {@code s} along its first and last corners' edge and {@code t} towards its
     * second and third's, as its corners wind: corner 0 at (0, 0), 1 at (0, 1), 2 at (1, 1), 3 at (1, 0).
     */
    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Face face, boolean textured, float s, float t) {
        var c = face.corners();
        float x = grown(lerp(lerp(c[0].x(), c[3].x(), s), lerp(c[1].x(), c[2].x(), s), t));
        float y = grown(lerp(lerp(c[0].y(), c[3].y(), s), lerp(c[1].y(), c[2].y(), s), t));
        float z = grown(lerp(lerp(c[0].z(), c[3].z(), s), lerp(c[1].z(), c[2].z(), s), t));
        var vertex = buffer.addVertex(pose, x, y, z);
        if (textured) {
            var uv = face.uvs();
            vertex.setColor(face.colour())
                    .setUv(lerp(lerp(uv[0], uv[6], s), lerp(uv[2], uv[4], s), t), lerp(lerp(uv[1], uv[7], s), lerp(uv[3], uv[5], s), t))
                    .setLight(face.light());
        }
    }

    private static float grown(float coordinate) {
        return 0.5F + (coordinate - 0.5F) * GROWN;
    }

    private static float lerp(float from, float to, float by) {
        return from + (to - from) * by;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
