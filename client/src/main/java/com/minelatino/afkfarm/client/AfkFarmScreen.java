package com.minelatino.afkfarm.client;

import com.minelatino.afkfarm.AfkFarmConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact tabbed configuration screen with recorded routes and explicit target selection. */
public final class AfkFarmScreen extends Screen {
    private enum Tab { GENERAL, COMMANDS, MOVEMENT, ATTACK }
    private record LabeledField(String label, EditBox box) {}

    private final Screen parent;
    private final Tab tab;
    private final List<LabeledField> fields = new ArrayList<>();
    private EditBox commands, postJoin, betweenCommands, movementDelay;
    private EditBox radius, rotation, routeName;
    private Button flowButton;
    private int generalActionsY;
    private int attackInfoY;

    public AfkFarmScreen(Screen parent) { this(parent, Tab.GENERAL); }
    private AfkFarmScreen(Screen parent, Tab tab) {
        super(Component.literal("MineLatino AFK Farm"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override protected void init() {
        fields.clear();
        int panelWidth = Math.min(430, Math.max(250, width - 20));
        int left = (width - panelWidth) / 2;
        int tabWidth = (panelWidth - 9) / 4;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab value = Tab.values()[i];
            Button button = addRenderableWidget(Button.builder(Component.literal(tabName(value)), ignored -> switchTab(value))
                    .bounds(left + i * (tabWidth + 3), 24, tabWidth, 20).build());
            button.active = value != tab;
        }
        switch (tab) {
            case GENERAL -> initGeneral(left, panelWidth);
            case COMMANDS -> initCommands(left, panelWidth);
            case MOVEMENT -> initMovement(left, panelWidth);
            case ATTACK -> initAttack(left, panelWidth);
        }
        addRenderableWidget(Button.builder(Component.literal("Volver"), button -> onClose())
                .bounds(left, height - 27, panelWidth, 20).build());
    }

    private void initGeneral(int left, int width) {
        AfkUsageController.instance().refresh();
        AfkFarmConfig config = config();
        var value = config.snapshot();
        int y = 55;
        addToggle(left, y, width, "Reconexión automática", value.autoReconnect(), config::setAutoReconnect); y += 24;
        addToggle(left, y, width, "Módulo de comandos", value.commandsEnabled(), config::setCommandsEnabled); y += 24;
        addToggle(left, y, width, "Módulo de recorrido", value.navigationEnabled(), config::setNavigationEnabled); y += 24;
        addToggle(left, y, width, "Módulo de ataque", value.autoAttackEnabled(), config::setAutoAttackEnabled); y += 29;
        boolean active = AfkFarmClient.instance().active();
        // The assistant belongs to the pause menu; this screen is exclusively
        // for AFK Farm configuration and control.
        generalActionsY = Math.min(y, height - 76);
        flowButton = addRenderableWidget(Button.builder(Component.literal(active ? "Detener flujo AFK" : "Iniciar flujo AFK"), button -> {
            saveFields();
            if (active) AfkFarmClient.instance().cancel("Flujo cancelado por el usuario");
            else AfkFarmClient.instance().start();
            minecraft.setScreen(null);
        }).bounds(left, generalActionsY, width, 20).build());
        flowButton.active = active;
    }

    private void initCommands(int left, int width) {
        var value = config().snapshot();
        int y = height < 230 ? 45 : 54;
        int gap = height < 230 ? 33 : 37;
        commands = field(left, y, width, "Comandos separados por ;", String.join("; ", value.commands()), 2048); y += gap;
        postJoin = field(left, y, width, "Espera al entrar al host (0–300 segundos)", Integer.toString(value.postJoinDelaySeconds()), 3); y += gap;
        betweenCommands = field(left, y, width, "Espera entre comandos (0–60 segundos)", Integer.toString(value.betweenCommandsDelaySeconds()), 2); y += gap;
        movementDelay = field(left, y, width, "Espera tras el último comando (0–300 segundos)", Integer.toString(value.movementStartDelaySeconds()), 3);
    }

    private void initMovement(int left, int width) {
        var value = config().snapshot();
        boolean compact = height < 230;
        int y = compact ? 45 : 54;
        int half = (width - 4) / 2;
        radius = field(left, y, half, "Radio final (0.25–32)", number(value.arrivalRadius()), 12);
        rotation = field(left + half + 4, y, width - half - 4, "Giro máximo (0.5–30°)", number(value.maxCameraRotationDegreesPerTick()), 12);
        y += compact ? 34 : 39;
        var activeRoute = value.routes().stream().filter(route -> route.name().equals(value.activeRoute())).findFirst().orElse(null);
        String routeLabel = activeRoute == null ? "Sin recorrido seleccionado" : activeRoute.name() + " · " + activeRoute.points().size() + " puntos";
        Button current = addRenderableWidget(Button.builder(Component.literal(trim(routeLabel, width)), button -> {})
                .bounds(left, y, width, 20).build());
        current.active = false;
        y += compact ? 23 : 24;
        routeName = field(left, y, width, "Nombre del nuevo recorrido", "Recorrido", 48); y += compact ? 34 : 37;
        boolean recording = AfkFarmClient.instance().recording();
        addRenderableWidget(Button.builder(Component.literal(recording ? "Detener y guardar grabación" : "Grabar recorrido caminando"), button -> {
            saveFields();
            if (recording) {
                AfkFarmClient.instance().stopRecording();
                minecraft.setScreen(new AfkFarmScreen(parent, Tab.MOVEMENT));
            } else if (AfkFarmClient.instance().startRecording(routeName.getValue())) minecraft.setScreen(null);
        }).bounds(left, y, width, 20).build()); y += compact ? 23 : 24;
        int third = (width - 8) / 3;
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹ Anterior"), button -> cycleRoute(-1))
                .bounds(left, y, third, 20).build());
        Button next = addRenderableWidget(Button.builder(Component.literal("Siguiente ›"), button -> cycleRoute(1))
                .bounds(left + third + 4, y, third, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Eliminar"), button -> deleteRoute())
                .bounds(left + (third + 4) * 2, y, width - (third + 4) * 2, 20).build());
        previous.active = next.active = value.routes().size() > 1;
        delete.active = activeRoute != null;
    }

    private void initAttack(int left, int width) {
        var value = config().snapshot();
        boolean compact = height < 240;
        int controlGap = compact ? 21 : 24;
        int sectionGap = compact ? 23 : 29;
        int y = compact ? 44 : 55;
        addAttackToggle(left, y, width, "Atacar mobs hostiles", value.attackHostileMobs(), true); y += controlGap;
        addRenderableWidget(Button.builder(Component.literal(trim("Elegir mobs · " + value.allowedHostileMobs().size() + " seleccionados", width)),
                button -> minecraft.setScreen(new EntitySelectionScreen(this, EntitySelectionScreen.Category.HOSTILE)))
                .bounds(left, y, width, 20).build()); y += sectionGap;
        addAttackToggle(left, y, width, "Atacar animales", value.attackAnimals(), false); y += controlGap;
        addRenderableWidget(Button.builder(Component.literal(trim("Elegir animales · " + value.allowedAnimals().size() + " seleccionados", width)),
                button -> minecraft.setScreen(new EntitySelectionScreen(this, EntitySelectionScreen.Category.ANIMAL)))
                .bounds(left, y, width, 20).build()); y += sectionGap;
        addToggle(left, y, width, "Atacar disguises artificiales", value.attackArtificialPlayers(),
                config()::setAttackArtificialPlayers);
        attackInfoY = y + 25;
    }

    private void addToggle(int x, int y, int width, String label, boolean enabled, java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(Component.literal(trim(toggleLabel(label, enabled), width)), button -> {
            setter.accept(!enabled);
            minecraft.setScreen(new AfkFarmScreen(parent, tab));
        }).bounds(x, y, width, 20).build());
    }

    private void addAttackToggle(int x, int y, int width, String label, boolean enabled, boolean hostile) {
        addRenderableWidget(Button.builder(Component.literal(trim(toggleLabel(label, enabled), width)), button -> {
            if (hostile) config().setAttackHostileMobs(!enabled); else config().setAttackAnimals(!enabled);
            Screen returnTo = new AfkFarmScreen(parent, Tab.ATTACK);
            if (!enabled) minecraft.setScreen(new EntitySelectionScreen(returnTo,
                    hostile ? EntitySelectionScreen.Category.HOSTILE : EntitySelectionScreen.Category.ANIMAL));
            else minecraft.setScreen(returnTo);
        }).bounds(x, y, width, 20).build());
    }

    private EditBox field(int x, int y, int width, String label, String value, int maxLength) {
        EditBox box = new EditBox(font, x, y + 11, width, 20, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value);
        addRenderableWidget(box);
        fields.add(new LabeledField(label, box));
        return box;
    }

    private void switchTab(Tab next) { saveFields(); minecraft.setScreen(new AfkFarmScreen(parent, next)); }

    private void saveFields() {
        AfkFarmConfig config = config();
        if (tab == Tab.COMMANDS && commands != null) {
            config.setCommands(split(commands.getValue(), ";"));
            config.setDelays(integer(postJoin, 10), integer(betweenCommands, 3), integer(movementDelay, 10));
        } else if (tab == Tab.MOVEMENT && radius != null) {
            var value = config.snapshot();
            config.setNavigation(value.targetX(), value.targetY(), value.targetZ(), decimal(radius, 1.5), decimal(rotation, 8));
        }
    }

    private void cycleRoute(int direction) {
        saveFields();
        var value = config().snapshot();
        if (value.routes().isEmpty()) return;
        int current = 0;
        for (int i = 0; i < value.routes().size(); i++) if (value.routes().get(i).name().equals(value.activeRoute())) current = i;
        config().selectRoute(value.routes().get(Math.floorMod(current + direction, value.routes().size())).name());
        minecraft.setScreen(new AfkFarmScreen(parent, Tab.MOVEMENT));
    }

    private void deleteRoute() {
        String selected = config().snapshot().activeRoute();
        if (!selected.isBlank()) config().deleteRoute(selected);
        minecraft.setScreen(new AfkFarmScreen(parent, Tab.MOVEMENT));
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) { graphics.fill(0, 0, width, height, 0xF00C1016); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int panelWidth = Math.min(450, Math.max(270, width - 10));
        int left = (width - panelWidth) / 2;
        graphics.fill(left, 5, left + panelWidth, height - 4, 0xD8141B23);
        graphics.fill(left, 5, left + panelWidth, 7, 0xFF20D9FF);
        graphics.drawCenteredString(font, title, width / 2, 9, 0xFFA8F3FF);
        for (LabeledField value : fields)
            graphics.drawString(font, value.label(), value.box().getX(), value.box().getY() - 9, 0xFFB7C3CC, false);
        super.render(graphics, mouseX, mouseY, delta);
        if (tab == Tab.GENERAL)
        {
            AfkUsageController usage = AfkUsageController.instance();
            long seconds = usage.remainingSeconds();
            if (flowButton != null && !AfkFarmClient.instance().active()) flowButton.active = usage.canStart();
            String balance = seconds < 0 ? usage.message() : "Tiempo AFK disponible: " + formatDuration(seconds);
            int color = seconds == 0 ? 0xFFFF7676 : seconds < 0 ? 0xFFFFC857 : 0xFF62E8C6;
            int balanceY = Math.min(height - 49, generalActionsY + 26);
            graphics.drawCenteredString(font, balance, width / 2, balanceY, color);
            if (balanceY + 12 < height - 28)
                graphics.drawCenteredString(font, "Los módulos y recorridos se guardan por separado.", width / 2, balanceY + 12, 0xFF8E9AA5);
        }
        else if (tab == Tab.ATTACK && attackInfoY + 10 < height - 28) {
            graphics.drawCenteredString(font, "Detección conservadora · nombres y skins no se utilizan", width / 2,
                    attackInfoY, 0xFFFFC857);
            graphics.drawCenteredString(font, "Jugadores y entidades ambiguas siempre quedan excluidos", width / 2,
                    attackInfoY + 11, 0xFF8E9AA5);
        }
    }

    @Override public void onClose() { saveFields(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private AfkFarmConfig config() { return AfkFarmConfig.get(minecraft.gameDirectory.toPath()); }
    private static List<String> split(String value, String separator) { return Arrays.stream(value.split(java.util.regex.Pattern.quote(separator))).map(String::trim).filter(v -> !v.isBlank()).toList(); }
    private static int integer(EditBox box, int fallback) { try { return Integer.parseInt(box.getValue().trim()); } catch (Exception ignored) { return fallback; } }
    private static double decimal(EditBox box, double fallback) { try { return Double.parseDouble(box.getValue().trim().replace(',', '.')); } catch (Exception ignored) { return fallback; } }
    private static String number(double value) { return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value); }
    private static String toggleLabel(String label, boolean enabled) { return label + ": " + (enabled ? "Activado" : "Desactivado"); }
    private static String formatDuration(long total) {
        long days = total / 86400, hours = total % 86400 / 3600, minutes = total % 3600 / 60, seconds = total % 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m " + seconds + "s";
    }
    private String trim(String value, int width) { return font.plainSubstrByWidth(value, Math.max(30, width - 12)); }
    private static String tabName(Tab tab) { return switch (tab) { case GENERAL -> "General"; case COMMANDS -> "Tiempos"; case MOVEMENT -> "Recorrido"; case ATTACK -> "Ataque"; }; }
}
