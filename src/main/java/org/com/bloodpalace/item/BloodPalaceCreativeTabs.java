package org.com.bloodpalace.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.com.bloodpalace.BloodPalace;

public final class BloodPalaceCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BloodPalace.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.bloodpalace.main"))
            .icon(() -> new ItemStack(BloodPalaceItems.TELEPORT_ANCHOR.get()))
            .displayItems((params, output) -> {
                output.accept(BloodPalaceItems.ROOM_CORE.get());
                output.accept(BloodPalaceItems.TELEPORT_ANCHOR.get());
            })
            .build());

    private BloodPalaceCreativeTabs() {
    }
}
