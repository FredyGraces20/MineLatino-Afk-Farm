package com.minelatino.afkfarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AfkFarmConfigTest {
    @TempDir Path directory;

    @Test void defaultsAreSafeAndDisabled() {
        var value = AfkFarmConfig.get(directory).snapshot();
        assertFalse(value.autoReconnect());
        assertFalse(value.commandsEnabled());
        assertFalse(value.navigationEnabled());
        assertFalse(value.autoAttackEnabled());
        assertTrue(value.commands().isEmpty());
        assertTrue(value.activeRoute().isBlank());
        assertTrue(value.routes().isEmpty());
    }

    @Test void clampsDelaysCommandsAndAttackLists() {
        var config = AfkFarmConfig.get(directory.resolve("clamps"));
        config.setDelays(-1, 90, 999);
        config.setCommands(List.of("/warp granja", " ", "/home"));
        config.setAllowedEntities(List.of("minecraft:zombie", "INVALID", "minecraft:zombie"),
                List.of("minecraft:cow"));
        var value = config.snapshot();
        assertEquals(0, value.postJoinDelaySeconds());
        assertEquals(60, value.betweenCommandsDelaySeconds());
        assertEquals(300, value.movementStartDelaySeconds());
        assertEquals(List.of("warp granja", "home"), value.commands());
        assertEquals(List.of("minecraft:zombie"), value.allowedHostileMobs());
    }

    @Test void damagedJsonReturnsSafeConfiguration() throws Exception {
        Path game = directory.resolve("damaged");
        Path file = game.resolve("config/minelatino-afk-farm/afk-farm.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not-json");
        var value = AfkFarmConfig.get(game).snapshot();
        assertFalse(value.autoReconnect());
        assertFalse(value.autoAttackEnabled());
    }

    @Test void savesSelectsAndDeletesRecordedRoutes() {
        var config = AfkFarmConfig.get(directory.resolve("routes"));
        config.saveRoute("Granja", List.of(
                new AfkFarmConfig.RoutePoint(1, 64, 2),
                new AfkFarmConfig.RoutePoint(1.5, 64, 2.5),
                new AfkFarmConfig.RoutePoint(2, 64, 3)));
        config.saveRoute("Animales", List.of(
                new AfkFarmConfig.RoutePoint(10, 70, 10),
                new AfkFarmConfig.RoutePoint(11, 70, 10)));

        var saved = config.snapshot();
        assertEquals("Animales", saved.activeRoute());
        assertEquals(2, saved.routes().size());
        config.selectRoute("Granja");
        assertEquals("Granja", config.snapshot().activeRoute());
        config.deleteRoute("Granja");
        assertEquals("Animales", config.snapshot().activeRoute());
        assertEquals(1, config.snapshot().routes().size());
    }

    @Test void rejectsUnusableRecordedRoutes() {
        var config = AfkFarmConfig.get(directory.resolve("invalid-routes"));
        assertThrows(IllegalArgumentException.class, () -> config.saveRoute("Solo un punto",
                List.of(new AfkFarmConfig.RoutePoint(1, 2, 3))));
        assertThrows(IllegalArgumentException.class, () -> config.saveRoute("No finito", List.of(
                new AfkFarmConfig.RoutePoint(Double.NaN, 2, 3),
                new AfkFarmConfig.RoutePoint(4, 5, 6))));
        assertTrue(config.snapshot().routes().isEmpty());
    }
}
