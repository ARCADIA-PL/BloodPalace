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
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        long time = level.getGameTime();
        float pulse = 0.45F + 0.25F * (float) Math.sin((time + event.getPartialTick()) * 0.12F);

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (TeleportAnchorEntity anchor : level.getEntitiesOfClass(TeleportAnchorEntity.class, searchBounds)) {
            renderEffect(anchor, poseStack, lines, pulse, time);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderEffect(TeleportAnchorEntity anchor, PoseStack poseStack,
            VertexConsumer lines, float pulse, long time) {
        poseStack.pushPose();
        poseStack.translate(anchor.getX(), anchor.getY() + 0.02D, anchor.getZ());

        float outer = 1.18F;
        float inner = 0.78F;
        float core = 0.42F;

        renderSquareOutline(poseStack, lines, outer, 0.08F, 0.95F, 1.0F, 0.85F);
        renderSquareOutline(poseStack, lines, inner, 0.65F, 0.85F, 1.0F, 0.55F + pulse * 0.2F);
        renderSquareOutline(poseStack, lines, core, 1.0F, 0.45F, 0.78F, 0.65F);

        float spin = (time % 360) * 4.0F;
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        renderCross(poseStack, lines, 0.92D, 1.0F, 0.65F, 0.18F, 0.9F);
        renderCross(poseStack, lines, 0.56D, 1.0F, 0.9F, 0.3F, 0.7F);

        poseStack.popPose();
    }

    private static void renderSquareOutline(PoseStack poseStack, VertexConsumer lines,
            float radius, float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        double y = 0.004D;
        LevelRenderer.renderLineBox(
            poseStack,
            lines,
            -radius, y, -radius,
            radius, y + 0.002D, radius,
            red, green, blue, alpha);
        line(lines, pose, -radius, y, -radius, radius, y, -radius, red, green, blue, alpha);
        line(lines, pose, radius, y, -radius, radius, y, radius, red, green, blue, alpha);
        line(lines, pose, radius, y, radius, -radius, y, radius, red, green, blue, alpha);
        line(lines, pose, -radius, y, radius, -radius, y, -radius, red, green, blue, alpha);
    }

    private static void renderCross(PoseStack poseStack, VertexConsumer lines, double radius,
            float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        double y = 0.012D;
        line(lines, pose, -radius, y, 0.0D, radius, y, 0.0D, red, green, blue, alpha);
        line(lines, pose, 0.0D, y, -radius, 0.0D, y, radius, red, green, blue, alpha);
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float red, float green, float blue, float alpha) {
        lines.vertex(pose.pose(), (float) x1, (float) y1, (float) z1)
            .color(red, green, blue, alpha)
            .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
            .endVertex();
        lines.vertex(pose.pose(), (float) x2, (float) y2, (float) z2)
            .color(red, green, blue, alpha)
            .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
            .endVertex();
    }
}
