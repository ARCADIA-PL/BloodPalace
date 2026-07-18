package org.com.bloodpalace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RoomConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("bloodpalace-rooms.json");
    private static final String DEFAULT_RESOURCE = "/defaultconfigs/bloodpalace-rooms.json";
    private static final Map<String, List<Room>> ROOMS = new LinkedHashMap<>();

    private static boolean loaded;

    private RoomConfig() {
    }

    public static synchronized void init() {
        ensureLoaded();
    }

    public static synchronized List<Room> list(String dimensionId) {
        ensureLoaded();
        return List.copyOf(ROOMS.getOrDefault(dimensionId, List.of()));
    }

    public static synchronized Optional<Room> get(String dimensionId, String roomId) {
        ensureLoaded();
        return ROOMS.getOrDefault(dimensionId, List.of()).stream()
            .filter(room -> room.id.equals(roomId))
            .findFirst();
    }

    public static synchronized void set(String dimensionId, Room room) throws IOException {
        ensureLoaded();
        List<Room> rooms = ROOMS.computeIfAbsent(dimensionId, ignored -> new ArrayList<>());
        rooms.removeIf(existing -> existing.id.equals(room.id));
        rooms.add(room);
        save();
    }

    public static synchronized boolean delete(String dimensionId, String roomId) throws IOException {
        ensureLoaded();
        List<Room> rooms = ROOMS.get(dimensionId);
        if (rooms == null) return false;
        boolean removed = rooms.removeIf(existing -> existing.id.equals(roomId));
        if (rooms.isEmpty()) ROOMS.remove(dimensionId);
        if (removed) save();
        return removed;
    }

    public static Path getPath() {
        return CONFIG_PATH;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        if (!Files.exists(CONFIG_PATH)) {
            try {
                loadDefaults();
                save();
            } catch (IOException e) {
                LOGGER.error("BloodPalace: failed to create room config {}", CONFIG_PATH, e);
            }
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            ROOMS.clear();
            if (data != null && data.rooms != null) {
                data.rooms.forEach((dimensionId, rooms) ->
                    ROOMS.put(dimensionId, new ArrayList<>(rooms)));
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.error("BloodPalace: failed to load room config {}", CONFIG_PATH, e);
        }
    }

    private static void loadDefaults() throws IOException {
        try (Reader reader = openDefaultConfig()) {
            if (reader == null) {
                return;
            }
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            ROOMS.clear();
            if (data != null && data.rooms != null) {
                data.rooms.forEach((dimensionId, rooms) ->
                    ROOMS.put(dimensionId, new ArrayList<>(rooms)));
            }
        }
    }

    private static Reader openDefaultConfig() {
        var stream = RoomConfig.class.getResourceAsStream(DEFAULT_RESOURCE);
        if (stream == null) {
            LOGGER.warn("BloodPalace: default room config resource {} not found", DEFAULT_RESOURCE);
            return null;
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static void save() throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());

        ConfigData data = new ConfigData();
        data.rooms.putAll(ROOMS);

        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
    }

    private static final class ConfigData {
        private Map<String, List<Room>> rooms = new LinkedHashMap<>();
    }

    public static final class Room {
        public String id;
        public String name;
        public Pos min;
        public Pos max;

        public Room() {
        }

        public Room(String id, String name, Pos min, Pos max) {
            this.id = id;
            this.name = name;
            this.min = min;
            this.max = max;
        }
    }

    public static final class Pos {
        public int x;
        public int y;
        public int z;

        public Pos() {
        }

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
