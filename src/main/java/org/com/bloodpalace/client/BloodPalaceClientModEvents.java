package org.com.bloodpalace.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.bloodpalace.BloodPalace;
import org.com.bloodpalace.entity.BloodPalaceEntityTypes;

@Mod.EventBusSubscriber(modid = BloodPalace.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BloodPalaceClientModEvents {

    private BloodPalaceClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BloodPalaceEntityTypes.ROOM_CORE.get(), RoomCoreRenderer::new);
    }
}
