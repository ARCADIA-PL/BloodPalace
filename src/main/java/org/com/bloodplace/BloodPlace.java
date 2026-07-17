package org.com.bloodplace;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.bloodplace.command.BloodPlaceCommand;
import org.com.bloodplace.config.SpawnConfig;
import org.com.bloodplace.handler.ShowcaseHandler;
import org.slf4j.Logger;

@Mod(BloodPlace.MODID)
public class BloodPlace {

    public static final String MODID = "bloodplace";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BloodPlace() {
        LOGGER.info("BloodPlace mod initialized");
        SpawnConfig.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ShowcaseHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BloodPlaceCommand.register(event.getDispatcher());
    }
}
