package org.com.bloodpalace.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.com.bloodpalace.BloodPalace;

public final class BloodPalaceEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BloodPalace.MODID);

    public static final RegistryObject<EntityType<RoomCoreEntity>> ROOM_CORE = ENTITY_TYPES.register(
        "room_core",
        () -> EntityType.Builder.<RoomCoreEntity>of(RoomCoreEntity::new, MobCategory.MISC)
            .sized(0.7F, 0.9F)
            .clientTrackingRange(512)
            .updateInterval(10)
            .build(BloodPalace.MODID + ":room_core"));

    private BloodPalaceEntityTypes() {
    }
}
