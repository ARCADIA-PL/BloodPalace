package org.com.bloodpalace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.bloodpalace.BloodPalace;
import org.com.bloodpalace.entity.TeleportAnchorEntity;

@Mod.EventBusSubscriber(modid = BloodPalace.MODID, value = Dist.CLIENT)
public final class TeleportAnchorEffectRenderer {

    private static final double SEARCH_RADIUS = 128.0D;

    private TeleportAnchorEffectRenderer() {
    }

    @SubscribeEvent
    public static void renderAnchorEffects(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return;

        AABB searchBounds = player.getBoundingBox().inflate(SEARCH_RADIUS);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        long time = level.getGameTime();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (TeleportAnchorEntity anchor : level.getEntitiesOfClass(TeleportAnchorEntity.class, searchBounds)) {
            renderEffect(anchor, poseStack, vertices, time, event.getPartialTick());
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lightning());
    }

    private static void renderEffect(TeleportAnchorEntity anchor, PoseStack poseStack,
            VertexConsumer vertices, long time, float partialTick) {
        float pulse = (float) (0.5D + 0.5D * Math.sin((time + partialTick) * 0.12D));
        float alpha = 0.24F + pulse * 0.18F;

        poseStack.pushPose();
        poseStack.translate(anchor.getX(), anchor.getY() + 0.004D, anchor.getZ());
        renderDiamond(vertices, poseStack, 1.15F, 0.05F, 0.9F, 1.0F, alpha);
        poseStack.mulPose(Axis.YP.rotationDegrees((time + partialTick) * 2.0F));
        renderDiamond(vertices, poseStack, 0.78F, 0.65F, 0.1F, 1.0F, alpha * 0.9F);
        poseStack.popPose();
    }

    private static void renderDiamond(VertexConsumer vertices, PoseStack poseStack, float radius,
            float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        vertex(vertices, pose, 0.0F, 0.0F, -radius, red, green, blue, alpha);
        vertex(vertices, pose, radius, 0.0F, 0.0F, red, green, blue, alpha);
        vertex(vertices, pose, 0.0F, 0.0F, radius, red, green, blue, alpha);
        vertex(vertices, pose, -radius, 0.0F, 0.0F, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
            float x, float y, float z, float red, float green, float blue, float alpha) {
        vertices.vertex(pose.pose(), x, y, z).color(red, green, blue, alpha).endVertex();
    }
}
