package org.com.bloodpalace.worldgen.prefab;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.security.MessageDigest;

public final class PrefabReloadListener extends SimplePreparableReloadListener<PrefabReloadListener.Prepared> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOT = "bloodpalace_prefabs";

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, PrefabManifest> manifests = new HashMap<>();
        Map<PrefabRepository.PrefabKey, byte[]> chunks = new HashMap<>();
        manager.listResources(ROOT, id -> id.getPath().endsWith("/manifest.json"))
            .forEach((id, resource) -> readManifest(id, resource, manifests));
        manager.listResources(ROOT, id -> id.getPath().endsWith(".nbt"))
            .forEach((id, resource) -> readChunk(id, resource, chunks));
        manifests.entrySet().removeIf(entry -> !validate(entry.getValue(), chunks));
        chunks.keySet().removeIf(key -> {
            PrefabManifest manifest = manifests.get(key.prefabId());
            return manifest == null || !manifest.contains(key.chunkX(), key.chunkZ());
        });
        return new Prepared(Map.copyOf(manifests), Map.copyOf(chunks));
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        PrefabRepository.get().install(prepared.manifests, prepared.chunks);
    }

    private static void readManifest(ResourceLocation resourceId, Resource resource,
            Map<ResourceLocation, PrefabManifest> manifests) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            ResourceLocation prefabId = prefabId(resourceId, "/manifest.json");
            PrefabManifest manifest = new PrefabManifest(prefabId,
                GsonHelper.getAsInt(json, "format"), GsonHelper.getAsInt(json, "data_version", 0),
                GsonHelper.getAsInt(json, "min_chunk_x"), GsonHelper.getAsInt(json, "max_chunk_x"),
                GsonHelper.getAsInt(json, "min_chunk_z"), GsonHelper.getAsInt(json, "max_chunk_z"),
                GsonHelper.getAsInt(json, "min_y"), GsonHelper.getAsInt(json, "height"),
                GsonHelper.getAsString(json, "content_hash", ""));
            if (manifest.format() != PrefabManifest.CURRENT_FORMAT) {
                throw new IllegalArgumentException("unsupported format " + manifest.format());
            }
            int currentDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            if (manifest.dataVersion() != currentDataVersion) {
                LOGGER.warn("BloodPalace: prefab {} was exported with DataVersion {}, current is {}",
                    prefabId, manifest.dataVersion(), currentDataVersion);
            }
            manifests.put(prefabId, manifest);
        } catch (Exception e) {
            LOGGER.error("BloodPalace: failed to load prefab manifest {}", resourceId, e);
        }
    }

    private static void readChunk(ResourceLocation resourceId, Resource resource,
            Map<PrefabRepository.PrefabKey, byte[]> chunks) {
        String path = resourceId.getPath();
        int marker = path.lastIndexOf("/chunks/");
        if (marker < ROOT.length()) return;
        String fileName = path.substring(marker + 8, path.length() - 4);
        String[] coordinates = fileName.split("\\.", 2);
        if (coordinates.length != 2) return;
        try (InputStream input = resource.open()) {
            ResourceLocation prefabId = ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(),
                path.substring(ROOT.length() + 1, marker));
            chunks.put(new PrefabRepository.PrefabKey(prefabId,
                Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])), input.readAllBytes());
        } catch (Exception e) {
            LOGGER.error("BloodPalace: failed to load prefab chunk {}", resourceId, e);
        }
    }

    private static ResourceLocation prefabId(ResourceLocation resourceId, String suffix) {
        String path = resourceId.getPath();
        return ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(),
            path.substring(ROOT.length() + 1, path.length() - suffix.length()));
    }

    private static boolean validate(PrefabManifest manifest,
            Map<PrefabRepository.PrefabKey, byte[]> chunks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int z = manifest.minChunkZ(); z <= manifest.maxChunkZ(); z++) {
                for (int x = manifest.minChunkX(); x <= manifest.maxChunkX(); x++) {
                    byte[] data = chunks.get(new PrefabRepository.PrefabKey(manifest.id(), x, z));
                    if (data == null) throw new IllegalStateException("missing chunk " + x + "," + z);
                    digest.update(data);
                }
            }
            String actual = "sha256:" + HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(manifest.contentHash())) {
                throw new IllegalStateException("content hash mismatch");
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("BloodPalace: invalid prefab {}: {}", manifest.id(), e.getMessage());
            return false;
        }
    }

    record Prepared(Map<ResourceLocation, PrefabManifest> manifests,
                    Map<PrefabRepository.PrefabKey, byte[]> chunks) {}
}
