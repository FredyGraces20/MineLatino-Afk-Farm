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

/** Tabbed configuration screen; keeps the independent modules visually separate. */
public final class AfkFarmScreen extends Screen {
    private enum Tab { GENERAL, COMMANDS, MOVEMENT, ATTACK }
    private record LabeledField(String label, EditBox box) {}

    private final Screen parent;
    private final Tab tab;
    private final List<LabeledField> fields = new ArrayList<>();
    private EditBox commands;
    private EditBox postJoin;
    private EditBox betweenCommands;
    private EditBox movementDelay;
    private EditBox targetX;
    private EditBox targetY;
    private EditBox targetZ;
    private EditBox radius;
    private EditBox rotation;
    private EditBox hostileIds;
    private EditBox animalIds;

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
            addRenderableWidget(Button.builder(Component.literal(tabName(value)), button -> switchTab(value))
                    .bounds(left + i * (tabWidth + 3), 24, tabWidth, 20).build());
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
        AfkFarmConfig config = config();
        var value = config.snapshot();
        int y = 57;
        addToggle(left, y, width, "Reconexión automática", value.autoReconnect(), config::setAutoReconnect); y += 25;
        addToggle(left, y, width, "Módulo de comandos", value.commandsEnabled(), config::setCommandsEnabled); y += 25;
        addToggle(left, y, width, "Módulo de movimiento", value.navigationEnabled(), config::setNavigationEnabled); y += 25;
        addToggle(left, y, width, "Módulo de ataque", value.autoAttackEnabled(), config::setAutoAttackEnabled); y += 30;
        boolean active = AfkFarmClient.instance().active();
        addRenderableWidget(Button.builder(Component.literal(active ? "Detener flujo AFK" : "Iniciar flujo AFK"), button -> {
            saveFields();
            if (active) AfkFarmClient.instance().cancel("Flujo cancelado por el usuario");
            else AfkFarmClient.instance().start();
            // Return to gameplay. Returning to the pause screen would immediately
            // trigger the intentional "screen changed" cancellation guard.
            minecraft.setScreen(null);
        }).bounds(left, Math.min(y, height - 52), width, 20).build());
    }

    private void initCommands(int left, int width) {
        var value = config().snapshot();
        int y = 62;
        commands = field(left, y, width, "Comandos separados por ;", String.join("; ", value.commands()), 2048); y += 39;
        int third = (width - 8) / 3;
        postJoin = field(left, y, third, "Al entrar (0–300 s)", Integer.toString(value.postJoinDelaySeconds()), 3);
        betweenCommands = field(left + third + 4, y, third, "Entre comandos (0–60 s)",
                Integer.toString(value.betweenCommandsDelaySeconds()), 2);
        movementDelay = field(left + (third + 4) * 2, y, width - (third + 4) * 2,
                "Antes de caminar (0–300 s)", Integer.toString(value.movementStartDelaySeconds()), 3);
    }

    private void initMovement(int left, int width) {
        var value = config().snapshot();
        int y = 62;
        int third = (width - 8) / 3;
        targetX = field(left, y, third, "Destino X", number(value.targetX()), 24);
        targetY = field(left + third + 4, y, third, "Destino Y", number(value.targetY()), 24);
        targetZ = field(left + (third + 4) * 2, y, width - (third + 4) * 2, "Destino Z", number(value.targetZ()), 24);
        y += 42;
        int half = (width - 4) / 2;
        radius = field(left, y, half, "Radio de llegada (0.25–32)", number(value.arrivalRadius()), 12);
        rotation = field(left + half + 4, y, width - half - 4, "Giro máximo por tick (0.5–30°)",
                number(value.maxCameraRotationDegreesPerTick()), 12);
    }

    private void initAttack(int left, int width) {
        AfkFarmConfig config = config();
        var value = config.snapshot();
        int y = 57;
        addToggle(left, y, width, "Atacar mobs hostiles", value.attackHostileMobs(), config::setAttackHostileMobs); y += 29;
        hostileIds = field(left, y, width, "Hostiles permitidos (IDs separados por coma)",
                String.join(", ", value.allowedHostileMobs()), 2048); y += 42;
        addToggle(left, y, width, "Atacar animales", value.attackAnimals(), config::setAttackAnimals); y += 29;
        animalIds = field(left, y, width, "Animales permitidos (ej. minecraft:cow)",
                String.join(", ", value.allowedAnimals()), 2048);
    }

    private void addToggle(int x, int y, int width, String label, boolean enabled,
                           java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (enabled ? "Activado" : "Desactivado")), button -> {
            setter.accept(!enabled);
            minecraft.setScreen(new AfkFarmScreen(parent, tab));
        }).bounds(x, y, width, 20).build());
    }

    private EditBox field(int x, int y, int width, String label, String value, int maxLength) {
        EditBox box = new EditBox(font, x, y + 12, width, 20, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value);
        addRenderableWidget(box);
        fields.add(new LabeledField(label, box));
        return box;
    }

    private void switchTab(Tab next) {
        saveFields();
        minecraft.setScreen(new AfkFarmScreen(parent, next));
    }

    private void saveFields() {
        AfkFarmConfig config = config();
        if (tab == Tab.COMMANDS && commands != null) {
            config.setCommands(split(commands.getValue(), ";"));
            config.setDelays(integer(postJoin, 10), integer(betweenCommands, 3), integer(movementDelay, 10));
        } else if (tab == Tab.MOVEMENT && targetX != null) {
            config.setNavigation(decimal(targetX, 0), decimal(targetY, 64), decimal(targetZ, 0),
                    decimal(radius, 1.5), decimal(rotation, 8));
        } else if (tab == Tab.ATTACK) {
            config.setAllowedEntities(split(hostileIds == null ? "" : hostileIds.getValue(), ","),
                    split(animalIds == null ? "" : animalIds.getValue(), ","));
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF00C1016);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int panelWidth = Math.min(450, Math.max(270, width - 10));
        int left = (width - panelWidth) / 2;
        graphics.fill(left, 5, left + panelWidth, height - 4, 0xD8141B23);
        graphics.fill(left, 5, left + panelWidth, 7, 0xFF20D9FF);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 9, 0xFFA8F3FF);
        for (LabeledField value : fields) {
            graphics.drawString(font, value.label(), value.box().getX(), value.box().getY() - 10, 0xFFB7C3CC, false);
        }
        if (tab == Tab.GENERAL) {
            graphics.drawCenteredString(font, "Cada módulo funciona de forma independiente.", width / 2,
                    Math.min(height - 42, 172), 0xFF8E9AA5);
        } else if (tab == Tab.ATTACK) {
            graphics.drawCenteredString(font, "Solo MineLatino · cooldown real · máximo interno 4 intentos/s",
                    width / 2, Math.min(height - 42, 188), 0xFFFFC857);
        }
    }

    @Override public void onClose() {
        saveFields();
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }

    private AfkFarmConfig config() { return AfkFarmConfig.get(minecraft.gameDirectory.toPath()); }
    private static List<String> split(String value, String separator) {
        return Arrays.stream(value.split(java.util.regex.Pattern.quote(separator))).map(String::trim).filter(v -> !v.isBlank()).toList();
    }
    private static int integer(EditBox box, int fallback) {
        try { return Integer.parseInt(box.getValue().trim()); } catch (Exception ignored) { return fallback; }
    }
    private static double decimal(EditBox box, double fallback) {
        try { return Double.parseDouble(box.getValue().trim().replace(',', '.')); } catch (Exception ignored) { return fallback; }
    }
    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }
    private static String tabName(Tab tab) {
        return switch (tab) {
            case GENERAL -> "General";
            case COMMANDS -> "Comandos";
            case MOVEMENT -> "Movimiento";
            case ATTACK -> "Ataque";
        };
    }
}
