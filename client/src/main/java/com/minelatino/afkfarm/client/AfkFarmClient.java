package com.minelatino.afkfarm.client;

import com.minelatino.afkfarm.AfkFarmConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;

/** Tick-driven AFK workflow. All Minecraft interaction happens on the client thread. */
public final class AfkFarmClient {
    /** Compile-time attack policy. It is intentionally absent from afk-farm.json and the backend. */
    public static final int MINIMUM_ATTACK_INTERVAL_TICKS = 5; // 20 ticks / 5 = at most 4 attempts per second.
    public static final float REQUIRED_ATTACK_STRENGTH = 0.95f;
    public static final double ATTACK_SEARCH_RADIUS = 4.5;
    private static final int WORLD_READY_TICKS = 20;
    private static final String AUTHORIZED_SERVER = "play.minelatino.com";
    private static final AfkFarmClient INSTANCE = new AfkFarmClient();

    private enum State { IDLE, WAITING_WORLD, WAITING_COMMAND, WAITING_MOVEMENT, MOVING, ATTACKING, COMPLETE }

    private State state = State.IDLE;
    private boolean active;
    private boolean driving;
    private int readyTicks;
    private long ticks;
    private long deadline;
    private long lastAttackTick = Long.MIN_VALUE / 2;
    private int commandIndex;
    private String status = "";

    public static AfkFarmClient instance() { return INSTANCE; }
    public boolean active() { return active; }
    public String status() { return status; }

    public void start() {
        active = true;
        state = State.WAITING_WORLD;
        status = "Esperando que el mundo termine de cargar";
        Minecraft minecraft = Minecraft.getInstance();
        if (worldReady(minecraft)) beginSequence();
    }

    public void cancel(String reason) {
        releaseControls();
        active = false;
        state = State.IDLE;
        status = reason == null ? "" : reason;
    }

    public void tick() {
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        AutoReconnect.tick();
        AutoReconnect.remember(minecraft.getCurrentServer());

        if (!worldReady(minecraft)) {
            readyTicks = 0;
            releaseControls();
            if (active) {
                state = State.WAITING_WORLD;
                status = "Esperando que el mundo termine de cargar";
            }
            return;
        }

        readyTicks++;
        if (readyTicks == WORLD_READY_TICKS) {
            AutoReconnect.connected();
            if (active && state == State.WAITING_WORLD) beginSequence();
        }
        if (!active || readyTicks < WORLD_READY_TICKS) return;

        if (minecraft.screen != null && !(minecraft.screen instanceof AfkFarmScreen)) {
            cancel("Secuencia detenida al cambiar de pantalla");
            return;
        }
        if (manualMovement(minecraft)) {
            cancel("Secuencia detenida por movimiento manual");
            return;
        }

        switch (state) {
            case WAITING_COMMAND -> tickCommands(minecraft);
            case WAITING_MOVEMENT -> tickMovementDelay();
            case MOVING -> tickMovement(minecraft);
            case ATTACKING -> tickAttack(minecraft);
            default -> {}
        }
    }

    private void beginSequence() {
        AfkFarmConfig.Snapshot config = config();
        commandIndex = 0;
        if (config.commandsEnabled() && !config.commands().isEmpty()) {
            state = State.WAITING_COMMAND;
            deadline = ticks + seconds(config.postJoinDelaySeconds());
            updateCommandStatus(config);
        } else {
            beginMovementDelay(config);
        }
    }

    private void tickCommands(Minecraft minecraft) {
        AfkFarmConfig.Snapshot config = config();
        if (!config.commandsEnabled() || config.commands().isEmpty() || commandIndex >= config.commands().size()) {
            beginMovementDelay(config);
            return;
        }
        if (ticks < deadline) {
            updateCommandStatus(config);
            return;
        }
        // This state is reached only after player, level and connection stayed ready for WORLD_READY_TICKS.
        minecraft.player.connection.sendCommand(config.commands().get(commandIndex));
        commandIndex++;
        if (commandIndex < config.commands().size()) {
            deadline = ticks + seconds(config.betweenCommandsDelaySeconds());
            updateCommandStatus(config);
        } else {
            beginMovementDelay(config);
        }
    }

    private void updateCommandStatus(AfkFarmConfig.Snapshot config) {
        String command = commandIndex < config.commands().size() ? config.commands().get(commandIndex) : "";
        status = "Ejecutando /" + command + " en " + remainingSeconds() + " segundos";
    }

    private void beginMovementDelay(AfkFarmConfig.Snapshot config) {
        state = State.WAITING_MOVEMENT;
        deadline = ticks + seconds(config.movementStartDelaySeconds());
        status = "Esperando teletransporte · comenzando movimiento en " + remainingSeconds() + " segundos";
    }

    private void tickMovementDelay() {
        AfkFarmConfig.Snapshot config = config();
        if (ticks < deadline) {
            status = "Comenzando movimiento en " + remainingSeconds() + " segundos";
            return;
        }
        if (config.navigationEnabled()) {
            state = State.MOVING;
            status = "Caminando hacia el destino";
        } else {
            beginAttackOrComplete(config);
        }
    }

    private void tickMovement(Minecraft minecraft) {
        AfkFarmConfig.Snapshot config = config();
        if (!config.navigationEnabled()) {
            releaseControls();
            beginAttackOrComplete(config);
            return;
        }
        double dx = config.targetX() - minecraft.player.getX();
        double dy = config.targetY() - minecraft.player.getY();
        double dz = config.targetZ() - minecraft.player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= config.arrivalRadius()) {
            releaseControls();
            beginAttackOrComplete(config);
            return;
        }

        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        minecraft.player.setYRot(approachAngle(minecraft.player.getYRot(), targetYaw,
                (float)config.maxCameraRotationDegreesPerTick()));
        minecraft.player.setYHeadRot(minecraft.player.getYRot());
        driving = true;
        minecraft.options.keyUp.setDown(true);
        minecraft.options.keyJump.setDown(minecraft.player.horizontalCollision);
        status = String.format(Locale.ROOT, "Caminando · faltan %.1f bloques", distance);
    }

    private void beginAttackOrComplete(AfkFarmConfig.Snapshot config) {
        if (!config.autoAttackEnabled()) {
            state = State.COMPLETE;
            active = false;
            status = "Destino alcanzado";
            return;
        }
        if (!attackAuthorized(Minecraft.getInstance())) {
            state = State.COMPLETE;
            active = false;
            status = "Ataque bloqueado: servidor no autorizado";
            return;
        }
        state = State.ATTACKING;
        status = "Buscando objetivos permitidos";
    }

    private void tickAttack(Minecraft minecraft) {
        AfkFarmConfig.Snapshot config = config();
        if (!config.autoAttackEnabled() || !attackAuthorized(minecraft)) {
            state = State.COMPLETE;
            active = false;
            status = "Ataque automático detenido";
            return;
        }
        LivingEntity target = nearestTarget(minecraft, config);
        if (target == null) {
            status = "Sin objetivos permitidos cerca";
            return;
        }
        rotateToward(minecraft, target, (float)config.maxCameraRotationDegreesPerTick());
        status = "Objetivo: " + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (ticks - lastAttackTick < MINIMUM_ATTACK_INTERVAL_TICKS) return;
        if (minecraft.player.getAttackStrengthScale(0f) < REQUIRED_ATTACK_STRENGTH) return;
        minecraft.gameMode.attack(minecraft.player, target);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        lastAttackTick = ticks;
    }

    private LivingEntity nearestTarget(Minecraft minecraft, AfkFarmConfig.Snapshot config) {
        List<Entity> entities = minecraft.level.getEntities(minecraft.player,
                minecraft.player.getBoundingBox().inflate(ATTACK_SEARCH_RADIUS), entity ->
                        entity instanceof LivingEntity living && living.isAlive() && entity != minecraft.player
                                && minecraft.player.hasLineOfSight(entity) && allowed(living, config));
        return entities.stream().map(entity -> (LivingEntity)entity)
                .min(Comparator.comparingDouble(minecraft.player::distanceToSqr)).orElse(null);
    }

    private boolean allowed(LivingEntity entity, AfkFarmConfig.Snapshot config) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (entity instanceof Enemy) return config.attackHostileMobs() && contains(config.allowedHostileMobs(), id);
        if (entity instanceof Animal) return config.attackAnimals() && contains(config.allowedAnimals(), id);
        return false;
    }

    private static boolean contains(List<String> ids, String id) {
        return ids.contains("*") || ids.contains(id);
    }

    private void rotateToward(Minecraft minecraft, LivingEntity target, float maximum) {
        double dx = target.getX() - minecraft.player.getX();
        double dz = target.getZ() - minecraft.player.getZ();
        double eye = target.getEyeY() - minecraft.player.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)-Math.toDegrees(Math.atan2(eye, horizontal));
        minecraft.player.setYRot(approachAngle(minecraft.player.getYRot(), yaw, maximum));
        minecraft.player.setXRot(approachAngle(minecraft.player.getXRot(), pitch, maximum));
        minecraft.player.setYHeadRot(minecraft.player.getYRot());
    }

    private boolean manualMovement(Minecraft minecraft) {
        if (driving) {
            return minecraft.options.keyDown.isDown() || minecraft.options.keyLeft.isDown()
                    || minecraft.options.keyRight.isDown() || minecraft.options.keyShift.isDown();
        }
        return minecraft.options.keyUp.isDown() || minecraft.options.keyDown.isDown()
                || minecraft.options.keyLeft.isDown() || minecraft.options.keyRight.isDown()
                || minecraft.options.keyJump.isDown() || minecraft.options.keyShift.isDown();
    }

    private void releaseControls() {
        if (!driving) return;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyJump.setDown(false);
        driving = false;
    }

    private static boolean attackAuthorized(Minecraft minecraft) {
        if (minecraft.getCurrentServer() == null || minecraft.getCurrentServer().ip == null) return false;
        String address = minecraft.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            address = end >= 0 ? address.substring(1, end) : address;
        } else {
            int colon = address.lastIndexOf(':');
            if (colon > 0) address = address.substring(0, colon);
        }
        return AUTHORIZED_SERVER.equals(address);
    }

    private AfkFarmConfig.Snapshot config() {
        return AfkFarmConfig.get(Minecraft.getInstance().gameDirectory.toPath()).snapshot();
    }

    private long remainingSeconds() { return Math.max(0, (deadline - ticks + 19) / 20); }
    private static long seconds(int value) { return Math.max(0, value) * 20L; }
    private static boolean worldReady(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null && minecraft.player.connection != null;
    }
    private static float approachAngle(float current, float target, float maximum) {
        return current + Mth.clamp(Mth.wrapDegrees(target - current), -maximum, maximum);
    }
}
