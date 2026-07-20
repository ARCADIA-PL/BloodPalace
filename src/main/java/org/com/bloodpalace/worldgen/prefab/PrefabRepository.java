package org.com.bloodpalace.worldgen.prefab;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class PrefabRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long CACHE_BUDGET_BYTES = 64L * 1024L * 1024L;
    private static final int DISK_CACHE_SCHEMA = 2;
    private static final PrefabRepository INSTANCE = new PrefabRepository();

    private final Map<VersionedKey, CompletableFuture<Optional<PrefabChunkData>>> inFlight =
        new ConcurrentHashMap<>();
    private final LinkedHashMap<VersionedKey, PrefabChunkData> cache =
        new LinkedHashMap<>(64, 0.75F, true);
    private volatile Snapshot snapshot = new Snapshot(0, Map.of(), Map.of());
    private long cachedBytes;

    private PrefabRepository() {}

    public static PrefabRepository get() {
        return INSTANCE;
    }

    public synchronized void install(Map<ResourceLocation, PrefabManifest> manifests,
            Map<PrefabKey, byte[]> chunks) {
        snapshot = new Snapshot(snapshot.generation + 1, Map.copyOf(manifests), Map.copyOf(chunks));
        inFlight.clear();
        cache.clear();
        cachedBytes = 0;
        LOGGER.info("BloodPalace: loaded {} prefab manifests and {} compressed prefab chunks",
            manifests.size(), chunks.size());
    }

    public boolean hasManifest(ResourceLocation prefabId) {
        return snapshot.manifests.containsKey(prefabId);
    }

    public Optional<PrefabManifest> manifest(ResourceLocation prefabId) {
        return Optional.ofNullable(snapshot.manifests.get(prefabId));
    }

    public String diskCacheFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("schema=" + DISK_CACHE_SCHEMA + "\n").getBytes(StandardCharsets.UTF_8));
            snapshot.manifests.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> digest.update((entry.getKey() + "="
                    + entry.getValue().contentHash() + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public CompletableFuture<Optional<PrefabChunkData>> loadAsync(ResourceLocation prefabId,
            ChunkPos chunkPos, Executor executor) {
        Snapshot current = snapshot;
        PrefabKey key = new PrefabKey(prefabId, chunkPos.x, chunkPos.z);
        byte[] raw = current.chunks.get(key);
        if (raw == null) return CompletableFuture.completedFuture(Optional.empty());

        VersionedKey versionedKey = new VersionedKey(current.generation, key);
        synchronized (this) {
            PrefabChunkData cached = cache.get(versionedKey);
            if (cached != null) return CompletableFuture.completedFuture(Optional.of(cached));
        }
        CompletableFuture<Optional<PrefabChunkData>> future = inFlight.computeIfAbsent(
            versionedKey, ignored -> CompletableFuture.supplyAsync(() -> decode(key, raw), executor));
        future.whenComplete((result, error) -> {
            inFlight.remove(versionedKey, future);
            if (error == null && result.isPresent()) cache(versionedKey, result.get());
        });
        return future;
    }

    public Optional<PrefabChunkData> loadNow(ResourceLocation prefabId, ChunkPos pos) {
        return loadAsync(prefabId, pos, Runnable::run).join();
    }

    private Optional<PrefabChunkData> decode(PrefabKey key, byte[] raw) {
        try {
            PrefabChunkData data = PrefabChunkCodec.decode(raw);
            if (data.chunkX() != key.chunkX || data.chunkZ() != key.chunkZ) {
                throw new IllegalStateException("Chunk coordinates do not match resource path");
            }
            return Optional.of(data);
        } catch (Exception e) {
            LOGGER.error("BloodPalace: failed to decode prefab chunk {} at {},{}",
                key.prefabId, key.chunkX, key.chunkZ, e);
            return Optional.empty();
        }
    }

    private synchronized void cache(VersionedKey key, PrefabChunkData data) {
        if (key.generation != snapshot.generation) return;
        PrefabChunkData previous = cache.put(key, data);
        if (previous != null) cachedBytes -= previous.estimatedBytes();
        cachedBytes += data.estimatedBytes();
        while (cachedBytes > CACHE_BUDGET_BYTES && !cache.isEmpty()) {
            Map.Entry<VersionedKey, PrefabChunkData> eldest = cache.entrySet().iterator().next();
            cachedBytes -= eldest.getValue().estimatedBytes();
            cache.remove(eldest.getKey());
        }
    }

    public record PrefabKey(ResourceLocation prefabId, int chunkX, int chunkZ) {}
    private record VersionedKey(long generation, PrefabKey key) {}
    private record Snapshot(long generation, Map<ResourceLocation, PrefabManifest> manifests,
                            Map<PrefabKey, byte[]> chunks) {}
}
