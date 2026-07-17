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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.com.bloodpalace.util.ShowcaseBlockCleaner;
import org.com.bloodpalace.util.ShowcaseDimensions;
import org.com.bloodpalace.util.ShowcaseTeleports;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShowcaseHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BORDER_SIZE = 500;
    private static final int RESET_DELAY_TICKS = 200;

    private static final Map<ResourceLocation, Boolean> RESET_STARTED = new HashMap<>();
    private static final Map<ResourceLocation, Integer> RESET_COUNTDOWN = new HashMap<>();
    private static final Set<ResourceLocation> RESET_QUEUE = new HashSet<>();

    // Structure → preload chunk radius (max_distance_from_center / 16, rounded up)
    private static final Map<String, Integer> HEAVY_STRUCTURES = new LinkedHashMap<>();
    static {
        HEAVY_STRUCTURES.put("keep_kayra",        16);
        HEAVY_STRUCTURES.put("mechanical_nest",   16);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        /*preloadHeavyStructuresSync(event.getServer());*/
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation toDim = event.getTo().location();
        ResourceLocation fromDim = event.getFrom().location();

        if (ShowcaseDimensions.isShowcaseDimension(toDim)) {
            RESET_STARTED.put(toDim, false);
            RESET_COUNTDOWN.remove(toDim);
            RESET_QUEUE.remove(toDim);

            WorldBorder border = player.serverLevel().getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(BORDER_SIZE);
            if (!ShowcaseTeleports.consumeSpawnEventSkip(player, toDim)) {
                ShowcaseTeleports.teleportToConfiguredSpawn(player, toDim);
            }
            clearLoadedShowcaseMobs(player.serverLevel());
            clearLoadedShowcaseBlocks(player.serverLevel());
        }

        if (ShowcaseDimensions.isShowcaseDimension(fromDim)) {
            scheduleEmptyShowcaseReset(player.getServer(), fromDim);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        tickQueuedShowcaseResets(event.getServer());
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

    private void tickQueuedShowcaseResets(MinecraftServer server) {
        for (ResourceLocation dimensionId : List.copyOf(RESET_QUEUE)) {
            tickShowcaseReset(server, dimensionId);
        }
    }

    private void scheduleEmptyShowcaseReset(MinecraftServer server, ResourceLocation dimensionId) {
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)) return;
        RESET_QUEUE.add(dimensionId);
        tickShowcaseReset(server, dimensionId);
    }

    private void tickShowcaseReset(MinecraftServer server, ResourceLocation dimensionId) {
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)) return;

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(key);
        if (level == null) return;

        if (!level.players().isEmpty()) {
            RESET_STARTED.put(dimensionId, false);
            RESET_COUNTDOWN.remove(dimensionId);
            RESET_QUEUE.remove(dimensionId);
            return;
        }

        if (!RESET_STARTED.getOrDefault(dimensionId, false)) {
            beginReset(level, dimensionId);
        }

        int current = RESET_COUNTDOWN.getOrDefault(dimensionId, 0);
        if (current <= 0) return;
        RESET_COUNTDOWN.put(dimensionId, current - 1);
        if (current == 1) {
            deleteRegionCaches(level, dimensionId);
            RESET_COUNTDOWN.remove(dimensionId);
            RESET_QUEUE.remove(dimensionId);
        }
    }

    private void beginReset(ServerLevel level, ResourceLocation dimensionId) {
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
        } catch (Exception e) {
            LOGGER.error("BloodPalace: failed to begin reset for {}", dimensionId, e);
            RESET_STARTED.put(dimensionId, true);
        }
    }

    private void deleteRegionCaches(ServerLevel level, ResourceLocation dimensionId) {
        try {
            IOWorker ioWorker = (IOWorker) level.getChunkSource().chunkScanner();
            Path regionFolder = ioWorker.storage.folder;
            if (!Files.exists(regionFolder)) {
                LOGGER.info("BloodPalace: no region files in {}, skipped reset delete", dimensionId);
                return;
            }

            ioWorker.storage.regionCache.clear();
            deleteFiles(regionFolder, dimensionId);
            deleteFiles(regionFolder.getParent().resolve("entities"), dimensionId);
            deleteFiles(regionFolder.getParent().resolve("poi"), dimensionId);
        } catch (IOException e) {
            LOGGER.error("BloodPalace: failed to reset dimension {}", dimensionId, e);
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
        return RESET_COUNTDOWN.getOrDefault(dimensionId, 0) > 0;
    }

    public static int resetTicksRemaining(ResourceLocation dimensionId) {
        return RESET_COUNTDOWN.getOrDefault(dimensionId, 0);
    }

}
