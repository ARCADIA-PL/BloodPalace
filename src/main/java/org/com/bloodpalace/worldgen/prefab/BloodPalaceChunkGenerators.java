package org.com.bloodpalace.worldgen.prefab;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.com.bloodpalace.BloodPalace;

public final class BloodPalaceChunkGenerators {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, BloodPalace.MODID);

    public static final RegistryObject<Codec<? extends ChunkGenerator>> PREFAB =
        CHUNK_GENERATORS.register("prefab", () -> PrefabChunkGenerator.CODEC);

    private BloodPalaceChunkGenerators() {
    }
}
