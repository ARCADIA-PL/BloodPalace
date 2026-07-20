package org.com.bloodpalace.worldgen.prefab;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PrefabExporter {
    private static final int PACK_FORMAT = 15;

    private PrefabExporter() {}

    public static Result export(ServerLevel level, ResourceLocation prefabId,
            int centerChunkX, int centerChunkZ, int radius) throws IOException {
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        Path packRoot = level.getServer().getServerDirectory().toPath()
            .resolve("bloodpalace").resolve("exports").resolve(safe(prefabId));
        Path prefabRoot = packRoot.resolve("data").resolve(prefabId.getNamespace())
            .resolve("bloodpalace_prefabs").resolve(prefabId.getPath());
        Path chunksRoot = prefabRoot.resolve("chunks");
        Files.createDirectories(chunksRoot);

        MessageDigest digest = sha256();
        Map<Long, List<CompoundTag>> entitiesByChunk = collectEntities(level);
        int chunkCount = 0;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                byte[] encoded = encodeChunk(chunk,
                    entitiesByChunk.getOrDefault(chunk.getPos().toLong(), List.of()));
                digest.update(encoded);
                Files.write(chunksRoot.resolve(chunkX + "." + chunkZ + ".nbt"), encoded);
                chunkCount++;
            }
        }

        String hash = HexFormat.of().formatHex(digest.digest());
        writeManifest(prefabRoot.resolve("manifest.json"), minChunkX, maxChunkX,
            minChunkZ, maxChunkZ, level.getMinBuildHeight(), level.getHeight(), hash);
        writePackMeta(packRoot.resolve("pack.mcmeta"), prefabId);
        return new Result(packRoot, chunkCount, hash);
    }

    private static byte[] encodeChunk(LevelChunk chunk, List<CompoundTag> entities) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("Format", PrefabManifest.CURRENT_FORMAT);
        root.putInt("ChunkX", chunk.getPos().x);
        root.putInt("ChunkZ", chunk.getPos().z);
        root.putInt("MinY", chunk.getMinBuildHeight());
        root.putInt("Height", chunk.getHeight());
        root.put("Sections", encodeSections(chunk));

        ListTag blockEntities = new ListTag();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            blockEntities.add(blockEntity.saveWithFullMetadata());
        }
        root.put("BlockEntities", blockEntities);
        ListTag entityTags = new ListTag();
        entities.forEach(tag -> entityTags.add(tag.copy()));
        root.put("Entities", entityTags);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, output);
            return output.toByteArray();
        }
    }

    private static Map<Long, List<CompoundTag>> collectEntities(ServerLevel level) {
        Map<Long, List<CompoundTag>> result = new HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player || entity.isPassenger()) continue;
            CompoundTag tag = new CompoundTag();
            if (!entity.save(tag)) continue;
            tag.remove("UUID");
            tag.remove("UUIDMost");
            tag.remove("UUIDLeast");
            result.computeIfAbsent(entity.chunkPosition().toLong(), ignored -> new ArrayList<>()).add(tag);
        }
        return result;
    }

    private static ListTag encodeSections(LevelChunk chunk) {
        ListTag sections = new ListTag();
        LevelChunkSection[] sourceSections = chunk.getSections();
        for (int index = 0; index < sourceSections.length; index++) {
            LevelChunkSection source = sourceSections[index];
            if (source.hasOnlyAir()) continue;
            sections.add(encodeSection(chunk.getSectionYFromSectionIndex(index), source));
        }
        return sections;
    }

    private static CompoundTag encodeSection(int sectionY, LevelChunkSection section) {
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag palette = new ListTag();
        int[] indices = new int[4096];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    int index = paletteIndex.computeIfAbsent(state, key -> {
                        palette.add(NbtUtils.writeBlockState(key));
                        return palette.size() - 1;
                    });
                    indices[(y << 8) | (z << 4) | x] = index;
                }
            }
        }
        int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        CompoundTag tag = new CompoundTag();
        tag.putInt("Y", sectionY);
        tag.putInt("Bits", bits);
        tag.put("Palette", palette);
        tag.putLongArray("Data", pack(indices, bits));
        return tag;
    }

    private static long[] pack(int[] indices, int bits) {
        int entriesPerLong = 64 / bits;
        long[] packed = new long[(indices.length + entriesPerLong - 1) / entriesPerLong];
        for (int i = 0; i < indices.length; i++) {
            packed[i / entriesPerLong] |= (long) indices[i]
                << ((i % entriesPerLong) * bits);
        }
        return packed;
    }

    private static void writeManifest(Path path, int minChunkX, int maxChunkX,
            int minChunkZ, int maxChunkZ, int minY, int height, String hash) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("format", PrefabManifest.CURRENT_FORMAT);
        json.addProperty("data_version", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        json.addProperty("min_chunk_x", minChunkX);
        json.addProperty("max_chunk_x", maxChunkX);
        json.addProperty("min_chunk_z", minChunkZ);
        json.addProperty("max_chunk_z", maxChunkZ);
        json.addProperty("min_y", minY);
        json.addProperty("height", height);
        json.addProperty("content_hash", "sha256:" + hash);
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json),
            StandardCharsets.UTF_8);
    }

    private static void writePackMeta(Path path, ResourceLocation prefabId) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "BloodPalace prefab " + prefabId);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root),
            StandardCharsets.UTF_8);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String safe(ResourceLocation id) {
        return (id.getNamespace() + "_" + id.getPath()).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record Result(Path packRoot, int chunkCount, String contentHash) {}
}
