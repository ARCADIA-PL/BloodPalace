package org.com.bloodplace.handler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ShowcaseHandler {

    private static final int BORDER_SIZE = 500;

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation dimId = player.level().dimension().location();
        if (isShowcaseDimension(dimId)) {
            WorldBorder border = player.serverLevel().getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(BORDER_SIZE);
            border.setDamagePerBlock(0.1);
            border.setWarningBlocks(20);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation dimId = player.level().dimension().location();
        if (isShowcaseDimension(dimId)) {
            WorldBorder border = player.serverLevel().getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(BORDER_SIZE);
        }
    }

    private boolean isShowcaseDimension(ResourceLocation dimId) {
        return "bloodplace".equals(dimId.getNamespace())
            && dimId.getPath().endsWith("_showcase");
    }
}
