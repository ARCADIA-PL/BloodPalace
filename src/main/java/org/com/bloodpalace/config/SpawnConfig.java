package org.com.bloodpalace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SpawnConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("bloodpalace-spawns.json");
    private static final Map<String, SpawnPoint> SPAWNS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> CATEGORIES = new LinkedHashMap<>();

    private static boolean loaded;
    private static FileTime lastLoadedModified;

    private SpawnConfig() {
    }

    public static synchronized void init() {
        ensureLoaded();
    }

    public static synchronized Optional<SpawnPoint> get(String dimensionId) {
        ensureLoaded();
        return Optional.ofNullable(SPAWNS.get(dimensionId));
    }

    public static synchronized void set(String dimensionId, SpawnPoint spawnPoint) throws IOException {
        ensureLoaded();
        SPAWNS.put(dimensionId, spawnPoint);
        save();
    }

    public static Path getPath() {
        return CONFIG_PATH;
    }

    private static void ensureLoaded() {
        if (loaded && !hasChangedOnDisk()) return;
        loaded = true;

        if (!Files.exists(CONFIG_PATH)) {
            try {
                save();
            } catch (IOException e) {
                LOGGER.error("BloodPalace: failed to create spawn config {}", CONFIG_PATH, e);
            }
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null && data.spawns != null) {
                SPAWNS.clear();
                SPAWNS.putAll(data.spawns);
            }
            CATEGORIES.clear();
            if (data != null && data.categories != null) {
                CATEGORIES.putAll(data.categories);
            }
            lastLoadedModified = Files.getLastModifiedTime(CONFIG_PATH);
        } catch (IOException | JsonParseException e) {
            LOGGER.error("BloodPalace: failed to load spawn config {}", CONFIG_PATH, e);
        }
    }

    private static void save() throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());

        ConfigData data = new ConfigData();
        data.spawns.putAll(SPAWNS);
        data.categories.putAll(CATEGORIES);

        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
        lastLoadedModified = Files.getLastModifiedTime(CONFIG_PATH);
    }

    private static boolean hasChangedOnDisk() {
        if (!Files.exists(CONFIG_PATH)) return false;
        try {
            FileTime modified = Files.getLastModifiedTime(CONFIG_PATH);
            return lastLoadedModified == null || !modified.equals(lastLoadedModified);
        } catch (IOException e) {
            LOGGER.error("BloodPalace: failed to stat spawn config {}", CONFIG_PATH, e);
            return false;
        }
    }

    private static final class ConfigData {
        private Map<String, SpawnPoint> spawns = new LinkedHashMap<>();
        private Map<String, Set<String>> categories = new LinkedHashMap<>();
    }

    public static final class SpawnPoint {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;

        public SpawnPoint() {
        }

        public SpawnPoint(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
