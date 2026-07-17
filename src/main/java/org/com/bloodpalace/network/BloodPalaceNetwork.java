package org.com.bloodpalace.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.com.bloodpalace.BloodPalace;
import org.com.bloodpalace.config.RoomConfig;

public final class BloodPalaceNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static int packetId;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(BloodPalace.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals);

    private BloodPalaceNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenRoomEditorScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenRoomEditorScreenPacket::encode)
            .decoder(OpenRoomEditorScreenPacket::decode)
            .consumerMainThread(OpenRoomEditorScreenPacket::handle)
            .add();

        CHANNEL.messageBuilder(RoomEditorActionPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RoomEditorActionPacket::encode)
            .decoder(RoomEditorActionPacket::decode)
            .consumerMainThread(RoomEditorActionPacket::handle)
            .add();
    }

    public static void openRoomEditor(ServerPlayer player, RoomConfig.Room room) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new OpenRoomEditorScreenPacket(RoomEditorState.from(room)));
    }

    public static void sendToServer(RoomEditorActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
