package org.com.bloodpalace;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.com.bloodpalace.command.BloodPalaceCommand;
import org.com.bloodpalace.config.RoomConfig;
import org.com.bloodpalace.config.SpawnConfig;
import org.com.bloodpalace.entity.BloodPalaceEntityTypes;
import org.com.bloodpalace.handler.ShowcaseHandler;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.worldgen.placement.BloodPalacePlacementTypes;
import org.slf4j.Logger;

@SuppressWarnings("removal")
@Mod(BloodPalace.MODID)
public class BloodPalace {

    public static final String MODID = "bloodpalace";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BloodPalace() {
        LOGGER.info("BloodPalace mod initialized");
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BloodPalacePlacementTypes.STRUCTURE_PLACEMENT_TYPES.register(modEventBus);
        BloodPalaceEntityTypes.ENTITY_TYPES.register(modEventBus);
        BloodPalaceNetwork.register();
        SpawnConfig.init();
        RoomConfig.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ShowcaseHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BloodPalaceCommand.register(event.getDispatcher());
    }
}
