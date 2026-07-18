package org.com.bloodpalace.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
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

    @Mod.EventBusSubscriber(modid = BloodPalace.MODID, value = Dist.CLIENT)
    public static final class ForgeEvents {

        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                RoomOverlayRenderer.tick();
            }
        }

        @SubscribeEvent
        public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
            if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
            RoomOverlayRenderer.render(
                event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
        }
    }
}
