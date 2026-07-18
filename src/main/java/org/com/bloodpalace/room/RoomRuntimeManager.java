package org.com.bloodpalace.room;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.com.bloodpalace.config.RoomConfig;
import org.com.bloodpalace.entity.RoomCoreEntity;
import org.com.bloodpalace.util.ShowcaseDimensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoomRuntimeManager {

    private static final Map<RoomKey, RoomState> STATES = new HashMap<>();

    private RoomRuntimeManager() {
    }

    public static void tick(ServerLevel level) {
        ResourceLocation dimensionId = level.dimension().location();
        if (!ShowcaseDimensions.isShowcaseDimension(dimensionId)) {
            clearDimension(dimensionId);
            return;
        }

        List<RoomConfig.Room> rooms = RoomConfig.list(dimensionId.toString());
        Set<RoomKey> activeKeys = new HashSet<>();
        for (RoomConfig.Room room : rooms) {
            RoomKey key = new RoomKey(dimensionId.toString(), room.id);
            activeKeys.add(key);
            tickRoom(level, key, room);
        }
        STATES.keySet().removeIf(key -> key.dimensionId.equals(dimensionId.toString()) && !activeKeys.contains(key));
    }

    public static Optional<Snapshot> snapshot(String dimensionId, String roomId) {
        RoomState state = STATES.get(new RoomKey(dimensionId, roomId));
        if (state == null) return Optional.empty();
        return Optional.of(state.snapshot());
    }

    public static List<Snapshot> snapshots(String dimensionId) {
        List<Snapshot> snapshots = new ArrayList<>();
        STATES.forEach((key, state) -> {
            if (key.dimensionId.equals(dimensionId)) {
                snapshots.add(state.snapshot());
            }
        });
        return List.copyOf(snapshots);
    }

    public static void clearDimension(ResourceLocation dimensionId) {
        STATES.keySet().removeIf(key -> key.dimensionId.equals(dimensionId.toString()));
    }

    private static void tickRoom(ServerLevel level, RoomKey key, RoomConfig.Room room) {
        RoomState state = STATES.computeIfAbsent(key, ignored -> new RoomState(key, room.name));
        state.roomName = cleanName(room);

        AABB bounds = bounds(room);
        List<ServerPlayer> players = level.players().stream()
            .filter(player -> !player.isRemoved())
            .filter(player -> contains(bounds, player))
            .toList();
        List<Entity> entities = level.getEntities((Entity) null, bounds, entity ->
            !entity.isRemoved()
                && !(entity instanceof RoomCoreEntity)
                && !(entity instanceof ServerPlayer)
                && contains(bounds, entity));

        Set<UUID> currentPlayers = new HashSet<>();
        for (ServerPlayer player : players) {
            currentPlayers.add(player.getUUID());
            if (!state.playerIds.contains(player.getUUID())) {
                showRoomOverlay(player, true, state.roomName);
            }
        }

        for (UUID previousPlayerId : Set.copyOf(state.playerIds)) {
            if (currentPlayers.contains(previousPlayerId)) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(previousPlayerId);
            if (player != null) {
                showRoomOverlay(player, false, state.roomName);
            }
        }

        state.playerIds.clear();
        state.playerIds.addAll(currentPlayers);
        state.entityIds.clear();
        for (Entity entity : entities) {
            state.entityIds.add(entity.getUUID());
        }
    }

    private static boolean contains(AABB bounds, Entity entity) {
        return bounds.contains(entity.position());
    }

    private static AABB bounds(RoomConfig.Room room) {
        return new AABB(
            room.min.x,
            room.min.y,
            room.min.z,
            room.max.x + 1.0D,
            room.max.y + 1.0D,
            room.max.z + 1.0D);
    }

    private static String cleanName(RoomConfig.Room room) {
        return room.name == null || room.name.isBlank() ? room.id : room.name;
    }

    private static void showRoomOverlay(ServerPlayer player, boolean entered, String roomName) {
        String prefix = entered ? "Entered Room" : "Left Room";
        int color = entered ? 0xE3B34B : 0xC7B8AF;
        Component message = Component.literal(prefix + ": " + roomName).withStyle(style -> style.withColor(color));
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    private record RoomKey(String dimensionId, String roomId) {
    }

    private static final class RoomState {
        private final RoomKey key;
        private String roomName;
        private final Set<UUID> playerIds = new HashSet<>();
        private final Set<UUID> entityIds = new HashSet<>();

        private RoomState(RoomKey key, String roomName) {
            this.key = key;
            this.roomName = roomName;
        }

        private Snapshot snapshot() {
            return new Snapshot(
                key.dimensionId,
                key.roomId,
                roomName,
                Set.copyOf(playerIds),
                Set.copyOf(entityIds));
        }
    }

    public record Snapshot(String dimensionId, String roomId, String roomName,
            Set<UUID> playerIds, Set<UUID> entityIds) {
    }
}
