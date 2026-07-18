package org.com.bloodpalace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.com.bloodpalace.entity.TeleportAnchorEntity;

public class TeleportAnchorRenderer extends EntityRenderer<TeleportAnchorEntity> {

    public TeleportAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TeleportAnchorEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
            poseStack,
            lines,
            -0.4D, 0.01D, -0.4D,
            0.4D, 0.18D, 0.4D,
            0.2F, 0.95F, 1.0F, 1.0F);
        if (entity.distanceToSqr(entityRenderDispatcher.camera.getEntity()) < 256.0D) {
            renderNameTag(entity, entity.getDisplayName(), poseStack, buffer, packedLight);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TeleportAnchorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
