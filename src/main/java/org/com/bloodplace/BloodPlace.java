package org.com.bloodplace;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BloodPlace.MODID)
public class BloodPlace {

    public static final String MODID = "bloodplace";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BloodPlace() {
        LOGGER.info("BloodPlace mod initialized");
    }
}
