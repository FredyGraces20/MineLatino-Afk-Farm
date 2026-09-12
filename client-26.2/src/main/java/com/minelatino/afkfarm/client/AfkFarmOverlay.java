package com.minelatino.afkfarm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AfkFarmOverlay {
    private AfkFarmOverlay() {}

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        AfkFarmClient client = AfkFarmClient.instance();
        if ((!client.active() && !client.recording()) || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null) return;
        String status = client.status();
        if (status == null || status.isBlank()) return;
        int width = Math.min(graphics.guiWidth() - 12, minecraft.font.width(status) + 20);
        int x = (graphics.guiWidth() - width) / 2;
        graphics.fill(x, 7, x + width, 27, 0xC010151B);
        graphics.fill(x, 7, x + width, 8, 0xFF20D9FF);
        graphics.centeredText(minecraft.font, minecraft.font.plainSubstrByWidth(status, width - 10),
                graphics.guiWidth() / 2, 13, 0xFFF2F7FA);
    }
}
