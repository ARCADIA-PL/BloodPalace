package org.com.bloodpalace.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.network.TeleportAnchorInfo;
import org.com.bloodpalace.network.TeleportAnchorSelectPacket;
import org.com.bloodpalace.util.ShowcaseDimensions;

import java.util.List;

public class TeleportAnchorScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private final List<TeleportAnchorInfo> anchors;
    private int scroll;

    public TeleportAnchorScreen(List<TeleportAnchorInfo> anchors) {
        super(Component.literal("Teleport Anchors"));
        this.anchors = List.copyOf(anchors);
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelW = Math.min(360, width - 32);
        int panelX = (width - panelW) / 2;
        int panelY = 32;
        int panelH = Math.min(height - 64, 260);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD0101014);
        graphics.fill(panelX, panelY, panelX + 3, panelY + panelH, 0xFF50E6FF);
        graphics.drawString(font, title, panelX + 14, panelY + 12, 0xE8FFFFFF, false);
        if (anchors.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No teleport anchors in this dimension."),
                width / 2, panelY + 82, 0xA8FFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, anchors.size() - visibleRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        rebuildButtons();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildButtons() {
        clearWidgets();
        int panelW = Math.min(360, width - 32);
        int panelX = (width - panelW) / 2;
        int panelY = 32;
        int listY = panelY + 34;
        int rows = visibleRows();
        for (int row = 0; row < rows; row++) {
            int index = scroll + row;
            if (index >= anchors.size()) break;
            TeleportAnchorInfo anchor = anchors.get(index);
            addRenderableWidget(Button.builder(label(anchor), button -> {
                    BloodPalaceNetwork.sendToServer(new TeleportAnchorSelectPacket(anchor.id()));
                    onClose();
                })
                .bounds(panelX + 14, listY + row * ROW_HEIGHT, panelW - 28, 20)
                .build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(width / 2 - 45, Math.min(height - 30, panelY + 232), 90, 20)
            .build());
    }

    private int visibleRows() {
        return Math.max(1, Math.min(7, (height - 118) / ROW_HEIGHT));
    }

    private Component label(TeleportAnchorInfo anchor) {
        return Component.literal(anchor.name() + "  [" +
            ShowcaseDimensions.formatCoordinate(anchor.x()) + ", " +
            ShowcaseDimensions.formatCoordinate(anchor.y()) + ", " +
            ShowcaseDimensions.formatCoordinate(anchor.z()) + "]");
    }
}
