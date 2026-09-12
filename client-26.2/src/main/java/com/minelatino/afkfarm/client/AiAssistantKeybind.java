package com.minelatino.afkfarm.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class AiAssistantKeybind {
    private static final KeyMapping OPEN = new KeyMapping("key.minelatino_afk_farm.open_ai_assistant",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_APOSTROPHE, KeyMapping.Category.MISC);
    private AiAssistantKeybind() {}
    public static KeyMapping mapping() { return OPEN; }
    public static KeyMapping[] appendTo(KeyMapping[] current) {
        if (Arrays.asList(current).contains(OPEN)) return current;
        KeyMapping[] result = Arrays.copyOf(current, current.length + 1); result[current.length] = OPEN; return result;
    }
    public static void handleTick(Minecraft minecraft) {
        if (!OPEN.consumeClick()) return;
        while (OPEN.consumeClick()) { }
        if (minecraft.gui.screen() instanceof AiAssistantScreen screen) { screen.onClose(); return; }
        if (minecraft.gui.overlay() != null) return;
        minecraft.gui.setScreen(new AiAssistantScreen(minecraft.gui.screen()));
    }
}
