package com.minelatino.afkfarm.client;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AfkFarmPauseMenu {
    private AfkFarmPauseMenu() {}

    public static void install(Screen screen, Consumer<AbstractWidget> add) {
        int width = Math.min(112, Math.max(88, screen.width / 4));
        int y = Math.max(76, screen.height - 28);
        int x = Math.max(4, screen.width - width - 6);
        add.accept(Button.builder(Component.literal("AFK Farm"), button ->
                Minecraft.getInstance().gui.setScreen(new AfkFarmScreen(screen)))
                .bounds(x, y, width, 20).build());
        // Keep both MineLatino tools balanced at opposite edges of the pause
        // screen. The assistant still has its configurable keyboard shortcut.
        add.accept(Button.builder(Component.literal("Asistente IA"), button ->
                Minecraft.getInstance().gui.setScreen(new AiAssistantScreen(screen)))
                .bounds(6, y, width, 20).build());
    }
}
