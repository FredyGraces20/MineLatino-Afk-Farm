package com.minelatino.afkfarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
