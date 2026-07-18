package org.com.bloodpalace.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.com.bloodpalace.network.RoomOverlayPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class RoomOverlayRenderer {

    private static final int MESSAGE_TICKS = 76;
    private static final int MAX_MESSAGES = 3;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private RoomOverlayRenderer() {
    }

    public static void push(RoomOverlayPacket packet) {
        String title = packet.entered() ? "ROOM ENTERED" : "ROOM LEFT";
        int accent = packet.entered() ? 0xFFE3B34B : 0xFFC7B8AF;
        ENTRIES.removeIf(entry -> entry.roomId.equals(packet.roomId()) && entry.entered == packet.entered());
        ENTRIES.add(0, new Entry(packet.entered(), packet.roomId(), packet.roomName(), title, accent));
        while (ENTRIES.size() > MAX_MESSAGES) {
            ENTRIES.remove(ENTRIES.size() - 1);
        }
    }

    public static void tick() {
        Iterator<Entry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            entry.remainingTicks--;
            if (entry.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || ENTRIES.isEmpty()) return;

        Font font = minecraft.font;
        int y = Math.max(14, screenHeight / 8);
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            int alpha = alpha(entry.remainingTicks);
            if (alpha <= 0) continue;
            int width = Math.max(font.width(entry.roomName), font.width(entry.title)) + 46;
            int height = 34;
            int x = (screenWidth - width) / 2;
            int rowY = y + i * 40;

            int bg = argb(alpha * 150 / 255, 10, 8, 8);
            int line = withAlpha(entry.accentColor, alpha);
            int muted = argb(alpha * 190 / 255, 207, 186, 176);
            int titleColor = withAlpha(entry.accentColor, alpha);
            int nameColor = argb(alpha, 255, 240, 224);

            graphics.fill(x, rowY, x + width, rowY + height, bg);
            graphics.fill(x, rowY, x + width, rowY + 1, line);
            graphics.fill(x, rowY + height - 1, x + width, rowY + height, argb(alpha * 75 / 255, 0, 0, 0));
            graphics.fill(x, rowY, x + 3, rowY + height, line);

            String marker = entry.entered ? ">" : "<";
            graphics.drawString(font, marker, x + 10, rowY + 13, titleColor, false);
            graphics.drawString(font, entry.title, x + 24, rowY + 7, titleColor, false);
            graphics.drawString(font, trim(font, entry.roomName, width - 36), x + 24, rowY + 20, nameColor, false);

            String id = trim(font, entry.roomId, Math.max(0, width - 92));
            graphics.drawString(font, id, x + width - 10 - font.width(id), rowY + 7, muted, false);
        }
    }

    private static int alpha(int remainingTicks) {
        if (remainingTicks > MESSAGE_TICKS - 8) {
            return (MESSAGE_TICKS - remainingTicks) * 255 / 8;
        }
        if (remainingTicks < 18) {
            return remainingTicks * 255 / 18;
        }
        return 255;
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (clamp(alpha) << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class Entry {
        private final boolean entered;
        private final String roomId;
        private final String roomName;
        private final String title;
        private final int accentColor;
        private int remainingTicks = MESSAGE_TICKS;

        private Entry(boolean entered, String roomId, String roomName, String title, int accentColor) {
            this.entered = entered;
            this.roomId = roomId;
            this.roomName = roomName == null || roomName.isBlank() ? roomId : roomName;
            this.title = title;
            this.accentColor = accentColor;
        }
    }
}
