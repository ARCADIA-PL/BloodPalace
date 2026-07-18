package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.bloodpalace.util.RoomEditor;

import java.util.function.Supplier;

public record RoomEditorUpdatePacket(RoomEditorState state) {

    public static void encode(RoomEditorUpdatePacket packet, FriendlyByteBuf buf) {
        packet.state.encode(buf);
    }

    public static RoomEditorUpdatePacket decode(FriendlyByteBuf buf) {
        return new RoomEditorUpdatePacket(RoomEditorState.decode(buf));
    }

    public static void handle(RoomEditorUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2) || !player.isCreative()) {
                player.sendSystemMessage(Component.literal("\u00a7cOnly creative operators can edit room cores."));
                return;
            }
            RoomEditor.update(player, packet.state);
        });
        context.setPacketHandled(true);
    }
}
