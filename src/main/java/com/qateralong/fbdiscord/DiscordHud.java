package com.qateralong.fbdiscord;

import com.moulberry.flashback.Flashback;
import com.qateralong.fbdiscord.capture.AudioCapture;
import com.qateralong.fbdiscord.replay.LivePlayback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class DiscordHud {
    private DiscordHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        DiscordConfig config = DiscordConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.showHud || minecraft.options.hideGui || Flashback.isExporting()) {
            return;
        }
        AudioCapture capture = DiscordRecorder.capture();
        if (capture != null && (Flashback.RECORDER != null || DiscordRecorder.testing())) {
            boolean live = capture.level() > 0.002F;
            int color = capture.error() != null ? 0xFFFF5555 : live ? 0xFF5865F2 : 0xFF8890A0;
            draw(graphics, minecraft, Component.translatable("flashback_discord.hud.recording"), color, capture.level());
            if (capture.error() != null) {
                graphics.drawString(minecraft.font, capture.error(), 6, 18, 0xFFFF7777);
            } else if (DiscordRecorder.testing()) {
                graphics.drawString(minecraft.font, capture.describe(), 6, 18, 0xFFCCCCCC);
            }
        } else if (Flashback.isInReplay()) {
            if (config.muted) {
                draw(graphics, minecraft, Component.translatable("flashback_discord.hud.muted"), 0xFF8890A0, 0.0F);
            } else if (LivePlayback.level() > 0.002F) {
                draw(graphics, minecraft, Component.translatable("flashback_discord.hud.playing"), 0xFF5865F2, LivePlayback.level());
            }
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft, Component text, int color, float level) {
        int x = 6;
        int y = 6;
        graphics.fill(x - 2, y - 2, x + 92, y + 10, 0x90000000);
        graphics.fill(x, y + 1, x + 6, y + 7, color);
        graphics.drawString(minecraft.font, text, x + 9, y, 0xFFFFFFFF);
        int width = Math.min(30, (int) (Math.sqrt(level) * 60));
        graphics.fill(x + 58, y + 2, x + 88, y + 6, 0x60FFFFFF);
        graphics.fill(x + 58, y + 2, x + 58 + width, y + 6, color);
    }
}
