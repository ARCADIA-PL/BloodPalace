package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.com.bloodpalace.client.ClientPacketHandlers;

import java.util.function.Supplier;

public record RoomOverlayPacket(boolean entered, String roomId, String roomName) {

    public static void encode(RoomOverlayPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.entered);
        buf.writeUtf(packet.roomId);
        buf.writeUtf(packet.roomName);
    }

    public static RoomOverlayPacket decode(FriendlyByteBuf buf) {
        return new RoomOverlayPacket(buf.readBoolean(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(RoomOverlayPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.showRoomOverlay(packet)));
        context.setPacketHandled(true);
    }
}
