package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.bloodpalace.util.RoomEditor;

import java.util.function.Supplier;

public record RoomEditorActionPacket(Action action, int x, int y, int z, String name) {

    public enum Action {
        MOVE,
        SCALE,
        RENAME,
        SAVE,
        CANCEL
    }

    public static RoomEditorActionPacket move(int x, int y, int z) {
        return new RoomEditorActionPacket(Action.MOVE, x, y, z, "");
    }

    public static RoomEditorActionPacket scale(int x, int y, int z) {
        return new RoomEditorActionPacket(Action.SCALE, x, y, z, "");
    }

    public static RoomEditorActionPacket rename(String name) {
        return new RoomEditorActionPacket(Action.RENAME, 0, 0, 0, name);
    }

    public static RoomEditorActionPacket save() {
        return new RoomEditorActionPacket(Action.SAVE, 0, 0, 0, "");
    }

    public static RoomEditorActionPacket cancel() {
        return new RoomEditorActionPacket(Action.CANCEL, 0, 0, 0, "");
    }

    public static void encode(RoomEditorActionPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeInt(packet.x);
        buf.writeInt(packet.y);
        buf.writeInt(packet.z);
        buf.writeUtf(packet.name);
    }

    public static RoomEditorActionPacket decode(FriendlyByteBuf buf) {
        return new RoomEditorActionPacket(
            buf.readEnum(Action.class),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readUtf());
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
                case SAVE -> RoomEditor.save(player);
                case CANCEL -> RoomEditor.cancel(player);
            }
        });
        context.setPacketHandled(true);
    }
}
