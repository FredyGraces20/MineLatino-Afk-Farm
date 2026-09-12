package com.minelatino.afkfarm.client;

import com.minelatino.afkfarm.AfkFarmConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;

/** Runtime entity picker. Only living hostile mobs and animals that can be created by this client are shown. */
public final class EntitySelectionScreen extends Screen {
    public enum Category { HOSTILE, ANIMAL }
    private record Option(String id, String label) {}

    private final Screen parent;
    private final Category category;
    private final Set<String> selected = new LinkedHashSet<>();
    private List<Option> options = List.of();
    private int page;

    public EntitySelectionScreen(Screen parent, Category category) {
        super(Component.literal(category == Category.HOSTILE ? "Seleccionar mobs hostiles" : "Seleccionar animales"));
        this.parent = parent;
        this.category = category;
    }

    @Override protected void init() {
        var snapshot = config().snapshot();
        selected.clear();
        selected.addAll(category == Category.HOSTILE ? snapshot.allowedHostileMobs() : snapshot.allowedAnimals());
        options = discover();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int panelWidth = Math.min(520, Math.max(250, width - 16));
        int left = (width - panelWidth) / 2;
        int rows = Math.max(1, Math.min(9, (height - 106) / 23));
        int perPage = rows * 2;
        int pages = Math.max(1, (options.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int gap = 5, columnWidth = (panelWidth - gap) / 2;
        int start = page * perPage;
        for (int i = start; i < Math.min(options.size(), start + perPage); i++) {
            Option option = options.get(i);
            int local = i - start;
            int x = left + (local % 2) * (columnWidth + gap);
            int y = 52 + (local / 2) * 23;
            Button[] holder = new Button[1];
            holder[0] = Button.builder(label(option, columnWidth), button -> {
                if (!selected.remove(option.id())) selected.add(option.id());
                saveSelection();
                button.setMessage(label(option, columnWidth));
            }).bounds(x, y, columnWidth, 20).build();
            addRenderableWidget(holder[0]);
        }
        int footerY = height - 49;
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> { page--; rebuild(); })
                .bounds(left, footerY, 36, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("Página " + (page + 1) + "/" + pages), button -> {})
                .bounds(left + 40, footerY, panelWidth - 80, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal("›"), button -> { page++; rebuild(); })
                .bounds(left + panelWidth - 36, footerY, 36, 20).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.literal("Guardar y volver"), button -> onClose())
                .bounds(left, height - 25, panelWidth, 20).build());
    }

    private Component label(Option option, int buttonWidth) {
        String prefix = selected.contains(option.id()) ? "✓ " : "";
        return Component.literal(font.plainSubstrByWidth(prefix + option.label(), Math.max(20, buttonWidth - 12)));
    }

    private List<Option> discover() {
        if (minecraft.level == null) return List.of();
        List<Option> result = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Entity entity = null;
            try {
                entity = type.create(minecraft.level, EntitySpawnReason.COMMAND);
                boolean compatible = category == Category.HOSTILE ? entity instanceof Enemy : entity instanceof Animal;
                if (!compatible) continue;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                result.add(new Option(id, Component.translatable(type.getDescriptionId()).getString()));
            } catch (Throwable ignored) {
                // Factories requiring server-only data are intentionally not offered by the client mod.
            } finally {
                if (entity != null) entity.discard();
            }
        }
        return result.stream().distinct().sorted(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private void saveSelection() {
        AfkFarmConfig config = config();
        var snapshot = config.snapshot();
        if (category == Category.HOSTILE) config.setAllowedEntities(List.copyOf(selected), snapshot.allowedAnimals());
        else config.setAllowedEntities(snapshot.allowedHostileMobs(), List.copyOf(selected));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF00C1016);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        int panelWidth = Math.min(540, Math.max(270, width - 8));
        int left = (width - panelWidth) / 2;
        graphics.fill(left, 4, left + panelWidth, height - 3, 0xE0141B23);
        graphics.fill(left, 4, left + panelWidth, 6, 0xFF20D9FF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, 0xFFA8F3FF);
        graphics.centeredText(font, selected.size() + " seleccionados · los jugadores nunca aparecen",
                width / 2, 28, 0xFF8E9AA5);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private AfkFarmConfig config() { return AfkFarmConfig.get(minecraft.gameDirectory.toPath()); }
}
