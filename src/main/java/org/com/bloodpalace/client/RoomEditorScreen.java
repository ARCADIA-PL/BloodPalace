package org.com.bloodpalace.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.network.RoomEditorActionPacket;
import org.com.bloodpalace.network.RoomEditorState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RoomEditorScreen extends Screen {

    private static final int MIN_PANEL_WIDTH = 332;
    private static final int MAX_PANEL_WIDTH = 508;
    private static final int MAX_PANEL_HEIGHT = 316;
    private static final int BUTTON_HEIGHT = 22;
    private static final int STEP_MIN = 1;
    private static final int STEP_MAX = 64;

    private final List<Hotspot> hotspots = new ArrayList<>();

    private RoomEditorState state;
    private EditBox nameEdit;
    private EditBox stepEdit;
    private Hotspot hovered;

    public RoomEditorScreen(RoomEditorState state) {
        super(Component.literal("Room Core Editor"));
        this.state = state;
        RoomHighlightRenderer.setEditingState(state);
    }

    @Override
    protected void init() {
        Layout layout = layout();

        nameEdit = new EditBox(font, layout.nameX, layout.nameY + 6, layout.nameWidth, 16,
            Component.literal("Name"));
        nameEdit.setMaxLength(64);
        nameEdit.setBordered(false);
        nameEdit.setTextColor(0xF3FBFF);
        nameEdit.setTextColorUneditable(0x8A9AA0);
        nameEdit.setValue(state.name());
        addRenderableWidget(nameEdit);

        stepEdit = new EditBox(font, layout.stepInputX, layout.stepY + 6, layout.stepInputWidth, 16,
            Component.literal("Step"));
        stepEdit.setMaxLength(3);
        stepEdit.setBordered(false);
        stepEdit.setTextColor(0xF3FBFF);
        stepEdit.setValue("1");
        addRenderableWidget(stepEdit);
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
    }

    @Override
    public void tick() {
        if (nameEdit != null) nameEdit.tick();
        if (stepEdit != null) stepEdit.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameEdit != null && nameEdit.isFocused()
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            applyName();
            return true;
        }
        if (stepEdit != null && stepEdit.isFocused()
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            setStep(step());
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            rebuildHotspots(mouseX, mouseY);
            for (Hotspot hotspot : hotspots) {
                if (hotspot.enabled && hotspot.contains(mouseX, mouseY)) {
                    activate(hotspot.action);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        rebuildHotspots(mouseX, mouseY);

        Layout layout = layout();
        syncEditBoxes(layout);

        drawPanel(graphics, layout.left, layout.top, layout.panelWidth, layout.panelHeight);
        drawHeader(graphics, layout);
        drawMetrics(graphics, layout);
        drawFields(graphics, layout);
        drawHotspots(graphics);
        drawFooter(graphics, layout);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildHotspots(double mouseX, double mouseY) {
        hotspots.clear();
        Layout layout = layout();
        boolean canApplyName = nameEdit != null
            && !nameEdit.getValue().trim().isEmpty()
            && !nameEdit.getValue().trim().equals(state.name());

        hotspots.add(new Hotspot(Action.APPLY_NAME, "Apply", layout.applyX, layout.nameY, layout.applyWidth,
            BUTTON_HEIGHT, canApplyName, Tone.ACCENT));
        hotspots.add(new Hotspot(Action.STEP_DOWN, "-", layout.stepMinusX, layout.stepY, layout.smallButtonWidth,
            BUTTON_HEIGHT, true, Tone.NEUTRAL));
        hotspots.add(new Hotspot(Action.STEP_UP, "+", layout.stepPlusX, layout.stepY, layout.smallButtonWidth,
            BUTTON_HEIGHT, true, Tone.NEUTRAL));
        hotspots.add(new Hotspot(Action.STEP_1, "1", layout.stepPresetX, layout.stepY, layout.presetButtonWidth,
            BUTTON_HEIGHT, true, Tone.NEUTRAL));
        hotspots.add(new Hotspot(Action.STEP_5, "5", layout.stepPresetX + layout.presetButtonWidth + layout.gap,
            layout.stepY, layout.presetButtonWidth, BUTTON_HEIGHT, true, Tone.NEUTRAL));
        hotspots.add(new Hotspot(Action.STEP_10, "10", layout.stepPresetX + (layout.presetButtonWidth + layout.gap) * 2,
            layout.stepY, layout.presetButtonWidth + 4, BUTTON_HEIGHT, true, Tone.NEUTRAL));

        addAxisRow(layout.axisLeft, layout.moveY, layout.axisButtonWidth, layout.gap, true);
        addAxisRow(layout.axisLeft, layout.resizeY, layout.axisButtonWidth, layout.gap, false);

        hotspots.add(new Hotspot(Action.SAVE, "Save", layout.saveX, layout.footerButtonY, layout.footerButtonWidth,
            BUTTON_HEIGHT, true, Tone.ACCENT));
        hotspots.add(new Hotspot(Action.CANCEL, "Cancel", layout.cancelX, layout.footerButtonY,
            layout.footerButtonWidth, BUTTON_HEIGHT, true, Tone.DANGER));
        hotspots.add(new Hotspot(Action.CLOSE, "Close", layout.closeX, layout.footerButtonY,
            layout.footerButtonWidth, BUTTON_HEIGHT, true, Tone.NEUTRAL));

        hovered = null;
        for (Hotspot hotspot : hotspots) {
            if (hotspot.enabled && hotspot.contains(mouseX, mouseY)) {
                hovered = hotspot;
                break;
            }
        }
    }

    private void addAxisRow(int x, int y, int width, int gap, boolean move) {
        addAxisButton(x, y, width, move ? Action.MOVE_X_NEG : Action.SCALE_X_NEG, "X-");
        addAxisButton(x + (width + gap), y, width, move ? Action.MOVE_X_POS : Action.SCALE_X_POS, "X+");
        addAxisButton(x + (width + gap) * 2, y, width, move ? Action.MOVE_Y_NEG : Action.SCALE_Y_NEG, "Y-");
        addAxisButton(x + (width + gap) * 3, y, width, move ? Action.MOVE_Y_POS : Action.SCALE_Y_POS, "Y+");
        addAxisButton(x + (width + gap) * 4, y, width, move ? Action.MOVE_Z_NEG : Action.SCALE_Z_NEG, "Z-");
        addAxisButton(x + (width + gap) * 5, y, width, move ? Action.MOVE_Z_POS : Action.SCALE_Z_POS, "Z+");
    }

    private void addAxisButton(int x, int y, int width, Action action, String label) {
        hotspots.add(new Hotspot(action, label, x, y, width, BUTTON_HEIGHT, true, Tone.NEUTRAL));
    }

    private void activate(Action action) {
        switch (action) {
            case APPLY_NAME -> applyName();
            case STEP_DOWN -> setStep(step() - 1);
            case STEP_UP -> setStep(step() + 1);
            case STEP_1 -> setStep(1);
            case STEP_5 -> setStep(5);
            case STEP_10 -> setStep(10);
            case MOVE_X_NEG -> move(-1, 0, 0);
            case MOVE_X_POS -> move(1, 0, 0);
            case MOVE_Y_NEG -> move(0, -1, 0);
            case MOVE_Y_POS -> move(0, 1, 0);
            case MOVE_Z_NEG -> move(0, 0, -1);
            case MOVE_Z_POS -> move(0, 0, 1);
            case SCALE_X_NEG -> scale(-1, 0, 0);
            case SCALE_X_POS -> scale(1, 0, 0);
            case SCALE_Y_NEG -> scale(0, -1, 0);
            case SCALE_Y_POS -> scale(0, 1, 0);
            case SCALE_Z_NEG -> scale(0, 0, -1);
            case SCALE_Z_POS -> scale(0, 0, 1);
            case SAVE -> {
                applyNameIfChanged();
                BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.save());
                RoomHighlightRenderer.clearEditingState();
                onClose();
            }
            case CANCEL -> {
                BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.cancel());
                RoomHighlightRenderer.clearEditingState();
                onClose();
            }
            case CLOSE -> onClose();
        }
    }

    private void move(int x, int y, int z) {
        int currentStep = step();
        BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.move(x * currentStep, y * currentStep, z * currentStep));
    }

    private void scale(int x, int y, int z) {
        int currentStep = step();
        BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.scale(x * currentStep, y * currentStep, z * currentStep));
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int panelWidth, int panelHeight) {
        graphics.fill(0, 0, width, height, 0x6A020406);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xA0061013);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF00B1014);
        graphics.fill(left, top, left + panelWidth, top + 40, 0xFA111A20);
        graphics.fill(left, top + 40, left + panelWidth, top + 41, 0xFF25D0D8);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 2, 0x663FE5D7);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF2C5660);
    }

    private void drawHeader(GuiGraphics graphics, Layout layout) {
        graphics.drawString(font, "Room Core", layout.contentLeft, layout.top + 12, 0xF6FBFF, false);
        graphics.drawString(font, "editor", layout.contentLeft + 60, layout.top + 12, 0x718B91, false);
        String sizeText = "UI " + layout.panelWidth + "x" + layout.panelHeight
            + " / Window " + width + "x" + height;
        graphics.drawString(font, trimToWidth(sizeText, layout.contentWidth - 8),
            layout.contentLeft, layout.top + 28, 0x586C72, false);

        String id = state.roomId();
        int idWidth = Math.min(layout.panelWidth - 142, font.width(id) + 20);
        int idLeft = layout.contentRight - idWidth;
        drawSoftRect(graphics, idLeft, layout.top + 9, idWidth, 18, 0x5524A6B2, 0xAA2ADCE7);
        graphics.drawString(font, trimToWidth(id, idWidth - 14), idLeft + 10, layout.top + 14, 0xB9F7FF, false);
    }

    private void drawMetrics(GuiGraphics graphics, Layout layout) {
        if (layout.compact) {
            drawCompactMetrics(graphics, layout);
            return;
        }
        drawMetric(graphics, layout.contentLeft, layout.metricY, layout.metricWidth, "X",
            Integer.toString(state.width()), 0xFF25D0D8);
        drawMetric(graphics, layout.contentLeft + layout.metricWidth + layout.gap, layout.metricY,
            layout.metricWidth, "Y", Integer.toString(state.height()), 0xFFE2B54E);
        drawMetric(graphics, layout.contentLeft + (layout.metricWidth + layout.gap) * 2, layout.metricY,
            layout.metricWidth, "Z", Integer.toString(state.depth()), 0xFFE16978);

        graphics.drawString(font, "Center", layout.contentLeft, layout.centerY, 0x6F858B, false);
        graphics.drawString(font, trimToWidth(centerText(), layout.contentWidth - 58),
            layout.contentLeft + 54, layout.centerY, 0xC9E7EA, false);
    }

    private void drawCompactMetrics(GuiGraphics graphics, Layout layout) {
        drawSoftRect(graphics, layout.contentLeft, layout.metricY, layout.contentWidth, 22,
            0xD0131A1F, 0x66465C63);
        String size = "Size  X " + state.width() + "   Y " + state.height() + "   Z " + state.depth();
        graphics.drawString(font, trimToWidth(size, layout.contentWidth / 2 - 8),
            layout.contentLeft + 10, layout.metricY + 7, 0xE8F8FF, false);
        graphics.drawString(font, trimToWidth("Center  " + centerText(), layout.contentWidth / 2 - 8),
            layout.contentLeft + layout.contentWidth / 2 + 4, layout.metricY + 7, 0x9FC1C7, false);
    }

    private void drawMetric(GuiGraphics graphics, int x, int y, int width, String label, String value, int color) {
        drawSoftRect(graphics, x, y, width, 28, 0xD0131A1F, 0x6634484F);
        graphics.fill(x, y, x + 3, y + 28, color);
        graphics.drawString(font, label, x + 10, y + 6, 0x758D93, false);
        graphics.drawString(font, value, x + width - font.width(value) - 10, y + 6, 0xF2FBFF, false);
    }

    private void drawFields(GuiGraphics graphics, Layout layout) {
        graphics.drawString(font, "Name", layout.contentLeft, layout.nameY + 7, 0xA8BBC0, false);
        drawSoftRect(graphics, layout.nameX - 4, layout.nameY, layout.nameWidth + 8, BUTTON_HEIGHT,
            0xD0131A1F, 0x66465C63);

        graphics.drawString(font, "Step", layout.contentLeft, layout.stepY + 7, 0xA8BBC0, false);
        drawSoftRect(graphics, layout.stepInputX - 4, layout.stepY, layout.stepInputWidth + 8, BUTTON_HEIGHT,
            0xD0131A1F, 0x66465C63);

        if (layout.compact) {
            graphics.drawString(font, "Move", layout.contentLeft, layout.moveY + 7, 0x6F858B, false);
            graphics.drawString(font, "Resize", layout.contentLeft, layout.resizeY + 7, 0x6F858B, false);
        } else {
            graphics.drawString(font, "Move", layout.contentLeft, layout.moveY - 13, 0x6F858B, false);
            graphics.drawString(font, "Resize", layout.contentLeft, layout.resizeY - 13, 0x6F858B, false);
        }
    }

    private void drawHotspots(GuiGraphics graphics) {
        for (Hotspot hotspot : hotspots) {
            drawButton(graphics, hotspot, hotspot == hovered);
        }
    }

    private void drawFooter(GuiGraphics graphics, Layout layout) {
        String bounds = "Bounds  " + boundsText();
        int boundsWidth = layout.saveX - layout.contentLeft - layout.gap;
        if (boundsWidth > 28) {
            graphics.drawString(font, trimToWidth(bounds, boundsWidth), layout.contentLeft,
                layout.footerButtonY + 7, 0x72878D, false);
        }
    }

    private void drawButton(GuiGraphics graphics, Hotspot hotspot, boolean hot) {
        int fill = hotspot.tone.fill;
        int line = hotspot.tone.line;
        int text = hotspot.enabled ? hotspot.tone.text : 0x58656A;
        if (!hotspot.enabled) {
            fill = 0x5512161A;
            line = 0x44303A40;
        } else if (hot) {
            fill = hotspot.tone.hoverFill;
            line = hotspot.tone.hoverLine;
        }
        drawSoftRect(graphics, hotspot.x, hotspot.y, hotspot.width, hotspot.height, fill, line);
        int textX = hotspot.x + (hotspot.width - font.width(hotspot.label)) / 2;
        int textY = hotspot.y + (hotspot.height - 8) / 2;
        graphics.drawString(font, hotspot.label, textX, textY, text, false);
    }

    private void drawSoftRect(GuiGraphics graphics, int x, int y, int width, int height, int fill, int line) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, line);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x66000000);
        graphics.fill(x, y, x + 1, y + height, line);
        graphics.fill(x + width - 1, y, x + width, y + height, 0x66000000);
    }

    private void applyName() {
        if (nameEdit == null) return;
        String name = nameEdit.getValue().trim();
        if (name.isEmpty() || name.equals(state.name())) return;
        BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.rename(name));
    }

    private void applyNameIfChanged() {
        if (nameEdit == null) return;
        String name = nameEdit.getValue().trim();
        if (!name.isEmpty() && !name.equals(state.name())) {
            BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.rename(name));
        }
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

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        Layout layout = layout();
        return mouseX >= layout.left && mouseX <= layout.left + layout.panelWidth
            && mouseY >= layout.top && mouseY <= layout.top + layout.panelHeight;
    }

    private void syncEditBoxes(Layout layout) {
        if (nameEdit != null) {
            nameEdit.setX(layout.nameX);
            nameEdit.setY(layout.nameY + 6);
            nameEdit.setWidth(layout.nameWidth);
        }
        if (stepEdit != null) {
            stepEdit.setX(layout.stepInputX);
            stepEdit.setY(layout.stepY + 6);
            stepEdit.setWidth(layout.stepInputWidth);
        }
    }

    private Layout layout() {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (width - panelWidth) / 2;
        int top = Math.max(4, (height - panelHeight) / 2);
        int padding = panelWidth < 340 ? 12 : 18;
        int gap = panelWidth < 340 ? 4 : 6;
        int contentLeft = left + padding;
        int contentRight = left + panelWidth - padding;
        int contentWidth = contentRight - contentLeft;
        boolean compact = panelHeight < 270;

        int metricY = top + (compact ? 48 : 52);
        int metricWidth = Math.max(54, (contentWidth - gap * 2) / 3);
        int centerY = metricY + (compact ? 0 : 36);
        int nameY = compact ? top + 76 : centerY + 22;
        int stepY = nameY + (compact ? 30 : 38);
        int footerButtonY = top + panelHeight - 38;
        int moveY = compact ? stepY + 32 : footerButtonY - BUTTON_HEIGHT * 2 - 29;
        int resizeY = compact ? moveY + 27 : footerButtonY - BUTTON_HEIGHT - 7;
        if (!compact && moveY < stepY + 28) {
            moveY = stepY + 28;
            resizeY = moveY + BUTTON_HEIGHT + 4;
            footerButtonY = Math.max(resizeY + BUTTON_HEIGHT + 7, footerButtonY);
        }

        int applyWidth = panelWidth < 340 ? 50 : 58;
        int labelWidth = panelWidth < 340 ? 42 : 50;
        int applyX = contentRight - applyWidth;
        int nameX = contentLeft + labelWidth;
        int nameWidth = Math.max(80, applyX - nameX - gap - 4);

        int smallButtonWidth = panelWidth < 340 ? 20 : 24;
        int stepInputWidth = panelWidth < 340 ? 30 : 38;
        int presetButtonWidth = panelWidth < 340 ? 24 : 34;
        int stepMinusX = contentLeft + labelWidth;
        int stepInputX = stepMinusX + smallButtonWidth + gap;
        int stepPlusX = stepInputX + stepInputWidth + gap;
        int stepPresetX = stepPlusX + smallButtonWidth + gap + (panelWidth < 340 ? 4 : 14);
        int presetSpace = contentRight - stepPresetX - gap * 2 - 4;
        presetButtonWidth = Math.max(18, Math.min(presetButtonWidth, presetSpace / 3));

        int axisLeft = compact ? contentLeft + labelWidth : contentLeft;
        int axisWidth = contentRight - axisLeft;
        int axisButtonWidth = Math.max(28, (axisWidth - gap * 5) / 6);
        int footerGap = gap + 2;
        int footerButtonWidth = Math.max(48, Math.min(68, (contentWidth - footerGap * 2) / 3));
        int closeX = contentRight - footerButtonWidth;
        int cancelX = closeX - footerGap - footerButtonWidth;
        int saveX = cancelX - footerGap - footerButtonWidth;

        return new Layout(left, top, panelWidth, panelHeight, contentLeft, contentRight, contentWidth, gap,
            metricY, metricWidth, centerY, nameY, nameX, nameWidth, applyX, applyWidth, stepY,
            stepMinusX, stepInputX, stepInputWidth, stepPlusX, stepPresetX, smallButtonWidth,
            presetButtonWidth, axisLeft, axisButtonWidth, moveY, resizeY, footerButtonY, saveX, cancelX,
            closeX, footerButtonWidth, compact);
    }

    private int panelWidth() {
        int available = Math.max(220, width - 16);
        if (available < MIN_PANEL_WIDTH) return available;
        return Math.min(available, MAX_PANEL_WIDTH);
    }

    private int panelHeight() {
        int available = Math.max(220, height - 12);
        return Math.min(available, MAX_PANEL_HEIGHT);
    }

    @Override
    public void onClose() {
        RoomHighlightRenderer.clearEditingState();
        Minecraft.getInstance().setScreen(null);
    }

    private record Layout(
            int left,
            int top,
            int panelWidth,
            int panelHeight,
            int contentLeft,
            int contentRight,
            int contentWidth,
            int gap,
            int metricY,
            int metricWidth,
            int centerY,
            int nameY,
            int nameX,
            int nameWidth,
            int applyX,
            int applyWidth,
            int stepY,
            int stepMinusX,
            int stepInputX,
            int stepInputWidth,
            int stepPlusX,
            int stepPresetX,
            int smallButtonWidth,
            int presetButtonWidth,
            int axisLeft,
            int axisButtonWidth,
            int moveY,
            int resizeY,
            int footerButtonY,
            int saveX,
            int cancelX,
            int closeX,
            int footerButtonWidth,
            boolean compact) {
    }

    private record Hotspot(Action action, String label, int x, int y, int width, int height, boolean enabled, Tone tone) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private enum Tone {
        NEUTRAL(0xAA172027, 0x66465C63, 0xEECDDEE2, 0xCC1D2C34, 0xAA6B8790),
        ACCENT(0xBB0F5960, 0xCC26DCE8, 0xFFF2FEFF, 0xDD13717A, 0xFF49F2FF),
        DANGER(0xAA3E1B22, 0xAA9C4C58, 0xFFFFD7DA, 0xCC5A2430, 0xFFE16978);

        private final int fill;
        private final int line;
        private final int text;
        private final int hoverFill;
        private final int hoverLine;

        Tone(int fill, int line, int text, int hoverFill, int hoverLine) {
            this.fill = fill;
            this.line = line;
            this.text = text;
            this.hoverFill = hoverFill;
            this.hoverLine = hoverLine;
        }
    }

    private enum Action {
        APPLY_NAME,
        STEP_DOWN,
        STEP_UP,
        STEP_1,
        STEP_5,
        STEP_10,
        MOVE_X_NEG,
        MOVE_X_POS,
        MOVE_Y_NEG,
        MOVE_Y_POS,
        MOVE_Z_NEG,
        MOVE_Z_POS,
        SCALE_X_NEG,
        SCALE_X_POS,
        SCALE_Y_NEG,
        SCALE_Y_POS,
        SCALE_Z_NEG,
        SCALE_Z_POS,
        SAVE,
        CANCEL,
        CLOSE
    }
}
