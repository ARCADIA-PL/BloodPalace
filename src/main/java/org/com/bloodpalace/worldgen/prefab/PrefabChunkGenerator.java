package org.com.bloodpalace.worldgen.prefab;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class PrefabChunkGenerator extends ChunkGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<PrefabChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("prefab").forGetter(generator -> generator.prefabId),
            Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome),
            Codec.INT.optionalFieldOf("min_y", -64).forGetter(generator -> generator.minY),
            Codec.INT.optionalFieldOf("height", 384).forGetter(generator -> generator.height)
        ).apply(instance, PrefabChunkGenerator::new));

    private final ResourceLocation prefabId;
    private final Holder<Biome> biome;
    private final int minY;
    private final int height;

    public PrefabChunkGenerator(ResourceLocation prefabId, Holder<Biome> biome, int minY, int height) {
        super(new FixedBiomeSource(biome));
        this.prefabId = prefabId;
        this.biome = biome;
        this.minY = minY;
        this.height = height;
    }

    public ResourceLocation prefabId() {
        return prefabId;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
            RandomState randomState, StructureManager structures, ChunkAccess chunk) {
        PrefabRepository repository = PrefabRepository.get();
        if (!repository.hasManifest(prefabId)) {
            LOGGER.error("BloodPalace: prefab generator references missing manifest {}", prefabId);
            return CompletableFuture.completedFuture(chunk);
        }
        return repository.loadAsync(prefabId, chunk.getPos(), executor).thenApply(data -> {
            data.ifPresent(prefab -> PrefabChunkApplier.apply(chunk, prefab));
            return chunk;
        });
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures,
            RandomState randomState, ChunkAccess chunk) {}

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
            BiomeManager biomeManager, StructureManager structures, ChunkAccess chunk,
            GenerationStep.Carving carving) {}

    @Override
    public void createStructures(RegistryAccess registries,
            ChunkGeneratorStructureState structureState, StructureManager structures,
            ChunkAccess chunk, StructureTemplateManager templates) {}

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structures,
            ChunkAccess chunk) {}

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structures) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        PrefabRepository.get().loadNow(prefabId, region.getCenter()).ifPresent(data -> {
            for (CompoundTag source : data.entities()) {
                if (!belongsToChunk(source, data)) continue;
                EntityType.loadEntityRecursive(source.copy(), region.getLevel(), entity -> {
                    if (entity instanceof Mob) return null;
                    region.addFreshEntity(entity);
                    return entity;
                });
            }
        });
    }

    private static boolean belongsToChunk(CompoundTag entityTag, PrefabChunkData chunk) {
        ListTag pos = entityTag.getList("Pos", Tag.TAG_DOUBLE);
        if (pos.size() < 3) return false;
        int chunkX = ((int) Math.floor(pos.getDouble(0))) >> 4;
        int chunkZ = ((int) Math.floor(pos.getDouble(2))) >> 4;
        return chunkX == chunk.chunkX() && chunkZ == chunk.chunkZ();
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        return minY;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
            LevelHeightAccessor heightAccessor, RandomState randomState) {
        Optional<PrefabChunkData> data = dataAt(x, z);
        if (data.isEmpty()) return minY;
        for (int y = minY + height - 1; y >= minY; y--) {
            if (type.isOpaque().test(data.get().stateAt(x & 15, y, z & 15))) return y + 1;
        }
        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        BlockState[] states = new BlockState[height];
        Optional<PrefabChunkData> data = dataAt(x, z);
        for (int offset = 0; offset < height; offset++) {
            int y = minY + offset;
            states[offset] = data.map(chunk -> chunk.stateAt(x & 15, y, z & 15))
                .orElseGet(() -> Blocks.AIR.defaultBlockState());
        }
        return new NoiseColumn(minY, states);
    }

    private Optional<PrefabChunkData> dataAt(int blockX, int blockZ) {
        return PrefabRepository.get().loadNow(prefabId,
            new net.minecraft.world.level.ChunkPos(blockX >> 4, blockZ >> 4));
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        lines.add("BloodPalace prefab: " + prefabId);
    }
}
