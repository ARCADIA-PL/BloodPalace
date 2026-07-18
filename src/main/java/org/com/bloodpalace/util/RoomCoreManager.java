package org.com.bloodpalace.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.com.bloodpalace.config.RoomConfig;
import org.com.bloodpalace.entity.BloodPalaceEntityTypes;
import org.com.bloodpalace.entity.RoomCoreEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RoomCoreManager {

    private RoomCoreManager() {
    }

    public static void ensureForDimension(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        if (!ShowcaseDimensions.isShowcaseDimension(level.dimension().location())) return;
        for (RoomConfig.Room room : RoomConfig.list(dimensionId)) {
            RoomCoreEntity existing = find(level, room.id);
            if (existing == null) {
                upsert(level, room, false);
            } else {
                existing.setRoom(room, false);
            }
        }
    }

    public static RoomCoreEntity upsert(ServerLevel level, RoomConfig.Room room, boolean temporary) {
        RoomCoreEntity core = findCanonical(level, room);
        if (core == null) {
            core = BloodPalaceEntityTypes.ROOM_CORE.get().create(level);
            if (core == null) return null;
            core.setRoom(room, temporary);
            level.addFreshEntity(core);
            return core;
        }
        core.setRoom(room, temporary);
        return core;
    }

    public static void remove(ServerLevel level, String roomId) {
        for (RoomCoreEntity core : findAll(level, roomId)) {
            core.discard();
        }
    }

    public static RoomCoreEntity find(ServerLevel level, String roomId) {
        List<RoomCoreEntity> cores = findAll(level, roomId);
        if (cores.isEmpty()) return null;
        RoomCoreEntity first = cores.get(0);
        for (int i = 1; i < cores.size(); i++) {
            cores.get(i).discard();
        }
        return first;
    }

    private static RoomCoreEntity findCanonical(ServerLevel level, RoomConfig.Room room) {
        List<RoomCoreEntity> cores = findAll(level, room.id);
        if (cores.isEmpty()) return null;

        double centerX = (room.min.x + room.max.x + 1.0D) / 2.0D;
        double centerY = (room.min.y + room.max.y + 1.0D) / 2.0D;
        double centerZ = (room.min.z + room.max.z + 1.0D) / 2.0D;
        cores.sort(Comparator.comparingDouble(core ->
            core.distanceToSqr(centerX, centerY, centerZ)));

        RoomCoreEntity canonical = cores.get(0);
        for (int i = 1; i < cores.size(); i++) {
            cores.get(i).discard();
        }
        return canonical;
    }

    private static List<RoomCoreEntity> findAll(ServerLevel level, String roomId) {
        List<RoomCoreEntity> cores = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RoomCoreEntity core && core.getRoomId().equals(roomId)) {
                cores.add(core);
            }
        }
        return cores;
    }
}
