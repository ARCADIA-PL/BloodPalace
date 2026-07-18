package org.com.bloodpalace.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.network.TeleportAnchorInfo;
import org.com.bloodpalace.network.TeleportAnchorSelectPacket;
import org.com.bloodpalace.util.ShowcaseDimensions;

import java.util.List;

public class TeleportAnchorScreen extends Screen {

    private static final int TAB_HEIGHT = 18;
    private static final int ROW_HEIGHT = 26;
    private static final int BODY_PAD = 6;
    private static final int ROW_GAP = 4;

    private static final int COLOR_ACCENT = 0xFF50E6FF;
    private static final int COLOR_TEXT = 0xFFFFF4EA;
    private static final int COLOR_TEXT_SOFT = 0xFFE2D1C6;
    private static final int COLOR_LABEL = 0xFFB9A69B;
    private static final int COLOR_MUTED = 0xFF877873;
    private static final int COLOR_PANEL = 0xD0121114;
    private static final int COLOR_PANEL_LINE = 0x885B4A57;
    private static final int COLOR_BODY = 0x65110E10;
    private static final int COLOR_ROW = 0x7A171316;
    private static final int COLOR_ROW_HOT = 0x98211A1E;
    private static final int COLOR_ROW_ACTIVE = 0xAA153340;

    private final List<TeleportAnchorInfo> anchors;

    private Hotspot hovered;
    private int scroll;
    private int contentY;
    private float panelAlpha;
    private long lastRenderMs;

    public TeleportAnchorScreen(List<TeleportAnchorInfo> anchors) {
        super(Component.literal("Teleport Anchors"));
        this.anchors = List.copyOf(anchors);
    }

    @Override
    protected void init() {
        scroll = 0;
        panelAlpha = 0.0F;
        lastRenderMs = 0L;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickAnimation();
        Layout layout = layout();
        rebuildHotspots(layout, mouseX, mouseY);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        Layout layout = layout();
        rebuildHotspots(layout, mouseX, mouseY);
        for (Hotspot hotspot : hotspots(layout)) {
            if (!hotspot.contains(mouseX, mouseY)) continue;
            if (hotspot.kind == Kind.ROW) {
                activate(hotspot.anchor);
                return true;
            }
            if (hotspot.kind == Kind.BUTTON && hotspot.action == Action.CLOSE) {
                onClose();
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout layout = layout();
        if (!layout.bodyContains(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
        scroll = clamp(scroll - (int) Math.signum(delta) * 2, 0, maxScroll(layout));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    private void activate(TeleportAnchorInfo anchor) {
        BloodPalaceNetwork.sendToServer(new TeleportAnchorSelectPacket(anchor.id()));
        onClose();
    }

    private void rebuildHotspots(Layout layout, double mouseX, double mouseY) {
        hovered = null;
        contentY = layout.bodyY + BODY_PAD - scroll * (ROW_HEIGHT + ROW_GAP);
        for (Hotspot hotspot : hotspots(layout)) {
            if (hotspot.contains(mouseX, mouseY)) {
                hovered = hotspot;
                break;
            }
        }
    }

    private List<Hotspot> hotspots(Layout layout) {
        List<Hotspot> list = new java.util.ArrayList<>();
        list.add(Hotspot.button(Action.CLOSE, layout.contentRight - 48, layout.titleY - 2, 48, 16));

        int rowY = contentY + 4;
        int rows = Math.min(visibleRows(layout), anchors.size() - scroll);
        for (int i = 0; i < rows; i++) {
            TeleportAnchorInfo anchor = anchors.get(scroll + i);
            int y = rowY + i * (ROW_HEIGHT + ROW_GAP);
            list.add(Hotspot.row(anchor, layout.bodyX + 8, y, layout.bodyW - 16, ROW_HEIGHT));
        }
        return list;
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
        String titleText = "Teleport Anchors";
        graphics.drawString(font, titleText, layout.contentX, layout.titleY + 3, COLOR_TEXT, false);

        String count = anchors.size() + " anchors";
        graphics.drawString(font, count, layout.contentRight - 48 - font.width(count) - 8,
            layout.titleY + 4, COLOR_MUTED, false);

        drawButton(graphics, layout.contentRight - 48, layout.titleY - 2, 48, 16,
            "Close", hovered != null && hovered.kind == Kind.BUTTON && hovered.action == Action.CLOSE);
    }

    private void drawBody(GuiGraphics graphics, Layout layout) {
        graphics.fill(layout.bodyX, layout.bodyY, layout.bodyRight, layout.bodyBottom, alpha(COLOR_BODY));
        graphics.fill(layout.bodyX, layout.bodyY, layout.bodyRight, layout.bodyY + 1, 0x664A3730);

        graphics.enableScissor(layout.bodyX, layout.bodyY, layout.bodyRight, layout.bodyBottom);
        if (anchors.isEmpty()) {
            graphics.drawCenteredString(font,
                Component.literal("No teleport anchors in this dimension."),
                layout.bodyX + layout.bodyW / 2,
                layout.bodyY + 52,
                COLOR_TEXT_SOFT);
        } else {
            int rows = Math.min(visibleRows(layout), anchors.size() - scroll);
            for (int i = 0; i < rows; i++) {
                TeleportAnchorInfo anchor = anchors.get(scroll + i);
                int y = contentY + 4 + i * (ROW_HEIGHT + ROW_GAP);
                drawRow(graphics, layout, anchor, y, ROW_HEIGHT);
            }
        }
        graphics.disableScissor();

        drawScrollbar(graphics, layout);
    }

    private void drawFooter(GuiGraphics graphics, Layout layout) {
        String dim = "Dimension: " + Minecraft.getInstance().level.dimension().location();
        String hint = anchors.isEmpty() ? "No destination available" : "Click an anchor to teleport";
        graphics.drawString(font, trim(dim, layout.bodyW - font.width(hint) - 10),
            layout.contentX, layout.footerY + 4, COLOR_MUTED, false);
        graphics.drawString(font, hint,
            layout.contentRight - font.width(hint), layout.footerY + 4, COLOR_MUTED, false);
    }

    private void drawRow(GuiGraphics graphics, Layout layout, TeleportAnchorInfo anchor, int y, int h) {
        boolean hot = hovered != null && hovered.kind == Kind.ROW && hovered.anchor.id().equals(anchor.id());
        int fill = hot ? COLOR_ROW_HOT : COLOR_ROW;
        drawPanel(graphics, layout.bodyX + 8, y, layout.bodyW - 16, h, alpha(fill), alpha(COLOR_PANEL_LINE));
        drawAccentEdge(graphics, layout.bodyX + 8, y, h, alpha(COLOR_ACCENT));

        graphics.drawString(font, anchor.name(), layout.bodyX + 20, y + 5, COLOR_TEXT, false);
        String coords = ShowcaseDimensions.formatCoordinate(anchor.x()) + ", "
            + ShowcaseDimensions.formatCoordinate(anchor.y()) + ", "
            + ShowcaseDimensions.formatCoordinate(anchor.z());
        graphics.drawString(font, coords, layout.bodyRight - 12 - font.width(coords), y + 5, COLOR_LABEL, false);

        String action = hot ? "Teleport" : "Go";
        graphics.drawString(font, action, layout.bodyRight - 12 - font.width(action), y + 14,
            hot ? COLOR_ACCENT : COLOR_TEXT_SOFT, false);
    }

    private void drawScrollbar(GuiGraphics graphics, Layout layout) {
        int max = maxScroll(layout);
        if (max <= 0) return;

        int barX = layout.bodyRight - 5;
        int barTop = layout.bodyY + 6;
        int barH = layout.bodyH - 12;
        graphics.fill(barX, barTop, barX + 2, barTop + barH, 0x55333135);

        int thumbH = Math.max(18, barH * visibleRows(layout) / Math.max(1, anchors.size()));
        int thumbY = barTop + (int) ((barH - thumbH) * (scroll / (float) max));
        graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, alpha(COLOR_ACCENT));
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
        graphics.fill(x, y, x + 3, y + h, color);
        graphics.fill(x + 3, y, x + 18, y + 1, color);
        graphics.fill(x + 3, y + h - 1, x + 18, y + h, color);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, String label, boolean hot) {
        int fill = hot ? 0x96371719 : 0x6F1A1111;
        int line = hot ? COLOR_ACCENT : 0x70633F37;
        drawPanel(graphics, x, y, w, h, alpha(fill), alpha(line));
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + 4,
            hot ? COLOR_TEXT : COLOR_TEXT_SOFT, false);
    }

    private int visibleRows(Layout layout) {
        return Math.max(1, layout.bodyH / (ROW_HEIGHT + ROW_GAP));
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, anchors.size() - visibleRows(layout));
    }

    private void tickAnimation() {
        long now = Util.getMillis();
        if (lastRenderMs == 0L) lastRenderMs = now;
        float dt = Math.min((now - lastRenderMs) / 1000.0F, 0.1F);
        lastRenderMs = now;
        panelAlpha += (1.0F - panelAlpha) * Math.min(1.0F, dt * 12.0F);
    }

    private Layout layout() {
        int panelW = Math.min(460, Math.max(300, width - 32));
        int panelH = Math.min(304, Math.max(220, height - 64));
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int contentX = panelX + 14;
        int contentRight = panelX + panelW - 14;
        int bodyX = panelX + 10;
        int bodyY = panelY + 34;
        int bodyW = panelW - 20;
        int bodyRight = bodyX + bodyW;
        int footerY = panelY + panelH - 22;
        int bodyH = Math.max(88, panelH - 60);
        int bodyBottom = bodyY + bodyH;
        int titleY = panelY + 10;
        int contentW = panelW - 28;
        return new Layout(panelX, panelY, panelW, panelH, contentX, contentRight, contentW, titleY,
            bodyX, bodyY, bodyRight, bodyW, bodyH, bodyBottom, footerY);
    }

    private int alpha(int color) {
        int a = (int) (((color >>> 24) & 0xFF) * panelAlpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private enum Kind {
        ROW,
        BUTTON
    }

    private enum Action {
        CLOSE
    }

    private record Layout(int panelX, int panelY, int panelW, int panelH,
            int contentX, int contentRight, int contentW, int titleY,
            int bodyX, int bodyY, int bodyRight, int bodyW, int bodyH,
            int bodyBottom, int footerY) {
        private boolean bodyContains(double mouseX, double mouseY) {
            return mouseX >= bodyX && mouseX <= bodyX + bodyW
                && mouseY >= bodyY && mouseY <= bodyBottom;
        }
    }

    private record Hotspot(Kind kind, int x, int y, int w, int h, Action action, TeleportAnchorInfo anchor) {
        private static Hotspot button(Action action, int x, int y, int w, int h) {
            return new Hotspot(Kind.BUTTON, x, y, w, h, action, null);
        }

        private static Hotspot row(TeleportAnchorInfo anchor, int x, int y, int w, int h) {
            return new Hotspot(Kind.ROW, x, y, w, h, null, anchor);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }
}
