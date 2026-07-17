package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import org.com.bloodpalace.config.RoomConfig;

public record RoomEditorState(
        String roomId,
        String name,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ) {

    public static RoomEditorState from(RoomConfig.Room room) {
        String name = room.name == null || room.name.isBlank() ? room.id : room.name;
        return new RoomEditorState(
            room.id,
            name,
            room.min.x,
            room.min.y,
            room.min.z,
            room.max.x,
            room.max.y,
            room.max.z);
    }

    public static RoomEditorState decode(FriendlyByteBuf buf) {
        return new RoomEditorState(
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomId);
        buf.writeUtf(name);
        buf.writeInt(minX);
        buf.writeInt(minY);
        buf.writeInt(minZ);
        buf.writeInt(maxX);
        buf.writeInt(maxY);
        buf.writeInt(maxZ);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }
}
