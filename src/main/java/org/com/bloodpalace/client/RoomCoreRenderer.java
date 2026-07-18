package org.com.bloodpalace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.com.bloodpalace.BloodPalace;
import org.com.bloodpalace.entity.RoomCoreEntity;
import org.com.bloodpalace.network.RoomEditorState;

public class RoomCoreRenderer extends EntityRenderer<RoomCoreEntity> {

    private static final RenderType ROOM_OVERLAY_LINES = RoomOverlayLineRenderType.create();
    private static RoomEditorState editingState;

    public RoomCoreRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RoomCoreEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!shouldShowRoomEditingOverlay()) return;

        VertexConsumer lines = buffer.getBuffer(ROOM_OVERLAY_LINES);
        AABB localBounds = entity.roomBounds().move(-entity.getX(), -entity.getY(), -entity.getZ());
        renderRoom(localBounds, poseStack, lines, isEditing(entity));
        renderNameTag(entity, entity.getDisplayName(), poseStack, buffer, packedLight);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public static boolean shouldShowRoomEditingOverlay() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isCreative();
    }

    public static void setEditingState(RoomEditorState state) {
        editingState = state;
    }

    public static void clearEditingState() {
        editingState = null;
    }

    public static RenderType roomOverlayLines() {
        return ROOM_OVERLAY_LINES;
    }

    @Override
    public ResourceLocation getTextureLocation(RoomCoreEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private boolean isEditing(RoomCoreEntity entity) {
        return editingState != null && editingState.roomId().equals(entity.getRoomId());
    }

    private static void renderRoom(AABB bounds, PoseStack poseStack, VertexConsumer lines, boolean active) {
        float red = active ? 0.1F : 1.0F;
        float green = active ? 0.85F : 0.45F;
        float blue = active ? 1.0F : 0.05F;
        LevelRenderer.renderLineBox(poseStack, lines, bounds, red, green, blue, 1.0F);
        renderGuideLines(bounds, poseStack, lines, red, green, blue, active ? 0.5F : 0.28F);
    }

    private static void renderGuideLines(AABB bounds, PoseStack poseStack,
            VertexConsumer lines, float red, float green, float blue, float alpha) {
        renderHorizontalGuides(bounds, poseStack, lines, red, green, blue, alpha);
        renderAxisGuides(bounds, poseStack, lines, red, green, blue, alpha);
    }

    private static void renderHorizontalGuides(AABB bounds, PoseStack poseStack,
            VertexConsumer lines, float red, float green, float blue, float alpha) {
        int count = guideCount(bounds.getYsize());
        for (int i = 1; i < count; i++) {
            double y = lerp(bounds.minY, bounds.maxY, (double) i / count);
            LevelRenderer.renderLineBox(
                poseStack, lines,
                bounds.minX, y, bounds.minZ,
                bounds.maxX, y + 0.001D, bounds.maxZ,
                red, green, blue, alpha);
        }
    }

    private static void renderAxisGuides(AABB bounds, PoseStack poseStack,
            VertexConsumer lines, float red, float green, float blue, float alpha) {
        int xCount = guideCount(bounds.getXsize());
        for (int i = 1; i < xCount; i++) {
            double x = lerp(bounds.minX, bounds.maxX, (double) i / xCount);
            LevelRenderer.renderLineBox(
                poseStack, lines,
                x, bounds.minY, bounds.minZ,
                x + 0.001D, bounds.maxY, bounds.maxZ,
                red, green, blue, alpha);
        }

        int zCount = guideCount(bounds.getZsize());
        for (int i = 1; i < zCount; i++) {
            double z = lerp(bounds.minZ, bounds.maxZ, (double) i / zCount);
            LevelRenderer.renderLineBox(
                poseStack, lines,
                bounds.minX, bounds.minY, z,
                bounds.maxX, bounds.maxY, z + 0.001D,
                red, green, blue, alpha);
        }
    }

    private static int guideCount(double size) {
        return Math.max(2, Math.min(8, (int) Math.ceil(size / 8.0D)));
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static final class RoomOverlayLineRenderType extends RenderType {

        private RoomOverlayLineRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        private static RenderType create() {
            return RenderType.create(
                BloodPalace.MODID + ":room_overlay_lines",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false));
        }
    }
}
