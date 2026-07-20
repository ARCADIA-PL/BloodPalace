package org.com.bloodpalace.worldgen.prefab;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

public final class PrefabChunkApplier {
    private static final EnumSet<Heightmap.Types> HEIGHTMAPS = EnumSet.allOf(Heightmap.Types.class);

    private PrefabChunkApplier() {}

    public static void apply(ChunkAccess chunk, PrefabChunkData prefab) {
        for (PrefabSection source : prefab.sections().values()) {
            if (source.sectionY() < chunk.getMinSection() || source.sectionY() >= chunk.getMaxSection()) continue;
            LevelChunkSection target = chunk.getSection(chunk.getSectionIndexFromSectionY(source.sectionY()));
            target.acquire();
            try {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            target.setBlockState(x, y, z, source.stateAt(x, y, z), false);
                        }
                    }
                }
            } finally {
                target.release();
            }
            target.recalcBlockCounts();
        }

        for (CompoundTag source : prefab.blockEntities()) {
            CompoundTag tag = source.copy();
            tag.putInt("x", chunk.getPos().getMinBlockX() + Math.floorMod(tag.getInt("x"), 16));
            tag.putInt("z", chunk.getPos().getMinBlockZ() + Math.floorMod(tag.getInt("z"), 16));
            chunk.setBlockEntityNbt(tag);
        }
        Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
        chunk.initializeLightSources();
        chunk.setUnsaved(true);
    }
}
