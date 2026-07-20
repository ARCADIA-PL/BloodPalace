package org.com.bloodpalace.worldgen.prefab;

import net.minecraft.resources.ResourceLocation;

public record PrefabManifest(
        ResourceLocation id,
        int format,
        int dataVersion,
        int minChunkX,
        int maxChunkX,
        int minChunkZ,
        int maxChunkZ,
        int minY,
        int height,
        String contentHash) {

    public static final int CURRENT_FORMAT = 1;

    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX
            && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }
}
