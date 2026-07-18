package org.com.bloodpalace.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.com.bloodpalace.BloodPalace;

public final class BloodPalaceItems {

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, BloodPalace.MODID);

    public static final RegistryObject<Item> ROOM_CORE = ITEMS.register(
        "room_core",
        () -> new RoomCoreItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> TELEPORT_ANCHOR = ITEMS.register(
        "teleport_anchor",
        () -> new TeleportAnchorItem(new Item.Properties().stacksTo(16)));

    private BloodPalaceItems() {
    }
}
