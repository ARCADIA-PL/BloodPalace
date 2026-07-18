package org.com.bloodpalace.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.com.bloodpalace.BloodPalace;
import org.com.bloodpalace.entity.TeleportAnchorEntity;

public class TeleportAnchorRenderer extends EntityRenderer<TeleportAnchorEntity> {

    private static final RenderType ANCHOR_GLOW = AnchorGlowRenderType.create();

    public TeleportAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TeleportAnchorEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer quads = buffer.getBuffer(ANCHOR_GLOW);
        float breath = 0.5F + 0.5F * (float) Math.sin((entity.tickCount + partialTick) * 0.12F);
        renderAnchorArea(poseStack, quads, breath);
        if (entity.distanceToSqr(entityRenderDispatcher.camera.getEntity()) < 256.0D) {
            renderNameTag(entity, entity.getDisplayName(), poseStack, buffer, packedLight);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TeleportAnchorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static void renderAnchorArea(PoseStack poseStack, VertexConsumer quads, float breath) {
        poseStack.pushPose();

        float topAlpha = 0.14F + breath * 0.18F;
        float glowAlpha = 0.06F + breath * 0.09F;
        float rippleAlpha = 0.08F + breath * 0.16F;
        double half = 0.5D;
        double height = 0.28D + breath * 0.24D;
        double outer = half + 0.12D;
        double inner = half - 0.16D;

        renderHorizontalQuad(quads, poseStack, -outer, -outer, outer, outer, 0.003D, glowAlpha);
        renderHorizontalQuad(quads, poseStack, -half, -half, half, half, 0.004D, topAlpha);
        renderHorizontalQuad(quads, poseStack, -inner, -inner, inner, inner, 0.005D, topAlpha * 0.6F);

        renderVerticalStrip(quads, poseStack, -half, -half, half, -half, height, rippleAlpha);
        renderVerticalStrip(quads, poseStack, half, -half, half, half, height, rippleAlpha);
        renderVerticalStrip(quads, poseStack, -half, half, half, half, height, rippleAlpha);
        renderVerticalStrip(quads, poseStack, -half, -half, -half, half, height, rippleAlpha);

        poseStack.popPose();
    }

    private static void renderHorizontalQuad(VertexConsumer vertices, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ, double y, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        vertex(vertices, pose, minX, y, minZ, alpha);
        vertex(vertices, pose, minX, y, maxZ, alpha);
        vertex(vertices, pose, maxX, y, maxZ, alpha);
        vertex(vertices, pose, maxX, y, minZ, alpha);
    }

    private static void renderVerticalStrip(VertexConsumer vertices, PoseStack poseStack,
            double x1, double z1, double x2, double z2, double height, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        vertex(vertices, pose, x1, 0.004D, z1, alpha);
        vertex(vertices, pose, x2, 0.004D, z2, alpha);
        vertex(vertices, pose, x2, height, z2, 0.0F);
        vertex(vertices, pose, x1, height, z1, 0.0F);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
            double x, double y, double z, float alpha) {
        vertices.vertex(pose.pose(), (float) x, (float) y, (float) z)
            .color(0.58F, 0.80F, 1.0F, alpha)
            .endVertex();
    }

    private static final class AnchorGlowRenderType extends RenderType {

        private AnchorGlowRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        private static RenderType create() {
            return RenderType.create(
                BloodPalace.MODID + ":teleport_anchor_glow",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false));
        }
    }
}
