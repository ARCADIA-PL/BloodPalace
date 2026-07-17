package org.com.bloodpalace.worldgen.placement;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.com.bloodpalace.BloodPalace;

public final class BloodPalacePlacementTypes {

    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENT_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, BloodPalace.MODID);

    public static final RegistryObject<StructurePlacementType<SpawnPosPlacement>> SPAWN_POS_PLACEMENT =
        STRUCTURE_PLACEMENT_TYPES.register("spawn_pos_placement", () -> SpawnPosPlacement.CODEC::codec);

    private BloodPalacePlacementTypes() {
    }
}
