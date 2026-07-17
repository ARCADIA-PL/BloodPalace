package org.com.bloodpalace.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.com.bloodpalace.config.RoomConfig;
import org.com.bloodpalace.entity.BloodPalaceEntityTypes;
import org.com.bloodpalace.entity.RoomCoreEntity;

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
        RoomCoreEntity core = find(level, room.id);
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
        RoomCoreEntity core = find(level, roomId);
        if (core != null) {
            core.discard();
        }
    }

    public static RoomCoreEntity find(ServerLevel level, String roomId) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RoomCoreEntity core && core.getRoomId().equals(roomId)) {
                return core;
            }
        }
        return null;
    }
}
