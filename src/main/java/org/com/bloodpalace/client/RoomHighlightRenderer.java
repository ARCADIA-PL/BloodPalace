package org.com.bloodpalace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.bloodpalace.BloodPalace;
import org.com.bloodpalace.entity.RoomCoreEntity;
import org.com.bloodpalace.network.RoomEditorState;

@Mod.EventBusSubscriber(modid = BloodPalace.MODID, value = Dist.CLIENT)
public final class RoomHighlightRenderer {

    private static final double SEARCH_RADIUS = 512.0D;
    private static final int MAX_GUIDE_LINES_PER_AXIS = 8;
    private static RoomEditorState editingState;

    private RoomHighlightRenderer() {
    }

    @SubscribeEvent
    public static void renderRoomHighlights(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !shouldShowRoomEditingOverlay()) return;

        AABB searchBounds = player.getBoundingBox().inflate(SEARCH_RADIUS);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        String editingRoomId = editingState == null ? null : editingState.roomId();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        if (editingState != null) {
            renderRoom(toBounds(editingState), poseStack, lines, true);
        }
        for (RoomCoreEntity core : level.getEntitiesOfClass(RoomCoreEntity.class, searchBounds)) {
            if (core.getRoomId().equals(editingRoomId)) continue;
            renderRoom(core.roomBounds(), poseStack, lines, false);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
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
        return Math.max(2, Math.min(MAX_GUIDE_LINES_PER_AXIS, (int) Math.ceil(size / 8.0D)));
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static AABB toBounds(RoomEditorState state) {
        return new AABB(
            state.minX(),
            state.minY(),
            state.minZ(),
            state.maxX() + 1.0D,
            state.maxY() + 1.0D,
            state.maxZ() + 1.0D);
    }
}
