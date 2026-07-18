package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.com.bloodpalace.client.ClientPacketHandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenTeleportAnchorScreenPacket(List<TeleportAnchorInfo> anchors) {

    public static void encode(OpenTeleportAnchorScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.anchors.size());
        for (TeleportAnchorInfo anchor : packet.anchors) {
            anchor.encode(buf);
        }
    }

    public static OpenTeleportAnchorScreenPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<TeleportAnchorInfo> anchors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            anchors.add(TeleportAnchorInfo.decode(buf));
        }
        return new OpenTeleportAnchorScreenPacket(anchors);
    }

    public static void handle(OpenTeleportAnchorScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.openTeleportAnchors(packet.anchors)));
        context.setPacketHandled(true);
    }
}
