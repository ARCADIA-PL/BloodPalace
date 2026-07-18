package org.com.bloodpalace.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record TeleportAnchorInfo(UUID id, String name, double x, double y, double z) {

    public static TeleportAnchorInfo decode(FriendlyByteBuf buf) {
        return new TeleportAnchorInfo(
            buf.readUUID(),
            buf.readUtf(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }
}
