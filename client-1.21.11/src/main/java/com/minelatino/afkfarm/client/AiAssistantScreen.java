package com.minelatino.afkfarm.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** 1.21.11 native screen adapter for the shared asynchronous assistant controller. */
public final class AiAssistantScreen extends Screen {
    private record MessageHit(int x1, int y1, int x2, int y2, String text) {}
    private final Screen parent;
    private final AiAssistantController controller = AiAssistantController.instance();
    private final List<MessageHit> hits = new ArrayList<>();
    private MultiLineEditBox input;
    private Button send, stop;
    private int left, panelWidth, historyTop, historyBottom, scroll, maxScroll, observedMessages = -1;
    private boolean observedBusy;
    private String copied = "";
    private long copiedUntil;

    public AiAssistantScreen(Screen parent) {
        super(Component.literal("Asistente MineLatino"));
        this.parent = parent;
        AfkFarmClient.instance().pauseForAssistant();
    }

    @Override protected void init() {
        panelWidth = Math.min(680, Math.max(300, width - 12)); left = (width - panelWidth) / 2;
        boolean compact = panelWidth < 470;
        int buttonRows = compact ? 2 : 1, buttonsTop = height - 27 - (buttonRows - 1) * 24;
        int inputHeight = height < 260 ? 40 : 56;
        int inputY = Math.max(72, buttonsTop - inputHeight - 5);
        historyTop = 42; historyBottom = inputY - 6;
        input = MultiLineEditBox.builder().setX(left + 7).setY(inputY)
                .setPlaceholder(Component.literal("Escribe tu mensaje…  Shift+Enter: nueva línea"))
                .build(font, panelWidth - 14, inputHeight, Component.literal("Mensaje"));
        input.setCharacterLimit(AiAssistantController.MAX_MESSAGE_CHARS);
        addRenderableWidget(input);

        if (compact) {
            int half = (panelWidth - 11) / 2;
            send = button("Enviar", left + 5, buttonsTop, half, this::send);
            stop = button("Detener respuesta", left + 6 + half, buttonsTop, panelWidth - half - 11, controller::stop);
            button("Nueva conversación", left + 5, buttonsTop + 24, half, controller::newConversation);
            button("Cerrar", left + 6 + half, buttonsTop + 24, panelWidth - half - 11, this::onClose);
        } else {
            int available = panelWidth - 13, unit = available / 4, x = left + 5;
            send = button("Enviar", x, buttonsTop, unit, this::send); x += unit + 1;
            stop = button("Detener respuesta", x, buttonsTop, unit, controller::stop); x += unit + 1;
            button("Nueva conversación", x, buttonsTop, unit, controller::newConversation); x += unit + 1;
            button("Cerrar", x, buttonsTop, left + panelWidth - 5 - x, this::onClose);
        }
        setInitialFocus(input);
    }

    private Button button(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    private void send() {
        if (controller.busy()) return;
        String value = input.getValue();
        if (!value.trim().isEmpty()) { controller.send(value); input.setValue(""); }
    }

    @Override public void tick() {
        boolean busy = controller.busy();
        send.active = !busy && !input.getValue().trim().isEmpty(); stop.active = busy;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (AiAssistantKeybind.mapping().matches(event)) { onClose(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) { send(); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x <= left + panelWidth && y >= historyTop && y <= historyBottom) {
            scroll = Mth.clamp(scroll - (int)Math.round(vertical * 22), 0, maxScroll); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) for (MessageHit hit : hits)
            if (event.x() >= hit.x1 && event.x() <= hit.x2 && event.y() >= hit.y1 && event.y() <= hit.y2) {
                minecraft.keyboardHandler.setClipboard(hit.text); copied = "Mensaje copiado"; copiedUntil = System.currentTimeMillis() + 1500; return true;
            }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF20A0E14);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(left, 4, left + panelWidth, height - 3, 0xE6121922);
        graphics.fill(left, 4, left + panelWidth, 7, 0xFF20D9FF);
        graphics.fill(left, 7, left + panelWidth, 8, 0xFF865DFF);
        graphics.drawString(font, title, left + 9, 13, 0xFFEAFBFF, false);
        graphics.drawString(font, controller.accountLabel(), left + 9, 26, 0xFF9DB1BF, false);
        String connection = controller.connectionLabel();
        graphics.drawString(font, connection, left + panelWidth - 9 - font.width(connection), 26,
                connection.startsWith("Error") ? 0xFFFF6B7A : 0xFF61E7B7, false);
        renderHistory(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        if (controller.busy()) graphics.drawString(font, "La IA está escribiendo…", left + 10, historyBottom - 12, 0xFF7DEBFF, false);
        String error = controller.error();
        if (!error.isBlank()) graphics.drawCenteredString(font, font.plainSubstrByWidth(error, panelWidth - 22), width / 2, historyBottom - 12, 0xFFFF8B94);
        if (System.currentTimeMillis() < copiedUntil) graphics.drawCenteredString(font, copied, width / 2, 12, 0xFFFFC857);
    }

    private void renderHistory(GuiGraphics graphics) {
        List<AiAssistantController.Message> messages = controller.messages();
        int contentHeight = 8;
        for (AiAssistantController.Message message : messages) contentHeight += messageHeight(message) + 6;
        maxScroll = Math.max(0, contentHeight - Math.max(1, historyBottom - historyTop));
        boolean busy = controller.busy();
        if (messages.size() != observedMessages || (observedBusy && !busy)) scroll = maxScroll;
        observedMessages = messages.size(); observedBusy = busy; scroll = Mth.clamp(scroll, 0, maxScroll);
        hits.clear(); graphics.enableScissor(left + 3, historyTop, left + panelWidth - 3, historyBottom);
        int y = historyTop + 4 - scroll;
        for (AiAssistantController.Message message : messages) {
            boolean user = "user".equals(message.role()); int bubbleWidth = Math.max(160, (panelWidth - 26) * 4 / 5);
            int x = user ? left + panelWidth - bubbleWidth - 8 : left + 8;
            List<FormattedCharSequence> lines = font.split(Component.literal(message.content()), bubbleWidth - 14);
            int h = 20 + lines.size() * 10;
            if (y + h >= historyTop && y <= historyBottom) {
                graphics.fill(x, y, x + bubbleWidth, y + h, user ? 0xD8245366 : 0xD8212633);
                graphics.fill(x, y, x + 3, y + h, user ? 0xFF20D9FF : 0xFF865DFF);
                graphics.drawString(font, user ? "Tú" : "MineLatino IA", x + 8, y + 5, user ? 0xFF7DEBFF : 0xFFBDA8FF, false);
                int lineY = y + 16;
                for (FormattedCharSequence line : lines) { graphics.drawString(font, line, x + 8, lineY, 0xFFF1F5F7, false); lineY += 10; }
                hits.add(new MessageHit(x, Math.max(y, historyTop), x + bubbleWidth, Math.min(y + h, historyBottom), message.content()));
            }
            y += h + 6;
        }
        if (messages.isEmpty()) {
            graphics.drawCenteredString(font, "Pregunta sobre AFK Farm o MineLatino", width / 2, historyTop + 20, 0xFF8FA3B0);
            graphics.drawCenteredString(font, "Haz clic en un mensaje para copiarlo", width / 2, historyTop + 34, 0xFF637681);
        }
        graphics.disableScissor();
    }

    private int messageHeight(AiAssistantController.Message message) {
        int bubbleWidth = Math.max(160, (panelWidth - 26) * 4 / 5);
        return 20 + font.split(Component.literal(message.content()), bubbleWidth - 14).size() * 10;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
