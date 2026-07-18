package org.com.bloodpalace.client;

import net.minecraft.client.Minecraft;
import org.com.bloodpalace.network.RoomEditorState;
import org.com.bloodpalace.network.RoomOverlayPacket;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void openRoomEditor(RoomEditorState state) {
        Minecraft minecraft = Minecraft.getInstance();
        RoomHighlightRenderer.setEditingState(state);
        if (minecraft.screen instanceof RoomEditorScreen screen && screen.isEditing(state.roomId())) {
            screen.updateState(state);
            return;
        }
        minecraft.setScreen(new RoomEditorScreen(state));
    }

    public static void showRoomOverlay(RoomOverlayPacket packet) {
        RoomOverlayRenderer.push(packet);
    }
}
