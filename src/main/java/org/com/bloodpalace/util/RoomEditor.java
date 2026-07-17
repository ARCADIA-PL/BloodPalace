package org.com.bloodpalace.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.com.bloodpalace.config.RoomConfig;
import org.com.bloodpalace.entity.RoomCoreEntity;
import org.com.bloodpalace.network.BloodPalaceNetwork;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RoomEditor {

    private static final int MIN_LENGTH_X = 10;
    private static final int MIN_WIDTH_Z = 10;
    private static final int MIN_HEIGHT_Y = 5;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private RoomEditor() {
    }

    public static int createCore(ServerPlayer player, BlockPos center) {
        if (!isInShowcase(player)) return notInShowcase(player);
        String roomId = nextRoomId(player);
        RoomConfig.Room room = defaultRoom(roomId, center);
        if (!saveRoom(player, room)) return 0;
        Session session = Session.from(room);
        beginSession(player, session);
        RoomCoreManager.upsert(player.serverLevel(), room, false);
        syncScreen(player, session);
        player.sendSystemMessage(Component.literal("\u00a7aCreated room core \u00a76" + roomId));
        return 1;
    }

    public static int edit(ServerPlayer player, String roomId) {
        if (!isInShowcase(player)) return notInShowcase(player);

        RoomConfig.Room room = RoomConfig.get(currentDimensionId(player), roomId)
            .orElseGet(() -> defaultRoom(roomId, player.blockPosition()));
        Session session = Session.from(room);
        beginSession(player, session);
        syncEditingCore(player, session);
        syncScreen(player, session);
        player.sendSystemMessage(Component.literal("\u00a7aEditing room core \u00a76" + roomId));
        return 1;
    }

    public static int openCoreEditor(ServerPlayer player, RoomCoreEntity core) {
        if (!isInShowcase(player)) return notInShowcase(player);
        if (!player.isCreative()) {
            player.sendSystemMessage(Component.literal("\u00a7cOnly creative players can edit room cores."));
            return 0;
        }

        Session session = Session.from(core.toRoom());
        beginSession(player, session);
        syncEditingCore(player, session);
        syncScreen(player, session);
        return 1;
    }

    public static int move(ServerPlayer player, int x, int y, int z) {
        Session session = completeSession(player);
        if (session == null) return 0;
        session.pos1 = session.pos1.offset(x, y, z);
        session.pos2 = session.pos2.offset(x, y, z);
        syncEditingCore(player, session);
        syncScreen(player, session);
        return 1;
    }

    public static int scale(ServerPlayer player, int x, int y, int z) {
        Session session = completeSession(player);
        if (session == null) return 0;
        Bounds bounds = Bounds.from(session.pos1, session.pos2);
        Bounds scaled = bounds.scale(x, y, z);
        if (!scaled.meetsMinimum()) {
            player.sendSystemMessage(Component.literal("\u00a7cMinimum room size is X="
                + MIN_LENGTH_X + ", Z=" + MIN_WIDTH_Z + ", Y=" + MIN_HEIGHT_Y + "."));
            return 0;
        }
        session.pos1 = new BlockPos(scaled.minX, scaled.minY, scaled.minZ);
        session.pos2 = new BlockPos(scaled.maxX, scaled.maxY, scaled.maxZ);
        syncEditingCore(player, session);
        syncScreen(player, session);
        return 1;
    }

    public static int rename(ServerPlayer player, String name) {
        Session session = session(player);
        if (session == null) return 0;
        String cleanName = name.trim();
        if (cleanName.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a7cRoom name cannot be empty."));
            return 0;
        }
        session.roomName = cleanName;
        if (session.isComplete()) {
            syncEditingCore(player, session);
            syncScreen(player, session);
        }
        return 1;
    }

    public static int save(ServerPlayer player) {
        Session session = completeSession(player);
        if (session == null) return 0;
        Bounds bounds = Bounds.from(session.pos1, session.pos2);
        if (!bounds.meetsMinimum()) {
            player.sendSystemMessage(Component.literal("\u00a7cMinimum room size is X="
                + MIN_LENGTH_X + ", Z=" + MIN_WIDTH_Z + ", Y=" + MIN_HEIGHT_Y + "."));
            return 0;
        }

        RoomConfig.Room room = session.toRoom();
        if (!saveRoom(player, room)) return 0;

        RoomCoreManager.upsert(player.serverLevel(), room, false);
        SESSIONS.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("\u00a7aSaved room \u00a76" + room.id));
        return 1;
    }

    public static int cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && isInShowcase(player)) {
            restorePersistedCore(player, session);
        }
        player.sendSystemMessage(Component.literal("\u00a7aRoom editing cancelled."));
        return 1;
    }

    public static int list(ServerPlayer player) {
        if (!isInShowcase(player)) return notInShowcase(player);
        RoomCoreManager.ensureForDimension(player.serverLevel());
        List<RoomConfig.Room> rooms = RoomConfig.list(currentDimensionId(player));
        if (rooms.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a77No rooms configured in this dimension."));
            return 1;
        }

        StringBuilder sb = new StringBuilder("\u00a76Rooms:\n");
        for (RoomConfig.Room room : rooms) {
            sb.append("\u00a77- \u00a7f").append(room.id)
                .append(" \u00a78")
                .append(format(room.min)).append(" -> ").append(format(room.max))
                .append("\n");
        }
        player.sendSystemMessage(Component.literal(sb.toString()));
        return 1;
    }

    public static int show(ServerPlayer player, String roomId) {
        if (!isInShowcase(player)) return notInShowcase(player);
        Optional<RoomConfig.Room> room = RoomConfig.get(currentDimensionId(player), roomId);
        if (room.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a7cUnknown room: " + roomId));
            return 0;
        }
        RoomCoreManager.upsert(player.serverLevel(), room.get(), false);
        player.sendSystemMessage(Component.literal(
            "\u00a7aShowing room core \u00a76" + roomId));
        return 1;
    }

    public static int showAll(ServerPlayer player) {
        if (!isInShowcase(player)) return notInShowcase(player);
        RoomCoreManager.ensureForDimension(player.serverLevel());
        player.sendSystemMessage(Component.literal("\u00a7aRoom cores are visible in creative mode."));
        return 1;
    }

    public static int delete(ServerPlayer player, String roomId) {
        if (!isInShowcase(player)) return notInShowcase(player);
        try {
            if (!RoomConfig.delete(currentDimensionId(player), roomId)) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown room: " + roomId));
                return 0;
            }
        } catch (IOException e) {
            player.sendSystemMessage(Component.literal("\u00a7cFailed to save room config: " + RoomConfig.getPath()));
            return 0;
        }
        SESSIONS.values().removeIf(session -> session.roomId.equals(roomId));
        RoomCoreManager.remove(player.serverLevel(), roomId);
        player.sendSystemMessage(Component.literal("\u00a7aDeleted room \u00a76" + roomId));
        return 1;
    }

    private static void syncEditingCore(ServerPlayer player, Session session) {
        if (!session.isComplete()) return;
        RoomCoreManager.upsert(player.serverLevel(), session.toRoom(), true);
    }

    private static void beginSession(ServerPlayer player, Session session) {
        Session previous = SESSIONS.put(player.getUUID(), session);
        if (previous != null && !previous.roomId.equals(session.roomId) && isInShowcase(player)) {
            restorePersistedCore(player, previous);
        }
    }

    private static void syncScreen(ServerPlayer player, Session session) {
        if (session.isComplete()) {
            BloodPalaceNetwork.openRoomEditor(player, session.toRoom());
        }
    }

    private static void restorePersistedCore(ServerPlayer player, Session session) {
        Optional<RoomConfig.Room> saved = RoomConfig.get(currentDimensionId(player), session.roomId);
        if (saved.isPresent()) {
            RoomCoreManager.upsert(player.serverLevel(), saved.get(), false);
        } else {
            RoomCoreManager.remove(player.serverLevel(), session.roomId);
        }
    }

    private static boolean saveRoom(ServerPlayer player, RoomConfig.Room room) {
        try {
            RoomConfig.set(currentDimensionId(player), room);
            return true;
        } catch (IOException e) {
            player.sendSystemMessage(Component.literal("\u00a7cFailed to save room config: " + RoomConfig.getPath()));
            return false;
        }
    }

    private static Session session(ServerPlayer player) {
        if (!isInShowcase(player)) {
            notInShowcase(player);
            return null;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal("\u00a7cUse /bloodpalace room edit <id> first."));
        }
        return session;
    }

    private static Session completeSession(ServerPlayer player) {
        Session session = session(player);
        if (session == null) return null;
        if (!session.isComplete()) {
            player.sendSystemMessage(Component.literal("\u00a7cCreate or open a room core first."));
            return null;
        }
        return session;
    }

    private static boolean isInShowcase(ServerPlayer player) {
        return ShowcaseDimensions.isShowcaseDimension(player.level().dimension().location());
    }

    private static int notInShowcase(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("\u00a7cStand in a BloodPalace showcase dimension."));
        return 0;
    }

    private static String currentDimensionId(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static String format(RoomConfig.Pos pos) {
        return pos.x + " " + pos.y + " " + pos.z;
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static RoomConfig.Room defaultRoom(String roomId, BlockPos center) {
        int minX = center.getX() - MIN_LENGTH_X / 2;
        int minY = center.getY() - MIN_HEIGHT_Y / 2;
        int minZ = center.getZ() - MIN_WIDTH_Z / 2;
        return new RoomConfig.Room(
            roomId,
            roomId,
            new RoomConfig.Pos(minX, minY, minZ),
            new RoomConfig.Pos(
                minX + MIN_LENGTH_X - 1,
                minY + MIN_HEIGHT_Y - 1,
                minZ + MIN_WIDTH_Z - 1));
    }

    private static String nextRoomId(ServerPlayer player) {
        int next = 1;
        for (RoomConfig.Room room : RoomConfig.list(currentDimensionId(player))) {
            String id = room.id;
            if (!id.startsWith("room_")) continue;
            try {
                next = Math.max(next, Integer.parseInt(id.substring("room_".length())) + 1);
            } catch (NumberFormatException ignored) {
            }
        }
        while (RoomCoreManager.find(player.serverLevel(), "room_" + next) != null) {
            next++;
        }
        return "room_" + next;
    }

    private static final class Session {
        private final String roomId;
        private String roomName;
        private BlockPos pos1;
        private BlockPos pos2;

        private Session(String roomId, String roomName, BlockPos pos1, BlockPos pos2) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        private static Session from(RoomConfig.Room room) {
            return new Session(
                room.id,
                room.name == null || room.name.isBlank() ? room.id : room.name,
                new BlockPos(room.min.x, room.min.y, room.min.z),
                new BlockPos(room.max.x, room.max.y, room.max.z));
        }

        private boolean isComplete() {
            return pos1 != null && pos2 != null;
        }

        private RoomConfig.Room toRoom() {
            Bounds bounds = Bounds.from(pos1, pos2);
            return new RoomConfig.Room(
                roomId,
                roomName,
                new RoomConfig.Pos(bounds.minX, bounds.minY, bounds.minZ),
                new RoomConfig.Pos(bounds.maxX, bounds.maxY, bounds.maxZ));
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds from(BlockPos a, BlockPos b) {
            return new Bounds(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        }

        private Bounds scale(int x, int y, int z) {
            return new Bounds(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
        }

        private boolean meetsMinimum() {
            return width() >= MIN_LENGTH_X && height() >= MIN_HEIGHT_Y && depth() >= MIN_WIDTH_Z;
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private int depth() {
            return maxZ - minZ + 1;
        }
    }
}
