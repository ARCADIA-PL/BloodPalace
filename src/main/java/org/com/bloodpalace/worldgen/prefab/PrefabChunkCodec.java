package org.com.bloodpalace.worldgen.prefab;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public final class PrefabChunkCodec {
    private PrefabChunkCodec() {}

    public static PrefabChunkData decode(byte[] compressedNbt) throws IOException {
        CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(compressedNbt));
        if (root.getInt("Format") != PrefabManifest.CURRENT_FORMAT) {
            throw new IOException("Unsupported prefab chunk format");
        }
        Map<Integer, PrefabSection> sections = readSections(root);
        List<CompoundTag> blockEntities = new ArrayList<>();
        ListTag tags = root.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) blockEntities.add(tags.getCompound(i).copy());
        List<CompoundTag> entities = new ArrayList<>();
        tags = root.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) entities.add(tags.getCompound(i).copy());
        return new PrefabChunkData(root.getInt("ChunkX"), root.getInt("ChunkZ"),
            root.getInt("MinY"), root.getInt("Height"), Map.copyOf(sections),
            List.copyOf(blockEntities), List.copyOf(entities));
    }

    private static Map<Integer, PrefabSection> readSections(CompoundTag root) throws IOException {
        Map<Integer, PrefabSection> sections = new HashMap<>();
        ListTag sectionTags = root.getList("Sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sectionTags.size(); i++) {
            CompoundTag sectionTag = sectionTags.getCompound(i);
            ListTag paletteTags = sectionTag.getList("Palette", Tag.TAG_COMPOUND);
            List<BlockState> palette = new ArrayList<>(paletteTags.size());
            for (int j = 0; j < paletteTags.size(); j++) {
                palette.add(NbtUtils.readBlockState(
                    BuiltInRegistries.BLOCK.asLookup(), paletteTags.getCompound(j)));
            }
            int bits = sectionTag.getInt("Bits");
            long[] data = sectionTag.getLongArray("Data");
            validate(palette, bits, data);
            int sectionY = sectionTag.getInt("Y");
            sections.put(sectionY, new PrefabSection(sectionY, List.copyOf(palette), bits, data));
        }
        return sections;
    }

    private static void validate(List<BlockState> palette, int bits, long[] data) throws IOException {
        if (palette.isEmpty() || bits < 1 || bits > 31) {
            throw new IOException("Invalid prefab section palette");
        }
        int entriesPerLong = 64 / bits;
        int expected = (4096 + entriesPerLong - 1) / entriesPerLong;
        if (data.length != expected) throw new IOException("Invalid packed block data length");
    }
}
