package org.com.bloodpalace.worldgen.prefab;

import net.minecraft.resources.ResourceLocation;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class PrefabBundleCodec {
    private static final int MAGIC = 0x42504642;
    private static final int STORAGE_FORMAT = 1;
    private static final int MAX_CHUNKS = 100_000;
    private static final int MAX_CHUNK_BYTES = 64 * 1024 * 1024;

    private PrefabBundleCodec() {}

    public static void writeHeader(DataOutputStream output, int chunkCount) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(STORAGE_FORMAT);
        output.writeInt(chunkCount);
    }

    public static void writeChunk(DataOutputStream output, int chunkX, int chunkZ, byte[] data)
            throws IOException {
        output.writeInt(chunkX);
        output.writeInt(chunkZ);
        output.writeInt(data.length);
        output.write(data);
    }

    public static Map<PrefabRepository.PrefabKey, byte[]> read(ResourceLocation prefabId,
            InputStream input) throws IOException {
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(input))) {
            if (data.readInt() != MAGIC) throw new IOException("Invalid prefab bundle magic");
            int format = data.readInt();
            if (format != STORAGE_FORMAT) {
                throw new IOException("Unsupported prefab bundle format " + format);
            }
            int count = data.readInt();
            if (count < 0 || count > MAX_CHUNKS) {
                throw new IOException("Invalid prefab bundle chunk count " + count);
            }

            Map<PrefabRepository.PrefabKey, byte[]> chunks = new HashMap<>(count * 2);
            for (int i = 0; i < count; i++) {
                int chunkX = data.readInt();
                int chunkZ = data.readInt();
                int length = data.readInt();
                if (length < 0 || length > MAX_CHUNK_BYTES) {
                    throw new IOException("Invalid prefab chunk length " + length);
                }
                byte[] raw = data.readNBytes(length);
                if (raw.length != length) throw new IOException("Truncated prefab bundle");
                PrefabRepository.PrefabKey key =
                    new PrefabRepository.PrefabKey(prefabId, chunkX, chunkZ);
                if (chunks.put(key, raw) != null) {
                    throw new IOException("Duplicate prefab chunk " + chunkX + "," + chunkZ);
                }
            }
            if (data.read() != -1) throw new IOException("Trailing data in prefab bundle");
            return chunks;
        }
    }
}
