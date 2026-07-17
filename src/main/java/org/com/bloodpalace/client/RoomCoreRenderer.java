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
import org.com.bloodpalace.entity.RoomCoreEntity;

public class RoomCoreRenderer extends EntityRenderer<RoomCoreEntity> {

    public RoomCoreRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RoomCoreEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!RoomHighlightRenderer.shouldShowRoomEditingOverlay()) return;

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
            poseStack,
            lines,
            -0.35D, 0.0D, -0.35D,
            0.35D, 0.9D, 0.35D,
            1.0F, 0.45F, 0.05F, 1.0F);
        renderNameTag(entity, entity.getDisplayName(), poseStack, buffer, packedLight);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RoomCoreEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
