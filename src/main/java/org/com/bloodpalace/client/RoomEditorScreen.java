package org.com.bloodpalace.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.network.RoomEditorActionPacket;
import org.com.bloodpalace.network.RoomEditorState;

public class RoomEditorScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int BUTTON_WIDTH = 38;
    private static final int BUTTON_HEIGHT = 20;

    private RoomEditorState state;
    private EditBox nameEdit;
    private EditBox stepEdit;

    public RoomEditorScreen(RoomEditorState state) {
        super(Component.literal("Room Core Editor"));
        this.state = state;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(24, (height - 182) / 2);

        nameEdit = new EditBox(font, left + 66, top + 46, 150, 20, Component.literal("Name"));
        nameEdit.setMaxLength(64);
        nameEdit.setValue(state.name());
        addRenderableWidget(nameEdit);

        addRenderableWidget(Button.builder(Component.literal("Apply"), button ->
                BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.rename(nameEdit.getValue())))
            .bounds(left + 222, top + 46, 54, BUTTON_HEIGHT)
            .build());

        stepEdit = new EditBox(font, left + 66, top + 72, 42, 20, Component.literal("Step"));
        stepEdit.setMaxLength(3);
        stepEdit.setValue("1");
        addRenderableWidget(stepEdit);

        int moveY = top + 104;
        addAxisButtons(left + 66, moveY, true);

        int scaleY = top + 132;
        addAxisButtons(left + 66, scaleY, false);

        int actionY = top + 162;
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
                BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.save());
                onClose();
            })
            .bounds(left + 66, actionY, 66, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
                BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.cancel());
                onClose();
            })
            .bounds(left + 138, actionY, 66, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
            .bounds(left + 210, actionY, 66, BUTTON_HEIGHT)
            .build());
    }

    public boolean isEditing(String roomId) {
        return state.roomId().equals(roomId);
    }

    public void updateState(RoomEditorState state) {
        this.state = state;
        if (nameEdit != null && !nameEdit.isFocused()) {
            nameEdit.setValue(state.name());
        }
    }

    @Override
    public void tick() {
        if (nameEdit != null) nameEdit.tick();
        if (stepEdit != null) stepEdit.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(24, (height - 182) / 2);

        graphics.fill(left - 10, top - 14, left + PANEL_WIDTH + 10, top + 194, 0xCC101014);
        graphics.renderOutline(left - 10, top - 14, PANEL_WIDTH + 20, 208, 0xFF47D8FF);
        graphics.drawCenteredString(font, title, width / 2, top - 6, 0xFFFFFF);
        graphics.drawString(font, "ID: " + state.roomId(), left, top + 18, 0xD0D0D0, false);
        graphics.drawString(font, "Bounds: " + formatBounds(), left, top + 30, 0xA8DFFF, false);
        graphics.drawString(font, "Name", left, top + 52, 0xD0D0D0, false);
        graphics.drawString(font, "Step", left, top + 78, 0xD0D0D0, false);
        graphics.drawString(font, "Move", left, top + 110, 0xD0D0D0, false);
        graphics.drawString(font, "Scale", left, top + 138, 0xD0D0D0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addAxisButtons(int x, int y, boolean move) {
        addRenderableWidget(axisButton("X-", x, y, move, -1, 0, 0));
        addRenderableWidget(axisButton("X+", x + 42, y, move, 1, 0, 0));
        addRenderableWidget(axisButton("Y-", x + 88, y, move, 0, -1, 0));
        addRenderableWidget(axisButton("Y+", x + 130, y, move, 0, 1, 0));
        addRenderableWidget(axisButton("Z-", x + 176, y, move, 0, 0, -1));
        addRenderableWidget(axisButton("Z+", x + 218, y, move, 0, 0, 1));
    }

    private Button axisButton(String label, int x, int y, boolean move, int dx, int dy, int dz) {
        return Button.builder(Component.literal(label), button -> {
                int step = step();
                RoomEditorActionPacket packet = move
                    ? RoomEditorActionPacket.move(dx * step, dy * step, dz * step)
                    : RoomEditorActionPacket.scale(dx * step, dy * step, dz * step);
                BloodPalaceNetwork.sendToServer(packet);
            })
            .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
    }

    private int step() {
        try {
            int value = Integer.parseInt(stepEdit.getValue().trim());
            return Math.max(1, Math.min(64, value));
        } catch (NumberFormatException e) {
            stepEdit.setValue("1");
            return 1;
        }
    }

    private String formatBounds() {
        return state.minX() + " " + state.minY() + " " + state.minZ()
            + " -> " + state.maxX() + " " + state.maxY() + " " + state.maxZ()
            + " (" + state.width() + "x" + state.height() + "x" + state.depth() + ")";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
