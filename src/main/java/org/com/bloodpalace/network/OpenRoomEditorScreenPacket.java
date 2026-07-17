package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.com.bloodpalace.client.ClientPacketHandlers;

import java.util.function.Supplier;

public record OpenRoomEditorScreenPacket(RoomEditorState state) {

    public static void encode(OpenRoomEditorScreenPacket packet, FriendlyByteBuf buf) {
        packet.state.encode(buf);
    }

    public static OpenRoomEditorScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenRoomEditorScreenPacket(RoomEditorState.decode(buf));
    }

    public static void handle(OpenRoomEditorScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.openRoomEditor(packet.state)));
        context.setPacketHandled(true);
    }
}
