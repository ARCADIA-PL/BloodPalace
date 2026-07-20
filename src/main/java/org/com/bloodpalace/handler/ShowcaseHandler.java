package org.com.bloodpalace.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.com.bloodpalace.util.ShowcaseBlockCleaner;
import org.com.bloodpalace.util.ChunkyPreloadCoordinator;
import org.com.bloodpalace.util.ShowcaseDimensions;
import org.com.bloodpalace.util.ShowcaseTeleports;
import org.com.bloodpalace.util.RoomCoreManager;
import org.com.bloodpalace.room.RoomRuntimeManager;
import org.com.bloodpalace.worldgen.prefab.PrefabChunkGenerator;
import org.com.bloodpalace.worldgen.prefab.PrefabRepository;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShowcaseHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BORDER_SIZE = 500;
    private static final int RESET_DELAY_TICKS = 200;
    private static final int RESET_RETRY_DELAY_TICKS = 100;
    private static final String PENDING_RESET_MARKER = "pending-prefab-resets.txt";
    private static final String STATE_FOLDER = "bloodpalace";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String PREFAB_CACHE_MARKER = "prefab-cache.fingerprint";

    private static final Map<ResourceLocation, Boolean> RESET_STARTED = new HashMap<>();
    private static final Map<ResourceLocation, Integer> RESET_COUNTDOWN = new HashMap<>();
    private static final Map<ResourceLocation, Integer> RESET_RETRY_COUNTDOWN = new HashMap<>();
    private static final Set<ResourceLocation> RESET_QUEUE = new HashSet<>();
    private static final Set<ResourceLocation> DIRTY_PRODUCTION_DIMENSIONS = new HashSet<>();
    private static final Set<ResourceLocation> STARTUP_RECOVERY_DIMENSIONS = new HashSet<>();
    private static final Map<UUID, ResourceLocation> DEATH_SHOWCASE_ORIGINS = new HashMap<>();
    private static final Map<UUID, ResourceLocation> LAST_KNOWN_SHOWCASE_DIMENSIONS = new HashMap<>();

    // Structure → preload chunk radius (max_distance_from_center / 16, rounded up)
    private static final Map<String, Integer> HEAVY_STRUCTURES = new LinkedHashMap<>();
    static {
        HEAVY_STRUCTURES.put("keep_kayra",        16);
        HEAVY_STRUCTURES.put("mechanical_nest",   16);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ChunkyPreloadCoordinator.clear(event.getServer());
        clearTransientResetState();
        loadPendingProductionResets(event.getServer());
        invalidateChangedPrefabCaches(event.getServer());
        /*preloadHeavyStructuresSync(event.getServer());*/
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ChunkyPreloadCoordinator.clear(event.getServer());
        clearTransientResetState();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation dimensionId = player.level().dimension().location();
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)) return;
        boolean requiresRecovery = STARTUP_RECOVERY_DIMENSIONS.contains(dimensionId);
        cancelResetForOccupiedDimension(dimensionId);
        LAST_KNOWN_SHOWCASE_DIMENSIONS.put(player.getUUID(), dimensionId);
        markProductionDimensionDirty(player.getServer(), dimensionId);
        RoomCoreManager.ensureForDimension(player.serverLevel());
        if (requiresRecovery) {
            evacuatePlayerForPendingReset(player, dimensionId);
        }
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation toDim = event.getTo().location();
        ResourceLocation fromDim = event.getFrom().location();

        if (ShowcaseDimensions.isShowcaseDimension(toDim)) {
            cancelResetForOccupiedDimension(toDim);
            LAST_KNOWN_SHOWCASE_DIMENSIONS.put(player.getUUID(), toDim);
            markProductionDimensionDirty(player.getServer(), toDim);

            WorldBorder border = player.serverLevel().getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(BORDER_SIZE);
            if (!ShowcaseTeleports.consumeSpawnEventSkip(player, toDim)) {
                ShowcaseTeleports.teleportToConfiguredSpawn(player, toDim);
            }
            clearLoadedShowcaseMobs(player.serverLevel());
            if (!(player.serverLevel().getChunkSource().getGenerator()
                    instanceof PrefabChunkGenerator)) {
                clearLoadedShowcaseBlocks(player.serverLevel());
            }
            RoomCoreManager.ensureForDimension(player.serverLevel());
        }

        if (ShowcaseDimensions.isShowcaseDimension(fromDim)) {
            if (fromDim.equals(LAST_KNOWN_SHOWCASE_DIMENSIONS.get(player.getUUID()))) {
                LAST_KNOWN_SHOWCASE_DIMENSIONS.remove(player.getUUID());
            }
            scheduleEmptyShowcaseReset(player.getServer(), fromDim);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        ChunkyPreloadCoordinator.tick(event.getServer());
        reconcileShowcasePlayerLocations(event.getServer());
        tickQueuedShowcaseResets(event.getServer());
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        if (!(event.level instanceof ServerLevel level)) return;
        RoomRuntimeManager.tick(level);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ShowcaseDimensions.isShowcaseDimension(event.getPlayer().level().dimension().location())) return;
        if (event.getPlayer().isCreative()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!ShowcaseDimensions.isShowcaseDimension(player.level().dimension().location())) return;
        if (player.isCreative()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob)) return;
        if (!ShowcaseDimensions.isShowcaseDimension(event.getLevel().dimension().location())) return;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!ShowcaseDimensions.isShowcaseDimension(level.dimension().location())) return;
        if (level.getChunkSource().getGenerator() instanceof PrefabChunkGenerator) return;
        level.getServer().tell(new TickTask(level.getServer().getTickCount(), () ->
            clearChunkShowcaseBlocks(level, chunk)));
    }

    // ═══════════════════════════════════════════
    //  Sync preload — runs during server startup,
    //  before any player can join the world.
    // ═══════════════════════════════════════════

    /**
     * Preload during server startup — before any player can join.
     * Uses per-structure radius (= max_distance_from_center / 16) and
     * ChunkStatus.STRUCTURE_STARTS to skip expensive post-processing.
     * Chunks persist on disk, so 2nd+ restart is instant.
     */
    private void preloadHeavyStructuresSync(MinecraftServer server) {
        long start = System.currentTimeMillis();

        for (var entry : HEAVY_STRUCTURES.entrySet()) {
            String name = entry.getKey();
            int r = entry.getValue();
            ResourceKey<Level> key = ShowcaseDimensions.dimensionKeyForStructure(name);
            ServerLevel level = server.getLevel(key);
            if (level == null) continue;

            LOGGER.info("BloodPalace: preloading {} ({}×{}chunks)", name, r*2+1, r*2+1);
            for (int cx = -r; cx <= r; cx++) {
                for (int cz = -r; cz <= r; cz++) {
                    level.getChunk(cx, cz, ChunkStatus.FULL, true);
                }
            }
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        LOGGER.info("BloodPalace: {} structures preloaded in {}s", HEAVY_STRUCTURES.size(), elapsed);
    }

    // ═══════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════

    private void clearLoadedShowcaseMobs(ServerLevel level) {
        AABB bounds = new AABB(
            -BORDER_SIZE, level.getMinBuildHeight(), -BORDER_SIZE,
            BORDER_SIZE, level.getMaxBuildHeight(), BORDER_SIZE);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, bounds)) {
            mob.discard();
        }
    }

    private void clearLoadedShowcaseBlocks(ServerLevel level) {
        int radius = BORDER_SIZE >> 4;
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                if (level.hasChunk(cx, cz)) {
                    ShowcaseBlockCleaner.cleanChunk(level, level.getChunk(cx, cz));
                }
            }
        }
    }

    private void clearChunkShowcaseBlocks(ServerLevel level, LevelChunk chunk) {
        ShowcaseBlockCleaner.cleanChunk(level, chunk);
    }

    private void clearTransientResetState() {
        RESET_STARTED.clear();
        RESET_COUNTDOWN.clear();
        RESET_RETRY_COUNTDOWN.clear();
        RESET_QUEUE.clear();
        DIRTY_PRODUCTION_DIMENSIONS.clear();
        STARTUP_RECOVERY_DIMENSIONS.clear();
        DEATH_SHOWCASE_ORIGINS.clear();
        LAST_KNOWN_SHOWCASE_DIMENSIONS.clear();
    }

    private void reconcileShowcasePlayerLocations(MinecraftServer server) {
        Map<UUID, ResourceLocation> currentDimensions = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceLocation dimensionId = player.level().dimension().location();
            if (ShowcaseDimensions.isShowcaseDimension(dimensionId)) {
                currentDimensions.put(player.getUUID(), dimensionId);
            }
        }

        for (Map.Entry<UUID, ResourceLocation> entry
                : List.copyOf(LAST_KNOWN_SHOWCASE_DIMENSIONS.entrySet())) {
            ResourceLocation currentDimension = currentDimensions.get(entry.getKey());
            if (!entry.getValue().equals(currentDimension)) {
                scheduleEmptyShowcaseReset(server, entry.getValue());
            }
        }

        LAST_KNOWN_SHOWCASE_DIMENSIONS.clear();
        LAST_KNOWN_SHOWCASE_DIMENSIONS.putAll(currentDimensions);
    }

    private void tickQueuedShowcaseResets(MinecraftServer server) {
        for (ResourceLocation dimensionId : List.copyOf(RESET_QUEUE)) {
            tickShowcaseReset(server, dimensionId);
        }
    }

    private void scheduleEmptyShowcaseReset(MinecraftServer server, ResourceLocation dimensionId) {
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)) return;
        RESET_QUEUE.add(dimensionId);
    }

    private void cancelResetForOccupiedDimension(ResourceLocation dimensionId) {
        RESET_STARTED.remove(dimensionId);
        RESET_COUNTDOWN.remove(dimensionId);
        RESET_RETRY_COUNTDOWN.remove(dimensionId);
        RESET_QUEUE.remove(dimensionId);
    }

    private void tickShowcaseReset(MinecraftServer server, ResourceLocation dimensionId) {
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)) return;

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            cancelResetForOccupiedDimension(dimensionId);
            return;
        }

        if (!level.players().isEmpty()) {
            cancelResetForOccupiedDimension(dimensionId);
            return;
        }

        int retryTicks = RESET_RETRY_COUNTDOWN.getOrDefault(dimensionId, 0);
        if (retryTicks > 0) {
            RESET_RETRY_COUNTDOWN.put(dimensionId, retryTicks - 1);
            return;
        }
        RESET_RETRY_COUNTDOWN.remove(dimensionId);

        if (!RESET_STARTED.getOrDefault(dimensionId, false)) {
            if (!beginReset(level, dimensionId)) {
                RESET_RETRY_COUNTDOWN.put(dimensionId, RESET_RETRY_DELAY_TICKS);
                return;
            }
        }

        int current = RESET_COUNTDOWN.getOrDefault(dimensionId, 0);
        if (current <= 0) return;
        RESET_COUNTDOWN.put(dimensionId, current - 1);
        if (current == 1) {
            RESET_COUNTDOWN.remove(dimensionId);
            if (deleteRegionCaches(level, dimensionId)) {
                RESET_STARTED.remove(dimensionId);
                RESET_RETRY_COUNTDOWN.remove(dimensionId);
                RESET_QUEUE.remove(dimensionId);
                clearProductionDimensionDirty(server, dimensionId);
            } else {
                RESET_STARTED.remove(dimensionId);
                RESET_RETRY_COUNTDOWN.put(dimensionId, RESET_RETRY_DELAY_TICKS);
            }
        }
    }

    private boolean beginReset(ServerLevel level, ResourceLocation dimensionId) {
        try {
            LOGGER.info("BloodPalace: no players in {}, resetting after {} ticks",
                dimensionId, RESET_DELAY_TICKS);

            IOWorker ioWorker = (IOWorker) level.getChunkSource().chunkScanner();
            level.noSave = false;
            level.save(null, true, true);
            ioWorker.storage.regionCache.clear();

            List<Entity> entities = new ArrayList<>();
            level.getAllEntities().forEach(entities::add);
            for (Entity entity : entities) {
                entity.discard();
            }

            RESET_STARTED.put(dimensionId, true);
            RESET_COUNTDOWN.put(dimensionId, RESET_DELAY_TICKS);
            return true;
        } catch (Exception e) {
            LOGGER.error("BloodPalace: failed to begin reset for {}", dimensionId, e);
            RESET_STARTED.remove(dimensionId);
            RESET_COUNTDOWN.remove(dimensionId);
            return false;
        }
    }

    private boolean deleteRegionCaches(ServerLevel level, ResourceLocation dimensionId) {
        try {
            IOWorker ioWorker = (IOWorker) level.getChunkSource().chunkScanner();
            Path regionFolder = ioWorker.storage.folder;
            ioWorker.storage.regionCache.clear();
            boolean hadCache = Files.exists(regionFolder)
                || Files.exists(regionFolder.getParent().resolve("entities"))
                || Files.exists(regionFolder.getParent().resolve("poi"));
            deleteFiles(regionFolder, dimensionId);
            deleteFiles(regionFolder.getParent().resolve("entities"), dimensionId);
            deleteFiles(regionFolder.getParent().resolve("poi"), dimensionId);
            if (!hadCache) {
                LOGGER.info("BloodPalace: no cached dimension files in {}, skipped delete", dimensionId);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("BloodPalace: failed to reset dimension {}", dimensionId, e);
            return false;
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation dimensionId = player.level().dimension().location();
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)) return;
        DEATH_SHOWCASE_ORIGINS.put(player.getUUID(), dimensionId);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation sourceDimension = DEATH_SHOWCASE_ORIGINS.remove(player.getUUID());
        if (sourceDimension == null) return;

        MinecraftServer server = player.getServer();
        if (server != null) {
            server.tell(new TickTask(server.getTickCount() + 1,
                () -> scheduleEmptyShowcaseReset(server, sourceDimension)));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ShowcaseTeleports.cancelPendingEnter(player);

        Set<ResourceLocation> departedDimensions = new HashSet<>();
        ResourceLocation deathOrigin = DEATH_SHOWCASE_ORIGINS.remove(player.getUUID());
        if (deathOrigin != null) {
            departedDimensions.add(deathOrigin);
        }

        ResourceLocation trackedDimension = LAST_KNOWN_SHOWCASE_DIMENSIONS.remove(player.getUUID());
        if (trackedDimension != null) {
            departedDimensions.add(trackedDimension);
        }

        ResourceLocation currentDimension = player.level().dimension().location();
        if (ShowcaseDimensions.isShowcaseDimension(currentDimension)) {
            departedDimensions.add(currentDimension);
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (ResourceLocation dimensionId : departedDimensions) {
            server.tell(new TickTask(server.getTickCount() + 1,
                () -> scheduleEmptyShowcaseReset(server, dimensionId)));
        }
    }

    private void evacuatePlayerForPendingReset(ServerPlayer player, ResourceLocation dimensionId) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (!dimensionId.equals(player.level().dimension().location())) return;
            if (!DIRTY_PRODUCTION_DIMENSIONS.contains(dimensionId)) return;

            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld,
                spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        }));
    }

    private boolean isProductionPrefabDimension(MinecraftServer server,
            ResourceLocation dimensionId) {
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)
                || ShowcaseDimensions.isLegacyShowcaseDimension(dimensionId)) {
            return false;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(key);
        return level != null
            && level.getChunkSource().getGenerator() instanceof PrefabChunkGenerator;
    }

    private void markProductionDimensionDirty(MinecraftServer server,
            ResourceLocation dimensionId) {
        if (!isProductionPrefabDimension(server, dimensionId)) return;
        if (DIRTY_PRODUCTION_DIMENSIONS.add(dimensionId)) {
            savePendingProductionResets(server);
        }
    }

    private void clearProductionDimensionDirty(MinecraftServer server,
            ResourceLocation dimensionId) {
        STARTUP_RECOVERY_DIMENSIONS.remove(dimensionId);
        if (DIRTY_PRODUCTION_DIMENSIONS.remove(dimensionId)) {
            savePendingProductionResets(server);
        }
    }

    private void loadPendingProductionResets(MinecraftServer server) {
        Path marker = pendingResetMarker(server);
        if (!Files.exists(marker)) return;
        try {
            for (String line : Files.readAllLines(marker)) {
                if (line.isBlank()) continue;
                ResourceLocation dimensionId = ResourceLocation.parse(line.trim());
                if (!isProductionPrefabDimension(server, dimensionId)) continue;
                DIRTY_PRODUCTION_DIMENSIONS.add(dimensionId);
                STARTUP_RECOVERY_DIMENSIONS.add(dimensionId);
                RESET_QUEUE.add(dimensionId);
            }
        } catch (Exception e) {
            LOGGER.error(PENDING_RESET_MARKER, e);
        }
    }

    private void savePendingProductionResets(MinecraftServer server) {
        Path marker = pendingResetMarker(server);
        try {
            Files.createDirectories(marker.getParent());
            if (DIRTY_PRODUCTION_DIMENSIONS.isEmpty()) {
                Files.deleteIfExists(marker);
                return;
            }

            List<String> lines = DIRTY_PRODUCTION_DIMENSIONS.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
            Path temporary = marker.resolveSibling(marker.getFileName() + TEMP_SUFFIX);
            Files.write(temporary, lines);
            try {
                Files.move(temporary, marker,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error(PENDING_RESET_MARKER, e);
        }
    }

    private Path pendingResetMarker(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
            .resolve(STATE_FOLDER)
            .resolve(PENDING_RESET_MARKER);
    }

    private void invalidateChangedPrefabCaches(MinecraftServer server) {
        String expected = PrefabRepository.get().diskCacheFingerprint();
        Path markerDir = server.getWorldPath(LevelResource.ROOT).resolve("bloodpalace");
        Path marker = markerDir.resolve(PREFAB_CACHE_MARKER);
        try {
            if (Files.exists(marker) && expected.equals(Files.readString(marker).trim())) return;

            LOGGER.info("BloodPalace: prefab resources changed, invalidating production dimension caches");
            boolean success = true;
            for (String structureName : ShowcaseDimensions.STRUCTURES) {
                ResourceKey<Level> key = ShowcaseDimensions.dimensionKeyForStructure(structureName);
                ServerLevel level = server.getLevel(key);
                if (level == null
                        || !(level.getChunkSource().getGenerator() instanceof PrefabChunkGenerator)) {
                    continue;
                }
                success &= deleteRegionCaches(level, level.dimension().location());
            }
            if (!success) {
                LOGGER.error("BloodPalace: prefab cache invalidation was incomplete; marker not updated");
                return;
            }

            Files.createDirectories(markerDir);
            Path temporary = markerDir.resolve(PREFAB_CACHE_MARKER + ".tmp");
            Files.writeString(temporary, expected);
            Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("BloodPalace: production prefab caches are ready for fingerprint {}", expected);
        } catch (IOException e) {
            LOGGER.error("BloodPalace: failed to update prefab cache fingerprint", e);
        }
    }

    private void deleteFiles(Path folder, ResourceLocation dimensionId) throws IOException {
        if (!Files.exists(folder)) return;
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.deleteIfExists(file)) {
                    LOGGER.info("BloodPalace: {} cache deleted -> {}", dimensionId, file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static boolean isResetting(ResourceLocation dimensionId) {
        return RESET_QUEUE.contains(dimensionId);
    }

    public static int resetTicksRemaining(ResourceLocation dimensionId) {
        int countdown = RESET_COUNTDOWN.getOrDefault(dimensionId, 0);
        int retry = RESET_RETRY_COUNTDOWN.getOrDefault(dimensionId, 0);
        return Math.max(1, Math.max(countdown, retry));
    }

}
