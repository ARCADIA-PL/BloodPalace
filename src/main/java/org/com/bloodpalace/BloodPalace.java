package org.com.bloodpalace;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.bloodpalace.command.BloodPalaceCommand;
import org.com.bloodpalace.config.SpawnConfig;
import org.com.bloodpalace.handler.ShowcaseHandler;
import org.slf4j.Logger;

@Mod(BloodPalace.MODID)
public class BloodPalace {

    public static final String MODID = "bloodpalace";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BloodPalace() {
        LOGGER.info("BloodPalace mod initialized");
        SpawnConfig.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ShowcaseHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BloodPalaceCommand.register(event.getDispatcher());
    }
}
