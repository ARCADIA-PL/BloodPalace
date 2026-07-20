package org.com.bloodpalace.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.world.level.Level;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChunkyPreloadCoordinator {

    private static final int TASK_RADIUS = 200;
    private static final int TIMEOUT_TICKS = 20 * 60 * 60;
    private static final Map<TaskKey, List<PendingEntry>> PENDING_BY_TASK = new HashMap<>();
    private static final Set<ChunkyAPI> REGISTERED_APIS =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private ChunkyPreloadCoordinator() {
    }

    public static boolean await(MinecraftServer server, UUID playerId,
            ResourceKey<Level> dimension, String structureName, String token) {
        ChunkyAPI api = ChunkyProvider.get().getApi();
        ensureListener(api);

        String worldId = dimension.location().toString();
        TaskKey taskKey = new TaskKey(api, worldId);
        PendingEntry entry = new PendingEntry(server, playerId, dimension,
            structureName, token, server.getTickCount() + TIMEOUT_TICKS, api);
        synchronized (ChunkyPreloadCoordinator.class) {
            removePlayerEntries(playerId);
            PENDING_BY_TASK.computeIfAbsent(taskKey, ignored -> new ArrayList<>()).add(entry);
        }

        if (api.isRunning(worldId)) return true;
        synchronized (ChunkyPreloadCoordinator.class) {
            if (!contains(entry)) return true;
        }
        if (api.startTask(worldId, "square", 0, 0,
                TASK_RADIUS, TASK_RADIUS, "concentric")) {
            return true;
        }
        if (api.isRunning(worldId)) return true;

        cancel(playerId);
        return false;
    }

    public static synchronized void cancel(UUID playerId) {
        removePlayerEntries(playerId);
    }

    public static synchronized void clear(MinecraftServer server) {
        PENDING_BY_TASK.values().forEach(entries ->
            entries.removeIf(entry -> entry.server == server));
        PENDING_BY_TASK.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static void tick(MinecraftServer server) {
        List<PendingEntry> expired = new ArrayList<>();
        synchronized (ChunkyPreloadCoordinator.class) {
            for (List<PendingEntry> entries : PENDING_BY_TASK.values()) {
                entries.removeIf(entry -> {
                    if (entry.server != server || entry.deadlineTick > server.getTickCount()) {
                        return false;
                    }
                    expired.add(entry);
                    return true;
                });
            }
            PENDING_BY_TASK.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        expired.forEach(ShowcaseTeleports::expireChunkyPreload);
    }

    private static synchronized void ensureListener(ChunkyAPI api) {
        if (!REGISTERED_APIS.add(api)) return;
        api.onGenerationComplete(event -> complete(api, event.world()));
    }

    private static void complete(ChunkyAPI api, String worldId) {
        List<PendingEntry> completed;
        synchronized (ChunkyPreloadCoordinator.class) {
            completed = PENDING_BY_TASK.remove(new TaskKey(api, worldId));
        }
        if (completed == null) return;
        for (PendingEntry entry : completed) {
            entry.server.tell(new TickTask(entry.server.getTickCount(), () ->
                ShowcaseTeleports.completeChunkyPreload(entry)));
        }
    }

    private static boolean contains(PendingEntry expected) {
        TaskKey taskKey = new TaskKey(expected.api,
            expected.dimension.location().toString());
        List<PendingEntry> entries = PENDING_BY_TASK.get(taskKey);
        return entries != null && entries.contains(expected);
    }

    private static void removePlayerEntries(UUID playerId) {
        PENDING_BY_TASK.values().forEach(entries ->
            entries.removeIf(entry -> entry.playerId.equals(playerId)));
        PENDING_BY_TASK.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    record PendingEntry(MinecraftServer server, UUID playerId,
            ResourceKey<Level> dimension, String structureName, String token,
            int deadlineTick, ChunkyAPI api) {
    }

    private record TaskKey(ChunkyAPI api, String worldId) {
    }
}
