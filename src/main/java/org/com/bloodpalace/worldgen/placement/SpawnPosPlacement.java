package org.com.bloodpalace.worldgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

public class SpawnPosPlacement extends StructurePlacement {

    public static final MapCodec<SpawnPosPlacement> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.stable(new SpawnPosPlacement()));

    protected SpawnPosPlacement() {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, 114514, Optional.empty());
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return chunkX == 0 && chunkZ == 0;
    }

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState structureState, int chunkX, int chunkZ) {
        return isPlacementChunk(structureState, chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type() {
        return BloodPalacePlacementTypes.SPAWN_POS_PLACEMENT.get();
    }
}
