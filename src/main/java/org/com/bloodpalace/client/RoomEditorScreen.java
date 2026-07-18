package org.com.bloodpalace.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.network.RoomEditorActionPacket;
import org.com.bloodpalace.network.RoomEditorState;
import org.com.bloodpalace.network.RoomEditorUpdatePacket;

import java.util.ArrayList;
import java.util.List;

public class RoomEditorScreen extends Screen {

    private static final int MIN_SIZE_X = 10;
    private static final int MIN_SIZE_Y = 5;
    private static final int MIN_SIZE_Z = 10;
    private static final int MAX_SIZE = 192;
    private static final int POS_RANGE = 128;

    private static final int TAB_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 16;
    private static final int FIELD_HEIGHT = 16;
    private static final int SLIDER_TRACK_H = 12;
    private static final int ROW_GAP = 4;
    private static final int BODY_PAD = 5;

    private static final int COLOR_ACCENT = 0xFFE3B34B;
    private static final int COLOR_DANGER = 0xFFB33645;
    private static final int COLOR_TEXT = 0xFFFFE9D6;
    private static final int COLOR_TEXT_SOFT = 0xFFE5CFC1;
    private static final int COLOR_LABEL = 0xFFC9B1A4;
    private static final int COLOR_MUTED = 0xFF8E7D76;
    private static final int COLOR_PANEL = 0x960B0808;
    private static final int COLOR_PANEL_LINE = 0x846B242D;
    private static final int COLOR_ROW = 0x64130E0E;
    private static final int COLOR_ROW_HOT = 0x851B1212;
    private static final int COLOR_FIELD = 0x7016100F;
    private static final int COLOR_FIELD_ACTIVE = 0xA8271716;

    private final List<Hotspot> hotspots = new ArrayList<>();

    private RoomEditorState state;
    private Tab activeTab = Tab.INFO;
    private TextField activeField;
    private Slider activeSlider;
    private String editBuffer = "";
    private CameraType previousCameraType;
    private Hotspot hovered;
    private long lastRenderMs;
    private float panelAlpha;
    private float scroll;
    private float targetScroll;
    private boolean closing;

    private final int baseCenterX;
    private final int baseCenterY;
    private final int baseCenterZ;

    public RoomEditorScreen(RoomEditorState state) {
        super(Component.literal("Room Core Editor"));
        this.state = normalize(state);
        this.baseCenterX = centerX(this.state);
        this.baseCenterY = centerY(this.state);
        this.baseCenterZ = centerZ(this.state);
        RoomCoreRenderer.setEditingState(this.state);
    }

    @Override
    protected void init() {
        if (minecraft != null) {
            if (previousCameraType == null) {
                previousCameraType = minecraft.options.getCameraType();
            }
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        lastRenderMs = 0L;
        panelAlpha = 0.0F;
        clampScroll(layout());
    }

    public boolean isEditing(String roomId) {
        return state.roomId().equals(roomId);
    }

    public void updateState(RoomEditorState state) {
        this.state = normalize(state);
        RoomCoreRenderer.setEditingState(this.state);
        clampScroll(layout());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeField != null) {
            if (keyCode == InputConstants.KEY_S && Screen.hasControlDown()) {
                saveAndClose();
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                activeField = null;
                editBuffer = "";
                return true;
            }
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                commitActiveField(true);
                return true;
            }
            if (keyCode == InputConstants.KEY_BACKSPACE && !editBuffer.isEmpty()) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                return true;
            }
            if (keyCode == InputConstants.KEY_DELETE) {
                editBuffer = "";
                return true;
            }
            return true;
        }

        if (keyCode == InputConstants.KEY_ESCAPE) {
            cancelAndClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_S && Screen.hasControlDown()) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeField == null) return super.charTyped(codePoint, modifiers);
        if (activeField.accepts(codePoint) && editBuffer.length() < activeField.maxLength()) {
            editBuffer += codePoint;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        Layout layout = layout();
        rebuildHotspots(layout, mouseX, mouseY);
        for (Hotspot hotspot : hotspots) {
            if (!hotspot.contains(mouseX, mouseY)) continue;
            if (hotspot.isBodyControl() && !layout.bodyContains(mouseX, mouseY)) continue;

            if (hotspot.kind == Kind.FIELD) {
                if (activeField != hotspot.field) {
                    commitActiveField(true);
                    beginField(hotspot.field);
                }
                return true;
            }

            if (hotspot.kind == Kind.BUTTON && hotspot.action == Action.SAVE) {
                saveAndClose();
                return true;
            }
            if (hotspot.kind == Kind.BUTTON && hotspot.action == Action.CANCEL) {
                cancelAndClose();
                return true;
            }

            commitActiveField(true);
            switch (hotspot.kind) {
                case TAB -> {
                    activeTab = hotspot.tab;
                    scroll = 0.0F;
                    targetScroll = 0.0F;
                }
                case BUTTON -> activate(hotspot.action);
                case SLIDER -> {
                    activeSlider = hotspot.slider;
                    applySlider(mouseX, hotspot);
                }
                case FIELD -> {
                }
            }
            return true;
        }

        commitActiveField(true);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && activeSlider != null) {
            Layout layout = layout();
            rebuildHotspots(layout, mouseX, mouseY);
            Hotspot slider = findSlider(activeSlider);
            if (slider != null) {
                applySlider(mouseX, slider);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && activeSlider != null) {
            activeSlider = null;
            sendUpdate();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout layout = layout();
        if (!layout.bodyContains(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
        targetScroll = clamp(targetScroll - (float) delta * 30.0F, 0.0F, maxScroll(layout));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickAnimation();
        Layout layout = layout();
        clampScroll(layout);
        rebuildHotspots(layout, mouseX, mouseY);
        drawPreviewOverlay(graphics, layout);
        drawConsole(graphics, layout);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closing) cancelAndClose();
    }

    @Override
    public void removed() {
        restoreCamera();
        super.removed();
    }

    private void rebuildHotspots(Layout layout, double mouseX, double mouseY) {
        hotspots.clear();
        hovered = null;

        int actionW = layout.narrow ? 44 : 50;
        int actionY = layout.titleY - 2;
        hotspots.add(Hotspot.button(Action.CANCEL, layout.contentRight - actionW, actionY,
            actionW, BUTTON_HEIGHT, "Cancel"));
        hotspots.add(Hotspot.button(Action.SAVE, layout.contentRight - actionW * 2 - 5, actionY,
            actionW, BUTTON_HEIGHT, "Save"));

        int tabW = Math.min(50, Math.max(24, (layout.contentW - 10) / 3));
        int tabGap = Math.max(2, Math.min(5, (layout.contentW - 10) / 18));
        int tabX = layout.contentX;
        for (Tab tab : Tab.values()) {
            hotspots.add(Hotspot.tab(tab, tabX, layout.tabY, tabW, TAB_HEIGHT));
            tabX += tabW + tabGap;
        }

        int contentY = layout.bodyY + BODY_PAD - Math.round(scroll);
        if (activeTab == Tab.INFO) {
            addInfoHotspots(layout, contentY);
        } else {
            addControlHotspots(layout, contentY, activeTab == Tab.SIZE);
        }

        for (Hotspot hotspot : hotspots) {
            if (!hotspot.contains(mouseX, mouseY)) continue;
            if (hotspot.isBodyControl() && !layout.bodyContains(mouseX, mouseY)) continue;
            hovered = hotspot;
            break;
        }
    }

    private void addInfoHotspots(Layout layout, int y) {
        int rowH = infoRowHeight(layout);
        int rowY = y + 18;
        hotspots.add(Hotspot.field(TextField.ROOM_ID, infoFieldRect(layout, rowY, rowH)));
        hotspots.add(Hotspot.field(TextField.ROOM_NAME, infoFieldRect(layout, rowY + rowH + ROW_GAP, rowH)));
    }

    private void addControlHotspots(Layout layout, int y, boolean sizePage) {
        Axis[] axes = sizePage
            ? new Axis[] {Axis.X, Axis.Y, Axis.Z, Axis.ALL}
            : new Axis[] {Axis.X, Axis.Y, Axis.Z};
        int rowH = controlRowHeight(layout);
        int rowY = y + 18;
        for (int i = 0; i < axes.length; i++) {
            Axis axis = axes[i];
            Rect row = contentRow(layout, rowY + i * (rowH + ROW_GAP), rowH);
            ControlRects rects = controlRects(layout, row);
            hotspots.add(Hotspot.field(field(axis, sizePage), rects.field));
            hotspots.add(Hotspot.slider(slider(axis, sizePage), rects.slider));
            hotspots.add(Hotspot.button(action(axis, false, sizePage), rects.minus, "-"));
            hotspots.add(Hotspot.button(action(axis, true, sizePage), rects.plus, "+"));
        }
    }

    private void drawPreviewOverlay(GuiGraphics graphics, Layout layout) {
        graphics.fill(layout.panelX - 1, layout.panelY, layout.panelX, layout.panelY + layout.panelH,
            0xCC2A1114);

        int badgeW = layout.panelX - layout.margin * 2;
        badgeW = Math.min(badgeW, layout.narrow ? 240 : 320);
        if (badgeW <= 80) return;
        int badgeX = layout.margin;
        int badgeY = layout.margin;
        drawPanel(graphics, badgeX, badgeY, badgeW, 26, 0x82110C0C, 0x88442A27);
        drawAccentEdge(graphics, badgeX, badgeY, 26, COLOR_ACCENT);
        graphics.drawString(font, "Room Preview", badgeX + 10, badgeY + 4, COLOR_TEXT, false);
        String stats = state.width() + " x " + state.height() + " x " + state.depth();
        graphics.drawString(font, stats, badgeX + badgeW - 8 - font.width(stats), badgeY + 15,
            COLOR_MUTED, false);
    }

    private void drawConsole(GuiGraphics graphics, Layout layout) {
        drawPanel(graphics, layout.panelX, layout.panelY, layout.panelW, layout.panelH,
            alpha(COLOR_PANEL), alpha(COLOR_PANEL_LINE));
        drawAccentEdge(graphics, layout.panelX, layout.panelY, layout.panelH, alpha(COLOR_ACCENT));

        drawHeader(graphics, layout);
        drawBody(graphics, layout);
        drawFooter(graphics, layout);
    }

    private void drawHeader(GuiGraphics graphics, Layout layout) {
        String title = "Room Core Editor";
        int actionLeft = findAction(Action.SAVE).x - 8;
        graphics.drawString(font, trim(title, Math.max(0, actionLeft - layout.contentX)),
            layout.contentX, layout.titleY + 3, COLOR_TEXT, false);

        drawButton(graphics, findAction(Action.SAVE));
        drawButton(graphics, findAction(Action.CANCEL));

        for (Hotspot hotspot : hotspots) {
            if (hotspot.kind != Kind.TAB) continue;
            drawTab(graphics, hotspot);
        }
    }

    private void drawBody(GuiGraphics graphics, Layout layout) {
        graphics.fill(layout.bodyX, layout.bodyY, layout.bodyRight, layout.bodyBottom, 0x40070505);
        graphics.fill(layout.bodyX, layout.bodyY, layout.bodyRight, layout.bodyY + 1, 0x664A3730);

        graphics.enableScissor(layout.bodyX, layout.bodyY, layout.bodyRight, layout.bodyBottom);
        int contentY = layout.bodyY + BODY_PAD - Math.round(scroll);
        if (activeTab == Tab.INFO) {
            drawInfoPage(graphics, layout, contentY);
        } else {
            drawControlPage(graphics, layout, contentY, activeTab == Tab.SIZE);
        }
        graphics.disableScissor();

        drawScrollbar(graphics, layout);
    }

    private void drawInfoPage(GuiGraphics graphics, Layout layout, int y) {
        drawSectionTitle(graphics, layout.bodyX + 8, y + 6, "Room Information");

        int rowH = infoRowHeight(layout);
        int rowY = y + 18;
        drawInfoRow(graphics, layout, rowY, rowH, "Room ID", TextField.ROOM_ID, state.roomId());
        drawInfoRow(graphics, layout, rowY + rowH + ROW_GAP, rowH, "Name", TextField.ROOM_NAME, state.name());
    }

    private void drawInfoRow(GuiGraphics graphics, Layout layout, int y, int rowH,
            String label, TextField field, String value) {
        Rect row = contentRow(layout, y, rowH);
        drawPanel(graphics, row.x, row.y, row.w, row.h, rowFill(row), 0x6643312D);
        drawAccentEdge(graphics, row.x, row.y, row.h, COLOR_ACCENT);
        graphics.drawString(font, label, row.x + 8, row.y + 7, COLOR_LABEL, false);
        drawField(graphics, findField(field), activeField == field ? editBuffer : value);
    }

    private void drawControlPage(GuiGraphics graphics, Layout layout, int y, boolean sizePage) {
        drawSectionTitle(graphics, layout.bodyX + 8, y + 6, sizePage ? "Room Size" : "Room Position");

        Axis[] axes = sizePage
            ? new Axis[] {Axis.X, Axis.Y, Axis.Z, Axis.ALL}
            : new Axis[] {Axis.X, Axis.Y, Axis.Z};
        int rowH = controlRowHeight(layout);
        int rowY = y + 18;
        for (int i = 0; i < axes.length; i++) {
            Axis axis = axes[i];
            Rect row = contentRow(layout, rowY + i * (rowH + ROW_GAP), rowH);
            drawControlRow(graphics, layout, row, axis, sizePage);
        }
    }

    private void drawControlRow(GuiGraphics graphics, Layout layout, Rect row, Axis axis, boolean sizePage) {
        ControlRects rects = controlRects(layout, row);
        drawPanel(graphics, row.x, row.y, row.w, row.h, rowFill(row), 0x6643312D);
        drawAccentEdge(graphics, row.x, row.y, row.h, colorForAxis(axis));

        int labelY = row.y + 7;
        graphics.drawString(font, axisLabel(axis, sizePage), row.x + 8, labelY, COLOR_LABEL, false);

        TextField field = field(axis, sizePage);
        drawField(graphics, findField(field),
            activeField == field ? editBuffer : Integer.toString(value(axis, sizePage)));
        drawSlider(graphics, findSlider(slider(axis, sizePage)));
        drawButton(graphics, findAction(action(axis, false, sizePage)));
        drawButton(graphics, findAction(action(axis, true, sizePage)));

        String range = sizePage ? minSize(axis) + "-" + MAX_SIZE : "center +/- " + POS_RANGE;
        int rangeW = font.width(range);
        if (rects.slider.w > rangeW + 20) {
            graphics.drawString(font, range,
                rects.slider.x + rects.slider.w - rangeW - 4,
                rects.slider.y + 2, COLOR_MUTED, false);
        }
    }

    private void drawFooter(GuiGraphics graphics, Layout layout) {
        if (layout.footerH <= 0) return;
        String bounds = "min/max " + state.minX() + " " + state.minY() + " " + state.minZ()
            + " -> " + state.maxX() + " " + state.maxY() + " " + state.maxZ();
        graphics.drawString(font, trim(bounds, layout.contentW - 70), layout.contentX,
            layout.footerY + 4, COLOR_MUTED, false);

        if (maxScroll(layout) > 0.0F) {
            String hint = "scroll";
            graphics.drawString(font, hint, layout.contentRight - font.width(hint),
                layout.footerY + 4, COLOR_MUTED, false);
        }
    }

    private void drawTab(GuiGraphics graphics, Hotspot hotspot) {
        boolean active = activeTab == hotspot.tab;
        boolean hot = hovered == hotspot;
        int fill = active ? 0x8F211515 : hot ? 0x701F1414 : 0x3F120D0D;
        int line = active ? COLOR_ACCENT : hot ? 0x906B242D : 0x4543312D;
        drawPanel(graphics, hotspot.x, hotspot.y, hotspot.w, hotspot.h, alpha(fill), alpha(line));
        String label = trim(hotspot.label, hotspot.w - 8);
        int color = active ? COLOR_TEXT : hot ? COLOR_TEXT_SOFT : COLOR_MUTED;
        graphics.drawString(font, label, hotspot.x + (hotspot.w - font.width(label)) / 2,
            hotspot.y + 4, color, false);
    }

    private void drawButton(GuiGraphics graphics, Hotspot hotspot) {
        if (hotspot == null) return;
        boolean hot = hovered == hotspot;
        int fill = hot ? 0x96371719 : 0x6F1A1111;
        int line = hot ? COLOR_ACCENT : 0x70633F37;
        if (hotspot.action == Action.SAVE) {
            fill = hot ? 0xB08C2330 : 0x8A6B1721;
            line = hot ? 0xFFE3515F : 0xB8BE3341;
        } else if (hotspot.action == Action.CANCEL) {
            fill = hot ? 0x9032171A : 0x65191111;
        }
        drawPanel(graphics, hotspot.x, hotspot.y, hotspot.w, hotspot.h, alpha(fill), alpha(line));
        graphics.drawString(font, hotspot.label, hotspot.x + (hotspot.w - font.width(hotspot.label)) / 2,
            hotspot.y + 4, COLOR_TEXT, false);
    }

    private void drawField(GuiGraphics graphics, Hotspot hotspot, String value) {
        if (hotspot == null) return;
        boolean active = hotspot.field == activeField;
        drawPanel(graphics, hotspot.x, hotspot.y, hotspot.w, hotspot.h,
            active ? alpha(COLOR_FIELD_ACTIVE) : alpha(COLOR_FIELD),
            active ? COLOR_ACCENT : 0x77583A34);
        String text = active ? value + cursor() : value;
        graphics.drawString(font, trim(text, hotspot.w - 8), hotspot.x + 4, hotspot.y + 4,
            active ? 0xFFFFF3E8 : 0xFFE7D5CA, false);
    }

    private void drawSlider(GuiGraphics graphics, Hotspot hotspot) {
        if (hotspot == null) return;
        double percent = sliderPercent(hotspot.slider);

        // Thin track line (3px) centered vertically in the hotspot
        int trackCY = hotspot.y + hotspot.h / 2;
        int trackH = 3;
        int trackY = trackCY - trackH / 2;

        // Track background
        graphics.fill(hotspot.x, trackY, hotspot.x + hotspot.w, trackY + trackH,
            alpha(0x88493632));
        // Track fill (accent color to the knob position)
        graphics.fill(hotspot.x, trackY,
            hotspot.x + (int) (hotspot.w * percent), trackY + trackH,
            COLOR_ACCENT);

        // Knob fills full hotspot height, centered on the percent position
        int knobX = hotspot.x + (int) (hotspot.w * percent);
        int knobHW = 3;
        graphics.fill(knobX - knobHW, hotspot.y,
            knobX + knobHW, hotspot.y + hotspot.h,
            COLOR_TEXT);
        // Accent line through knob center
        graphics.fill(knobX - 1, hotspot.y,
            knobX + 1, hotspot.y + hotspot.h,
            COLOR_ACCENT);
    }

    private void drawSectionTitle(GuiGraphics graphics, int x, int y, String title) {
        graphics.drawString(font, title, x, y, COLOR_ACCENT, false);
        graphics.fill(x, y + 13, x + Math.min(180, font.width(title) + 42), y + 14, 0xAA7C3038);
    }

    private void drawScrollbar(GuiGraphics graphics, Layout layout) {
        float max = maxScroll(layout);
        if (max <= 0.0F || layout.bodyH <= 0) return;
        int trackX = layout.bodyRight + 1;
        int trackW = 3;
        int trackY = layout.bodyY + 2;
        int trackH = Math.max(12, layout.bodyH - 4);
        int thumbH = Math.max(18, (int) (trackH * (layout.bodyH / (float) contentHeight(layout))));
        int thumbY = trackY + (int) ((trackH - thumbH) * (scroll / max));

        // Clamp to panel bounds
        int panelRight = layout.panelX + layout.panelW;
        if (trackX + trackW + 1 > panelRight) {
            trackX = panelRight - trackW - 2;
        }

        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x55392A27);
        graphics.fill(trackX - 1, thumbY, trackX + trackW + 1, thumbY + thumbH, COLOR_ACCENT);
    }

    private int rowFill(Rect row) {
        return hovered != null && hovered.isBodyControl()
            && hovered.y >= row.y && hovered.y <= row.y + row.h
            ? COLOR_ROW_HOT
            : COLOR_ROW;
    }

    private Rect contentRow(Layout layout, int y, int h) {
        return new Rect(layout.bodyX + 8, y, Math.max(80, layout.bodyW - 16), h);
    }

    private Rect infoFieldRect(Layout layout, int rowY, int rowH) {
        Rect row = contentRow(layout, rowY, rowH);
        int labelW = layout.ultraNarrow ? 54 : 64;
        int fieldW = Math.max(52, row.w - labelW - 8);
        return new Rect(row.x + labelW, row.y + 7, fieldW, FIELD_HEIGHT);
    }

    private ControlRects controlRects(Layout layout, Rect row) {
        int buttonW = layout.ultraNarrow ? 20 : 18;
        int fieldW = layout.ultraNarrow ? 54 : layout.narrow ? 58 : 56;
        fieldW = Math.min(fieldW, Math.max(52, (row.w - 100) / 2));

        // Layer 1 (top): field row
        int fieldY = row.y + 3;
        int fieldX = row.x + row.w - fieldW - 8;

        // Layer 2 (bottom): slider + buttons row, 18px tall zone
        int bottomTop = row.y + row.h - 18;
        int sliderY = bottomTop + 3;
        int buttonY = bottomTop + 1;

        // Buttons anchored to right edge
        int plusX = row.x + row.w - buttonW - 8;
        int minusX = plusX - buttonW - 4;

        // Slider spans from left edge to just before minus button
        int sliderX = row.x + 8;
        int sliderW = Math.max(40, minusX - sliderX - 8);

        Rect field  = new Rect(fieldX,  fieldY,  fieldW,  FIELD_HEIGHT);
        Rect slider = new Rect(sliderX, sliderY, sliderW, SLIDER_TRACK_H);
        Rect minus  = new Rect(minusX, buttonY, buttonW, BUTTON_HEIGHT);
        Rect plus   = new Rect(plusX,  buttonY, buttonW, BUTTON_HEIGHT);
        return new ControlRects(field, slider, minus, plus);
    }

    private int infoRowHeight(Layout layout) {
        return layout.narrow ? 38 : 34;
    }

    private int controlRowHeight(Layout layout) {
        if (layout.ultraNarrow) return 64;
        return layout.narrow ? 50 : 44;
    }

    private int contentHeight(Layout layout) {
        return switch (activeTab) {
            case INFO -> 22 + 2 * infoRowHeight(layout) + ROW_GAP + 10;
            case SIZE -> controlContentHeight(layout, 4);
            case POSITION -> controlContentHeight(layout, 3);
        };
    }

    private int controlContentHeight(Layout layout, int rows) {
        return 22 + rows * controlRowHeight(layout) + Math.max(0, rows - 1) * ROW_GAP + 10;
    }

    private float maxScroll(Layout layout) {
        return Math.max(0.0F, contentHeight(layout) - layout.bodyH);
    }

    private Layout layout() {
        int margin = width < 420 ? 4 : 8;
        boolean narrow = width < 880;
        boolean tiny = height < 430;

        // Panel width: ~22% of screen width, clamped between 160 and 280 pixels
        int panelW = clamp((int) (width * 0.22F), 160, 280);

        int panelX = Math.max(margin, width - margin - panelW);
        int panelY = margin;
        int panelH = Math.max(160, height - margin * 2);
        int panelBottom = Math.min(height - margin, panelY + panelH);
        panelH = panelBottom - panelY;

        int contentX = panelX + (narrow ? 8 : 10);
        int contentRight = panelX + panelW - (narrow ? 8 : 10);
        int contentW = Math.max(80, contentRight - contentX);
        boolean ultraNarrow = contentW < 250;

        int headerH = tiny ? 48 : 52;
        int footerH = tiny ? 0 : 14;
        int titleY = panelY + 6;
        int tabY = titleY + 22;
        int bodyX = contentX;
        int bodyRight = contentRight;
        int bodyY = tabY + TAB_HEIGHT + 6;
        int footerY = panelBottom - footerH;
        int bodyBottom = footerH > 0 ? footerY - 5 : panelBottom - 5;
        int bodyH = Math.max(48, bodyBottom - bodyY);

        return new Layout(margin, panelY, panelX, panelY, panelW, panelH,
            contentX, contentRight, contentW, titleY, tabY, bodyX, bodyY, bodyRight,
            bodyBottom, Math.max(80, bodyRight - bodyX), bodyH, footerY, footerH,
            narrow, ultraNarrow);
    }

    private void activate(Action action) {
        switch (action) {
            case SAVE -> saveAndClose();
            case CANCEL -> cancelAndClose();
            case SIZE_X_MINUS -> setSize(Axis.X, sizeX() - 1, true);
            case SIZE_X_PLUS -> setSize(Axis.X, sizeX() + 1, true);
            case SIZE_Y_MINUS -> setSize(Axis.Y, sizeY() - 1, true);
            case SIZE_Y_PLUS -> setSize(Axis.Y, sizeY() + 1, true);
            case SIZE_Z_MINUS -> setSize(Axis.Z, sizeZ() - 1, true);
            case SIZE_Z_PLUS -> setSize(Axis.Z, sizeZ() + 1, true);
            case SIZE_ALL_MINUS -> scaleAll(-1, true);
            case SIZE_ALL_PLUS -> scaleAll(1, true);
            case POS_X_MINUS -> setCenter(Axis.X, centerX(state) - 1, true);
            case POS_X_PLUS -> setCenter(Axis.X, centerX(state) + 1, true);
            case POS_Y_MINUS -> setCenter(Axis.Y, centerY(state) - 1, true);
            case POS_Y_PLUS -> setCenter(Axis.Y, centerY(state) + 1, true);
            case POS_Z_MINUS -> setCenter(Axis.Z, centerZ(state) - 1, true);
            case POS_Z_PLUS -> setCenter(Axis.Z, centerZ(state) + 1, true);
        }
    }

    private void applySlider(double mouseX, Hotspot hotspot) {
        double t = clamp((mouseX - hotspot.x) / hotspot.w, 0.0D, 1.0D);
        switch (hotspot.slider) {
            case SIZE_X -> setSize(Axis.X, lerpInt(MIN_SIZE_X, MAX_SIZE, t), false);
            case SIZE_Y -> setSize(Axis.Y, lerpInt(MIN_SIZE_Y, MAX_SIZE, t), false);
            case SIZE_Z -> setSize(Axis.Z, lerpInt(MIN_SIZE_Z, MAX_SIZE, t), false);
            case SIZE_ALL -> {
                int value = lerpInt(Math.max(Math.max(MIN_SIZE_X, MIN_SIZE_Y), MIN_SIZE_Z), MAX_SIZE, t);
                setBoundsFromCenter(centerX(state), centerY(state), centerZ(state),
                    value, Math.max(MIN_SIZE_Y, value), value, false);
            }
            case POS_X -> setCenter(Axis.X, lerpInt(baseCenterX - POS_RANGE, baseCenterX + POS_RANGE, t), false);
            case POS_Y -> setCenter(Axis.Y, lerpInt(baseCenterY - POS_RANGE, baseCenterY + POS_RANGE, t), false);
            case POS_Z -> setCenter(Axis.Z, lerpInt(baseCenterZ - POS_RANGE, baseCenterZ + POS_RANGE, t), false);
        }
    }

    private void beginField(TextField field) {
        activeField = field;
        editBuffer = switch (field) {
            case ROOM_ID -> state.roomId();
            case ROOM_NAME -> state.name();
            case SIZE_X -> Integer.toString(sizeX());
            case SIZE_Y -> Integer.toString(sizeY());
            case SIZE_Z -> Integer.toString(sizeZ());
            case SIZE_ALL -> Integer.toString(Math.max(Math.max(sizeX(), sizeY()), sizeZ()));
            case POS_X -> Integer.toString(centerX(state));
            case POS_Y -> Integer.toString(centerY(state));
            case POS_Z -> Integer.toString(centerZ(state));
        };
    }

    private void commitActiveField(boolean send) {
        if (activeField == null) return;
        TextField field = activeField;
        String value = editBuffer.trim();
        activeField = null;
        editBuffer = "";
        if (value.isEmpty()) return;

        try {
            switch (field) {
                case ROOM_ID -> setState(new RoomEditorState(cleanId(value), state.name(),
                    state.minX(), state.minY(), state.minZ(), state.maxX(), state.maxY(), state.maxZ()), send);
                case ROOM_NAME -> setState(new RoomEditorState(state.roomId(), value,
                    state.minX(), state.minY(), state.minZ(), state.maxX(), state.maxY(), state.maxZ()), send);
                case SIZE_X -> setSize(Axis.X, Integer.parseInt(value), send);
                case SIZE_Y -> setSize(Axis.Y, Integer.parseInt(value), send);
                case SIZE_Z -> setSize(Axis.Z, Integer.parseInt(value), send);
                case SIZE_ALL -> {
                    int parsed = Integer.parseInt(value);
                    setBoundsFromCenter(centerX(state), centerY(state), centerZ(state),
                        parsed, Math.max(MIN_SIZE_Y, parsed), parsed, send);
                }
                case POS_X -> setCenter(Axis.X, Integer.parseInt(value), send);
                case POS_Y -> setCenter(Axis.Y, Integer.parseInt(value), send);
                case POS_Z -> setCenter(Axis.Z, Integer.parseInt(value), send);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void scaleAll(int delta, boolean send) {
        setBoundsFromCenter(centerX(state), centerY(state), centerZ(state),
            sizeX() + delta, sizeY() + delta, sizeZ() + delta, send);
    }

    private void setSize(Axis axis, int value, boolean send) {
        int x = sizeX();
        int y = sizeY();
        int z = sizeZ();
        if (axis == Axis.X) x = clamp(value, MIN_SIZE_X, MAX_SIZE);
        if (axis == Axis.Y) y = clamp(value, MIN_SIZE_Y, MAX_SIZE);
        if (axis == Axis.Z) z = clamp(value, MIN_SIZE_Z, MAX_SIZE);
        setBoundsFromCenter(centerX(state), centerY(state), centerZ(state), x, y, z, send);
    }

    private void setCenter(Axis axis, int value, boolean send) {
        int x = centerX(state);
        int y = centerY(state);
        int z = centerZ(state);
        if (axis == Axis.X) x = value;
        if (axis == Axis.Y) y = value;
        if (axis == Axis.Z) z = value;
        setBoundsFromCenter(x, y, z, sizeX(), sizeY(), sizeZ(), send);
    }

    private void setBoundsFromCenter(int centerX, int centerY, int centerZ,
            int sizeX, int sizeY, int sizeZ, boolean send) {
        int x = clamp(sizeX, MIN_SIZE_X, MAX_SIZE);
        int y = clamp(sizeY, MIN_SIZE_Y, MAX_SIZE);
        int z = clamp(sizeZ, MIN_SIZE_Z, MAX_SIZE);
        int minX = centerX - x / 2;
        int minY = centerY - y / 2;
        int minZ = centerZ - z / 2;
        setState(new RoomEditorState(state.roomId(), state.name(), minX, minY, minZ,
            minX + x - 1, minY + y - 1, minZ + z - 1), send);
    }

    private void setState(RoomEditorState next, boolean send) {
        state = normalize(next);
        RoomCoreRenderer.setEditingState(state);
        clampScroll(layout());
        if (send) sendUpdate();
    }

    private void sendUpdate() {
        BloodPalaceNetwork.sendToServer(new RoomEditorUpdatePacket(state));
    }

    private void saveAndClose() {
        commitActiveField(false);
        BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.save(state));
        closeLocally();
    }

    private void cancelAndClose() {
        if (!closing) BloodPalaceNetwork.sendToServer(RoomEditorActionPacket.cancel());
        closeLocally();
    }

    private void closeLocally() {
        if (closing) return;
        closing = true;
        RoomCoreRenderer.clearEditingState();
        restoreCamera();
        Minecraft.getInstance().setScreen(null);
    }

    private void restoreCamera() {
        if (minecraft != null && previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
            previousCameraType = null;
        }
    }

    private void tickAnimation() {
        long now = Util.getMillis();
        if (lastRenderMs == 0L) lastRenderMs = now;
        float dt = Math.min((now - lastRenderMs) / 1000.0F, 0.1F);
        lastRenderMs = now;
        panelAlpha += (1.0F - panelAlpha) * Math.min(1.0F, dt * 12.0F);
        scroll += (targetScroll - scroll) * Math.min(1.0F, dt * 14.0F);
        if (Math.abs(scroll - targetScroll) < 0.25F) scroll = targetScroll;
    }

    private void clampScroll(Layout layout) {
        float max = maxScroll(layout);
        targetScroll = clamp(targetScroll, 0.0F, max);
        scroll = clamp(scroll, 0.0F, max);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, int fill, int line) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, line);
        graphics.fill(x, y, x + 1, y + h, line);
        graphics.fill(x + w - 1, y, x + w, y + h, 0x66000000);
        graphics.fill(x, y + h - 1, x + w, y + h, 0x66000000);
    }

    private void drawAccentEdge(GuiGraphics graphics, int x, int y, int h, int color) {
        if (h <= 0) return;
        graphics.fill(x, y, x + 3, y + h, color);
        graphics.fill(x + 3, y, x + 16, y + 1, color);
        graphics.fill(x + 3, y + h - 1, x + 16, y + h, color);
    }

    private Hotspot findAction(Action action) {
        for (Hotspot hotspot : hotspots) if (hotspot.action == action) return hotspot;
        return null;
    }

    private Hotspot findField(TextField field) {
        for (Hotspot hotspot : hotspots) if (hotspot.field == field) return hotspot;
        return null;
    }

    private Hotspot findSlider(Slider slider) {
        for (Hotspot hotspot : hotspots) if (hotspot.slider == slider) return hotspot;
        return null;
    }

    private double sliderPercent(Slider slider) {
        return switch (slider) {
            case SIZE_X -> percent(sizeX(), MIN_SIZE_X, MAX_SIZE);
            case SIZE_Y -> percent(sizeY(), MIN_SIZE_Y, MAX_SIZE);
            case SIZE_Z -> percent(sizeZ(), MIN_SIZE_Z, MAX_SIZE);
            case SIZE_ALL -> percent(Math.max(Math.max(sizeX(), sizeY()), sizeZ()),
                Math.max(Math.max(MIN_SIZE_X, MIN_SIZE_Y), MIN_SIZE_Z), MAX_SIZE);
            case POS_X -> percent(centerX(state), baseCenterX - POS_RANGE, baseCenterX + POS_RANGE);
            case POS_Y -> percent(centerY(state), baseCenterY - POS_RANGE, baseCenterY + POS_RANGE);
            case POS_Z -> percent(centerZ(state), baseCenterZ - POS_RANGE, baseCenterZ + POS_RANGE);
        };
    }

    private Action action(Axis axis, boolean plus, boolean sizePage) {
        if (sizePage) {
            return switch (axis) {
                case X -> plus ? Action.SIZE_X_PLUS : Action.SIZE_X_MINUS;
                case Y -> plus ? Action.SIZE_Y_PLUS : Action.SIZE_Y_MINUS;
                case Z -> plus ? Action.SIZE_Z_PLUS : Action.SIZE_Z_MINUS;
                case ALL -> plus ? Action.SIZE_ALL_PLUS : Action.SIZE_ALL_MINUS;
            };
        }
        return switch (axis) {
            case X -> plus ? Action.POS_X_PLUS : Action.POS_X_MINUS;
            case Y -> plus ? Action.POS_Y_PLUS : Action.POS_Y_MINUS;
            case Z, ALL -> plus ? Action.POS_Z_PLUS : Action.POS_Z_MINUS;
        };
    }

    private Slider slider(Axis axis, boolean sizePage) {
        if (sizePage) {
            return switch (axis) {
                case X -> Slider.SIZE_X;
                case Y -> Slider.SIZE_Y;
                case Z -> Slider.SIZE_Z;
                case ALL -> Slider.SIZE_ALL;
            };
        }
        return switch (axis) {
            case X -> Slider.POS_X;
            case Y -> Slider.POS_Y;
            case Z, ALL -> Slider.POS_Z;
        };
    }

    private TextField field(Axis axis, boolean sizePage) {
        if (sizePage) {
            return switch (axis) {
                case X -> TextField.SIZE_X;
                case Y -> TextField.SIZE_Y;
                case Z -> TextField.SIZE_Z;
                case ALL -> TextField.SIZE_ALL;
            };
        }
        return switch (axis) {
            case X -> TextField.POS_X;
            case Y -> TextField.POS_Y;
            case Z, ALL -> TextField.POS_Z;
        };
    }

    private int value(Axis axis, boolean sizePage) {
        if (sizePage) {
            return switch (axis) {
                case X -> sizeX();
                case Y -> sizeY();
                case Z -> sizeZ();
                case ALL -> Math.max(Math.max(sizeX(), sizeY()), sizeZ());
            };
        }
        return switch (axis) {
            case X -> centerX(state);
            case Y -> centerY(state);
            case Z, ALL -> centerZ(state);
        };
    }

    private String axisLabel(Axis axis, boolean sizePage) {
        if (sizePage) {
            return switch (axis) {
                case X -> "Size X";
                case Y -> "Size Y";
                case Z -> "Size Z";
                case ALL -> "Overall";
            };
        }
        return switch (axis) {
            case X -> "Pos X";
            case Y -> "Pos Y";
            case Z, ALL -> "Pos Z";
        };
    }

    private String axisHint(Axis axis, boolean sizePage) {
        if (axis == Axis.ALL) return "uniform room scale";
        return sizePage ? "block length" : "room center";
    }

    private int minSize(Axis axis) {
        return switch (axis) {
            case X, ALL -> MIN_SIZE_X;
            case Y -> MIN_SIZE_Y;
            case Z -> MIN_SIZE_Z;
        };
    }

    private int colorForAxis(Axis axis) {
        return switch (axis) {
            case X -> 0xFFE16978;
            case Y -> 0xFFE3B34B;
            case Z -> 0xFF4FC3F7;
            case ALL -> 0xFFB98CFF;
        };
    }

    private int sizeX() {
        return state.width();
    }

    private int sizeY() {
        return state.height();
    }

    private int sizeZ() {
        return state.depth();
    }

    private static int centerX(RoomEditorState state) {
        return (state.minX() + state.maxX() + 1) / 2;
    }

    private static int centerY(RoomEditorState state) {
        return (state.minY() + state.maxY() + 1) / 2;
    }

    private static int centerZ(RoomEditorState state) {
        return (state.minZ() + state.maxZ() + 1) / 2;
    }

    private RoomEditorState normalize(RoomEditorState state) {
        int minX = Math.min(state.minX(), state.maxX());
        int minY = Math.min(state.minY(), state.maxY());
        int minZ = Math.min(state.minZ(), state.maxZ());
        int maxX = Math.max(state.minX(), state.maxX());
        int maxY = Math.max(state.minY(), state.maxY());
        int maxZ = Math.max(state.minZ(), state.maxZ());
        maxX = Math.max(maxX, minX + MIN_SIZE_X - 1);
        maxY = Math.max(maxY, minY + MIN_SIZE_Y - 1);
        maxZ = Math.max(maxZ, minZ + MIN_SIZE_Z - 1);
        String id = cleanId(state.roomId());
        if (id.isBlank()) id = "room";
        String name = state.name() == null || state.name().isBlank() ? id : state.name().trim();
        return new RoomEditorState(id, name, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private String cleanId(String value) {
        return value == null ? "" : value.trim().replace(' ', '_');
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private String cursor() {
        return (Util.getMillis() / 350L) % 2L == 0L ? "_" : "";
    }

    private double percent(int value, int min, int max) {
        if (max <= min) return 0.0D;
        return clamp((value - min) / (double) (max - min), 0.0D, 1.0D);
    }

    private int lerpInt(int min, int max, double t) {
        return (int) Math.round(min + (max - min) * clamp(t, 0.0D, 1.0D));
    }

    private int alpha(int color) {
        int a = (int) (((color >>> 24) & 0xFF) * panelAlpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Tab {
        INFO("Info"),
        SIZE("Size"),
        POSITION("Position");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum Axis {
        X,
        Y,
        Z,
        ALL
    }

    private enum TextField {
        ROOM_ID(false, 64),
        ROOM_NAME(false, 64),
        SIZE_X(true, 4),
        SIZE_Y(true, 4),
        SIZE_Z(true, 4),
        SIZE_ALL(true, 4),
        POS_X(true, 7),
        POS_Y(true, 7),
        POS_Z(true, 7);

        private final boolean numeric;
        private final int maxLength;

        TextField(boolean numeric, int maxLength) {
            this.numeric = numeric;
            this.maxLength = maxLength;
        }

        private boolean accepts(char c) {
            if (!numeric) return c >= 32 && c != 127;
            return Character.isDigit(c) || c == '-';
        }

        private int maxLength() {
            return maxLength;
        }
    }

    private enum Slider {
        SIZE_X,
        SIZE_Y,
        SIZE_Z,
        SIZE_ALL,
        POS_X,
        POS_Y,
        POS_Z
    }

    private enum Action {
        SAVE,
        CANCEL,
        SIZE_X_MINUS,
        SIZE_X_PLUS,
        SIZE_Y_MINUS,
        SIZE_Y_PLUS,
        SIZE_Z_MINUS,
        SIZE_Z_PLUS,
        SIZE_ALL_MINUS,
        SIZE_ALL_PLUS,
        POS_X_MINUS,
        POS_X_PLUS,
        POS_Y_MINUS,
        POS_Y_PLUS,
        POS_Z_MINUS,
        POS_Z_PLUS
    }

    private enum Kind {
        BUTTON,
        FIELD,
        SLIDER,
        TAB
    }

    private record Layout(int margin, int previewH, int panelX, int panelY, int panelW, int panelH,
            int contentX, int contentRight, int contentW, int titleY, int tabY,
            int bodyX, int bodyY, int bodyRight, int bodyBottom, int bodyW, int bodyH,
            int footerY, int footerH, boolean narrow, boolean ultraNarrow) {
        private boolean bodyContains(double mouseX, double mouseY) {
            return mouseX >= bodyX && mouseX <= bodyRight && mouseY >= bodyY && mouseY <= bodyBottom;
        }
    }

    private record Rect(int x, int y, int w, int h) {
    }

    private record ControlRects(Rect field, Rect slider, Rect minus, Rect plus) {
    }

    private static final class Hotspot {
        private final Kind kind;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final String label;
        private final Action action;
        private final TextField field;
        private final Slider slider;
        private final Tab tab;

        private Hotspot(Kind kind, int x, int y, int w, int h, String label,
                Action action, TextField field, Slider slider, Tab tab) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.action = action;
            this.field = field;
            this.slider = slider;
            this.tab = tab;
        }

        private static Hotspot button(Action action, Rect rect, String label) {
            return button(action, rect.x, rect.y, rect.w, rect.h, label);
        }

        private static Hotspot button(Action action, int x, int y, int w, int h, String label) {
            return new Hotspot(Kind.BUTTON, x, y, w, h, label, action, null, null, null);
        }

        private static Hotspot field(TextField field, Rect rect) {
            return new Hotspot(Kind.FIELD, rect.x, rect.y, rect.w, rect.h, "", null, field, null, null);
        }

        private static Hotspot slider(Slider slider, Rect rect) {
            return new Hotspot(Kind.SLIDER, rect.x, rect.y, rect.w, rect.h, "", null, null, slider, null);
        }

        private static Hotspot tab(Tab tab, int x, int y, int w, int h) {
            return new Hotspot(Kind.TAB, x, y, w, h, tab.label, null, null, null, tab);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }

        private boolean isBodyControl() {
            if (kind == Kind.FIELD || kind == Kind.SLIDER) return true;
            return kind == Kind.BUTTON && action != Action.SAVE && action != Action.CANCEL;
        }
    }
}
