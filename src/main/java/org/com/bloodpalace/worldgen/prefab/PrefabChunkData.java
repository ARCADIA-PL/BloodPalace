package org.com.bloodpalace.worldgen.prefab;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

public record PrefabChunkData(
        int chunkX,
        int chunkZ,
        int minY,
        int height,
        Map<Integer, PrefabSection> sections,
        List<CompoundTag> blockEntities,
        List<CompoundTag> entities) {

    public BlockState stateAt(int localX, int y, int localZ) {
        PrefabSection section = sections.get(Math.floorDiv(y, 16));
        if (section == null) return Blocks.AIR.defaultBlockState();
        return section.stateAt(localX & 15, y & 15, localZ & 15);
    }

    public int estimatedBytes() {
        int bytes = 128 + blockEntities.size() * 256 + entities.size() * 512;
        for (PrefabSection section : sections.values()) bytes += section.estimatedBytes();
        return bytes;
    }
}
