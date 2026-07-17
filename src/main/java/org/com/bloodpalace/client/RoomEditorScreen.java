package org.com.bloodpalace.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.network.RoomEditorActionPacket;
import org.com.bloodpalace.network.RoomEditorState;

import java.util.Locale;

public class RoomEditorScreen extends Screen {

    private static final int PANEL_WIDTH = 388;
    private static final int PANEL_HEIGHT = 258;
    private static final int BUTTON_HEIGHT = 20;
    private static final int AXIS_BUTTON_WIDTH = 44;
    private static final int STEP_MIN = 1;
    private static final int STEP_MAX = 64;

    private RoomEditorState state;
    private EditBox nameEdit;
    private EditBox stepEdit;
    private Button applyNameButton;

    public RoomEditorScreen(RoomEditorState state) {
        super(Component.literal("Room Core Editor"));
        this.state = state;
        RoomHighlightRenderer.setEditingState(state);
    }

    @Override
    protected void init() {
        int left = left();
        int top = top();

        nameEdit = new EditBox(font, left + 84, top + 94, 198, BUTTON_HEIGHT, Component.literal("Name"));
        nameEdit.setMaxLength(64);
        nameEdit.setValue(state.name());
        addRenderableWidget(nameEdit);

        applyNameButton = addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyName())
            .bounds(left + 290, top + 94, 70, BUTTON_HEIGHT)
            .build());

        stepEdit = new EditBox(font, left + 84, top + 126, 46, BUTTON_HEIGHT, Component.literal("Step"));
        stepEdit.setMaxLength(3);
        stepEdit.setValue("1");
        addRenderableWidget(stepEdit);

        addRenderableWidget(Button.builder(Component.literal("-"), button -> setStep(step() - 1))
            .bounds(left + 52, top + 126, 24, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setStep(step() + 1))
            .bounds(left + 138, top + 126, 24, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("1"), button -> setStep(1))
            .bounds(left + 188, top + 126, 32, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("5"), button -> setStep(5))
            .bounds(left + 226, top + 126, 32, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("10"), button -> setStep(10))
            .bounds(left + 264, top + 126, 38, BUTTON_HEIGHT)
            .build());

        addAxisButtons(left + 84, top + 166, true);
        addAxisButtons(left + 84, top + 198, false);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            applyNameIfChanged();
            BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.save());
            RoomHighlightRenderer.clearEditingState();
            onClose();
        })
            .bounds(left + 128, top + 230, 74, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.cancel());
            RoomHighlightRenderer.clearEditingState();
            onClose();
        })
            .bounds(left + 208, top + 230, 74, BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
            .bounds(left + 288, top + 230, 74, BUTTON_HEIGHT)
            .build());

        updateApplyButton();
    }

    public boolean isEditing(String roomId) {
        return state.roomId().equals(roomId);
    }

    public void updateState(RoomEditorState state) {
        this.state = state;
        RoomHighlightRenderer.setEditingState(state);
        if (nameEdit != null && !nameEdit.isFocused()) {
            nameEdit.setValue(state.name());
        }
        updateApplyButton();
    }

    @Override
    public void tick() {
        if (nameEdit != null) nameEdit.tick();
        if (stepEdit != null) stepEdit.tick();
        updateApplyButton();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameEdit != null && nameEdit.isFocused()
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            applyName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInsidePanel(mouseX, mouseY)) {
            setStep(step() + (delta > 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();

        drawPanel(graphics, left, top);
        drawHeader(graphics, left, top);
        drawMetrics(graphics, left, top);
        drawSections(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addAxisButtons(int x, int y, boolean move) {
        addRenderableWidget(axisButton("X-", x, y, move, -1, 0, 0));
        addRenderableWidget(axisButton("X+", x + 48, y, move, 1, 0, 0));
        addRenderableWidget(axisButton("Y-", x + 104, y, move, 0, -1, 0));
        addRenderableWidget(axisButton("Y+", x + 152, y, move, 0, 1, 0));
        addRenderableWidget(axisButton("Z-", x + 208, y, move, 0, 0, -1));
        addRenderableWidget(axisButton("Z+", x + 256, y, move, 0, 0, 1));
    }

    private Button axisButton(String label, int x, int y, boolean move, int dx, int dy, int dz) {
        return Button.builder(Component.literal(label), button -> {
            int currentStep = step();
            RoomEditorActionPacket packet = move
                ? RoomEditorActionPacket.move(dx * currentStep, dy * currentStep, dz * currentStep)
                : RoomEditorActionPacket.scale(dx * currentStep, dy * currentStep, dz * currentStep);
            BloodPalaceNetwork.sendToServer(packet);
        })
            .bounds(x, y, AXIS_BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
    }

    private void drawPanel(GuiGraphics graphics, int left, int top) {
        graphics.fill(left - 8, top - 8, left + PANEL_WIDTH + 8, top + PANEL_HEIGHT + 8, 0xAA000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE1111418);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 28, 0xF01A2228);
        graphics.fill(left, top + 28, left + PANEL_WIDTH, top + 29, 0xFF47D8FF);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF2D5560);

        drawCard(graphics, left + 14, top + 38, PANEL_WIDTH - 28, 42);
        drawCard(graphics, left + 14, top + 86, PANEL_WIDTH - 28, 30);
        drawCard(graphics, left + 14, top + 120, PANEL_WIDTH - 28, 30);
        drawCard(graphics, left + 14, top + 154, PANEL_WIDTH - 28, 30);
        drawCard(graphics, left + 14, top + 186, PANEL_WIDTH - 28, 30);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xD0171B20);
        graphics.renderOutline(x, y, width, height, 0x663E6A74);
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, title, left + 14, top + 10, 0xFFFFFF, false);
        String id = state.roomId();
        int idWidth = font.width(id) + 16;
        int idLeft = left + PANEL_WIDTH - idWidth - 14;
        graphics.fill(idLeft, top + 7, idLeft + idWidth, top + 21, 0x6620A8C8);
        graphics.drawString(font, id, idLeft + 8, top + 10, 0xA8EFFF, false);
    }

    private void drawMetrics(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, "Size", left + 28, top + 46, 0x87AAB2, false);
        graphics.drawString(font, "X " + state.width() + "   Y " + state.height() + "   Z " + state.depth(),
            left + 86, top + 46, 0xE8F8FF, false);
        graphics.drawString(font, "Center", left + 28, top + 64, 0x87AAB2, false);
        graphics.drawString(font, centerText(), left + 86, top + 64, 0xBFE7F0, false);
    }

    private void drawSections(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, "Name", left + 28, top + 100, 0xD0D8DC, false);
        graphics.drawString(font, "Step", left + 28, top + 132, 0xD0D8DC, false);
        graphics.drawString(font, "Move", left + 28, top + 172, 0xD0D8DC, false);
        graphics.drawString(font, "Resize", left + 28, top + 204, 0xD0D8DC, false);
        graphics.drawString(font, "Bounds: " + boundsText(), left + 28, top + 220, 0x778D94, false);
    }

    private void applyName() {
        if (nameEdit == null) return;
        String name = nameEdit.getValue().trim();
        if (name.isEmpty()) return;
        BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.rename(name));
        updateApplyButton();
    }

    private void applyNameIfChanged() {
        if (nameEdit == null) return;
        String name = nameEdit.getValue().trim();
        if (!name.isEmpty() && !name.equals(state.name())) {
            BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.rename(name));
        }
    }

    private void updateApplyButton() {
        if (applyNameButton == null || nameEdit == null) return;
        String name = nameEdit.getValue().trim();
        applyNameButton.active = !name.isEmpty() && !name.equals(state.name());
    }

    private int step() {
        if (stepEdit == null) return STEP_MIN;
        try {
            int value = Integer.parseInt(stepEdit.getValue().trim());
            return clampStep(value);
        } catch (NumberFormatException e) {
            setStep(STEP_MIN);
            return STEP_MIN;
        }
    }

    private void setStep(int value) {
        if (stepEdit != null) {
            stepEdit.setValue(Integer.toString(clampStep(value)));
        }
    }

    private int clampStep(int value) {
        return Math.max(STEP_MIN, Math.min(STEP_MAX, value));
    }

    private String boundsText() {
        return state.minX() + " " + state.minY() + " " + state.minZ()
            + " -> " + state.maxX() + " " + state.maxY() + " " + state.maxZ();
    }

    private String centerText() {
        return decimal((state.minX() + state.maxX() + 1) / 2.0)
            + " / " + decimal((state.minY() + state.maxY() + 1) / 2.0)
            + " / " + decimal((state.minZ() + state.maxZ() + 1) / 2.0);
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        int left = left();
        int top = top();
        return mouseX >= left && mouseX <= left + PANEL_WIDTH
            && mouseY >= top && mouseY <= top + PANEL_HEIGHT;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(18, (height - PANEL_HEIGHT) / 2);
    }

    @Override
    public void onClose() {
        RoomHighlightRenderer.clearEditingState();
        Minecraft.getInstance().setScreen(null);
    }
}
