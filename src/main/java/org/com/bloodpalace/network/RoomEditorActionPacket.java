package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.bloodpalace.util.RoomEditor;

import java.util.function.Supplier;

public record RoomEditorActionPacket(Action action, int x, int y, int z, String name, RoomEditorState state) {

    public enum Action {
        MOVE,
        SCALE,
        RENAME,
        SAVE,
        CANCEL
    }

    public static RoomEditorActionPacket move(int x, int y, int z) {
        return new RoomEditorActionPacket(Action.MOVE, x, y, z, "", null);
    }

    public static RoomEditorActionPacket scale(int x, int y, int z) {
        return new RoomEditorActionPacket(Action.SCALE, x, y, z, "", null);
    }

    public static RoomEditorActionPacket rename(String name) {
        return new RoomEditorActionPacket(Action.RENAME, 0, 0, 0, name, null);
    }

    public static RoomEditorActionPacket save() {
        return new RoomEditorActionPacket(Action.SAVE, 0, 0, 0, "", null);
    }

    public static RoomEditorActionPacket save(RoomEditorState state) {
        return new RoomEditorActionPacket(Action.SAVE, 0, 0, 0, "", state);
    }

    public static RoomEditorActionPacket cancel() {
        return new RoomEditorActionPacket(Action.CANCEL, 0, 0, 0, "", null);
    }

    public static void encode(RoomEditorActionPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeInt(packet.x);
        buf.writeInt(packet.y);
        buf.writeInt(packet.z);
        buf.writeUtf(packet.name);
        buf.writeBoolean(packet.state != null);
        if (packet.state != null) {
            packet.state.encode(buf);
        }
    }

    public static RoomEditorActionPacket decode(FriendlyByteBuf buf) {
        RoomEditorState state = null;
        Action action = buf.readEnum(Action.class);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        String name = buf.readUtf();
        if (buf.readBoolean()) {
            state = RoomEditorState.decode(buf);
        }
        return new RoomEditorActionPacket(
            action,
            x,
            y,
            z,
            name,
            state);
    }

    public static void handle(RoomEditorActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2) || !player.isCreative()) {
                player.sendSystemMessage(Component.literal("\u00a7cOnly creative operators can edit room cores."));
                return;
            }

            switch (packet.action) {
                case MOVE -> RoomEditor.move(player, packet.x, packet.y, packet.z);
                case SCALE -> RoomEditor.scale(player, packet.x, packet.y, packet.z);
                case RENAME -> RoomEditor.rename(player, packet.name);
                case SAVE -> {
                    if (packet.state != null) {
                        RoomEditor.save(player, packet.state);
                    } else {
                        RoomEditor.save(player);
                    }
                }
                case CANCEL -> RoomEditor.cancel(player);
            }
        });
        context.setPacketHandled(true);
    }
}
