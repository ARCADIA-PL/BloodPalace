package org.com.bloodpalace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.bloodpalace.BloodPalace;
import org.com.bloodpalace.entity.RoomCoreEntity;

@Mod.EventBusSubscriber(modid = BloodPalace.MODID, value = Dist.CLIENT)
public final class RoomHighlightRenderer {

    private static final int BLOCK_OUTLINE_LIMIT = 768;
    private static final double SEARCH_RADIUS = 512.0D;

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
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (RoomCoreEntity core : level.getEntitiesOfClass(RoomCoreEntity.class, searchBounds)) {
            renderRoom(core.roomBounds(), poseStack, lines);
        }
        poseStack.popPose();
        buffer.endBatch(RenderType.lines());
    }

    public static boolean shouldShowRoomEditingOverlay() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isCreative();
    }

    private static void renderRoom(AABB bounds, PoseStack poseStack, VertexConsumer lines) {
        LevelRenderer.renderLineBox(poseStack, lines, bounds, 0.1F, 0.85F, 1.0F, 1.0F);
        renderBoundaryBlockOutlines(bounds, poseStack, lines);
    }

    private static void renderBoundaryBlockOutlines(AABB bounds, PoseStack poseStack, VertexConsumer lines) {
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX) - 1;
        int maxY = Mth.floor(bounds.maxY) - 1;
        int maxZ = Mth.floor(bounds.maxZ) - 1;

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        long boundaryBlocks = countBoundaryBlocks(width, height, depth);
        if (boundaryBlocks <= 0 || boundaryBlocks > BLOCK_OUTLINE_LIMIT) return;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) {
                        continue;
                    }
                    LevelRenderer.renderLineBox(
                        poseStack,
                        lines,
                        x, y, z,
                        x + 1.0D, y + 1.0D, z + 1.0D,
                        0.1F, 0.85F, 1.0F, 0.25F);
                }
            }
        }
    }

    private static long countBoundaryBlocks(int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) return 0;
        long total = (long) width * height * depth;
        long inner = (long) Math.max(0, width - 2) * Math.max(0, height - 2) * Math.max(0, depth - 2);
        return total - inner;
    }
}
