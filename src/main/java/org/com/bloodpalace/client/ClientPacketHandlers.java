package org.com.bloodpalace.client;

import net.minecraft.client.Minecraft;
import org.com.bloodpalace.network.RoomEditorState;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void openRoomEditor(RoomEditorState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RoomEditorScreen screen && screen.isEditing(state.roomId())) {
            screen.updateState(state);
            return;
        }
        minecraft.setScreen(new RoomEditorScreen(state));
    }
}
