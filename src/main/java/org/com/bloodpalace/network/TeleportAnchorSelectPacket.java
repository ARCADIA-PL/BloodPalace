package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.bloodpalace.util.TeleportAnchorManager;

import java.util.UUID;
import java.util.function.Supplier;

public record TeleportAnchorSelectPacket(UUID anchorId) {

    public static void encode(TeleportAnchorSelectPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.anchorId);
    }

    public static TeleportAnchorSelectPacket decode(FriendlyByteBuf buf) {
        return new TeleportAnchorSelectPacket(buf.readUUID());
    }

    public static void handle(TeleportAnchorSelectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                TeleportAnchorManager.teleport(player, packet.anchorId);
            }
        });
        context.setPacketHandled(true);
    }
}
