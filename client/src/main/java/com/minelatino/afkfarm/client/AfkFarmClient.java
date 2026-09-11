package com.minelatino.afkfarm.client;

import com.minelatino.afkfarm.AfkFarmConfig;
import com.minelatino.afkfarm.AfkFarmAttackPolicy;
import java.util.ArrayList;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;

/** Tick-driven AFK workflow. All Minecraft interaction happens on the client thread. */
public final class AfkFarmClient {
    /** Compile-time attack policy. It is intentionally absent from afk-farm.json and the backend. */
    public static final double ATTACK_SEARCH_RADIUS = 4.5;
    private static final int WORLD_READY_TICKS = 20;
    private static final int TRANSFER_SCREEN_GRACE_TICKS = 200;
    private static final double ROUTE_RECORDING_STEP = 0.35;
    private static final double WAYPOINT_RADIUS = 0.65;
    private static final String AUTHORIZED_SERVER = "play.minelatino.com";
    private static final AfkFarmClient INSTANCE = new AfkFarmClient();

    private enum State { IDLE, WAITING_WORLD, WAITING_COMMAND, WAITING_MOVEMENT, MOVING, ATTACKING, COMPLETE }

    private State state = State.IDLE;
    private boolean active;
    private boolean driving;
    private boolean recording;
    private String recordingName = "";
    private final List<AfkFarmConfig.RoutePoint> recordedPoints = new ArrayList<>();
    private long lastRecordedTick;
    private int readyTicks;
    private Object observedLevel;
    private State suspendedState;
    private long suspendedRemainingTicks;
    private int transferScreenGrace;
    private boolean sequenceStarted;
    private long ticks;
    private long deadline;
    private long lastAttackTick = Long.MIN_VALUE / 2;
    private int commandIndex;
    private int routeIndex;
    private String status = "";

    public static AfkFarmClient instance() { return INSTANCE; }
    public boolean active() { return active; }
    public boolean recording() { return recording; }
    public int recordedPointCount() { return recordedPoints.size(); }
    public String status() { return status; }

    public void start() {
        if (recording) stopRecording();
        active = true;
        state = State.WAITING_WORLD;
        sequenceStarted = false;
        suspendedState = null;
        readyTicks = 0;
        observedLevel = Minecraft.getInstance().level;
        status = "Esperando que el mundo termine de cargar";
    }

    public boolean startRecording(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!worldReady(minecraft)) { status = "Entra a un servidor antes de grabar"; return false; }
        cancel(null);
        recording = true;
        recordingName = name == null || name.isBlank() ? "Recorrido " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM HH-mm")) : name.trim();
        recordedPoints.clear();
        lastRecordedTick = Long.MIN_VALUE / 2;
        recordPoint(minecraft, true);
        status = "Grabando recorrido · 1 punto";
        return true;
    }

    public boolean stopRecording() {
        if (!recording) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (worldReady(minecraft)) recordPoint(minecraft, true);
        recording = false;
        if (recordedPoints.size() < 2) {
            status = "Recorrido descartado: camina antes de guardarlo";
            recordedPoints.clear();
            return false;
        }
        AfkFarmConfig.get(minecraft.gameDirectory.toPath()).saveRoute(recordingName, List.copyOf(recordedPoints));
        status = "Recorrido guardado · " + recordedPoints.size() + " puntos";
        return true;
    }

    public void cancel(String reason) {
        releaseControls();
        active = false;
        state = State.IDLE;
        sequenceStarted = false;
        suspendedState = null;
        status = reason == null ? "" : reason;
    }

    public void tick() {
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        AutoReconnect.tick();
        AutoReconnect.remember(minecraft.getCurrentServer());

        if (recording) {
            if (worldReady(minecraft)) {
                recordPoint(minecraft, false);
                status = "Grabando recorrido · " + recordedPoints.size() + " puntos";
            } else status = "Grabación pausada durante el cambio de servidor";
            return;
        }

        if (minecraft.level != observedLevel) {
            if (active && sequenceStarted && state != State.WAITING_WORLD) suspendForTransfer();
            observedLevel = minecraft.level;
            readyTicks = 0;
            transferScreenGrace = TRANSFER_SCREEN_GRACE_TICKS;
        }

        if (!worldReady(minecraft)) {
            readyTicks = 0;
            releaseControls();
            if (active) {
                if (sequenceStarted && state != State.WAITING_WORLD) suspendForTransfer();
                state = State.WAITING_WORLD;
                status = "Cambio de host detectado · esperando el nuevo mundo";
            }
            return;
        }

        readyTicks++;
        if (readyTicks == WORLD_READY_TICKS) {
            AutoReconnect.connected();
            if (active && state == State.WAITING_WORLD) {
                if (suspendedState != null) resumeAfterTransfer();
                else beginSequence();
            }
        }
        if (!active || readyTicks < WORLD_READY_TICKS) return;

        if (transferScreenGrace > 0) transferScreenGrace--;
        if (minecraft.screen != null && !(minecraft.screen instanceof AfkFarmScreen)
                && !(minecraft.screen instanceof EntitySelectionScreen)
                && transferScreenGrace <= 0 && !isTransferScreen(minecraft.screen)) {
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
        sequenceStarted = true;
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
        routeIndex = 0;
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
        AfkFarmConfig.SavedRoute route = selectedRoute(config);
        if (route == null || route.points().size() < 2) {
            releaseControls();
            cancel("Selecciona o graba un recorrido antes de caminar");
            return;
        }
        routeIndex = Math.min(routeIndex, route.points().size() - 1);
        AfkFarmConfig.RoutePoint point = route.points().get(routeIndex);
        double dx = point.x() - minecraft.player.getX();
        double dy = point.y() - minecraft.player.getY();
        double dz = point.z() - minecraft.player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double acceptedRadius = routeIndex == route.points().size() - 1 ? config.arrivalRadius() : WAYPOINT_RADIUS;
        if (distance <= acceptedRadius) {
            routeIndex++;
            if (routeIndex >= route.points().size()) {
                releaseControls();
                beginAttackOrComplete(config);
            }
            return;
        }

        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        minecraft.player.setYRot(approachAngle(minecraft.player.getYRot(), targetYaw,
                (float)config.maxCameraRotationDegreesPerTick()));
        minecraft.player.setYHeadRot(minecraft.player.getYRot());
        driving = true;
        minecraft.options.keyUp.setDown(true);
        minecraft.options.keyJump.setDown(minecraft.player.horizontalCollision || dy > 0.45);
        status = String.format(Locale.ROOT, "Recorrido %s · punto %d/%d · %.1f bloques",
                route.name(), routeIndex + 1, route.points().size(), distance);
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
        double attackRange = minecraft.player.entityInteractionRange();
        if (minecraft.player.distanceToSqr(target) > attackRange * attackRange) {
            status = "Objetivo permitido fuera del alcance real";
            return;
        }
        if (!AfkFarmAttackPolicy.mayAttempt(ticks, lastAttackTick,
                minecraft.player.getAttackStrengthScale(0f))) return;
        if (minecraft.gameMode == null) return;
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
        if (entity instanceof Enemy) return AfkFarmAttackPolicy.allowsId(
                config.attackHostileMobs(), config.allowedHostileMobs(), id);
        if (entity instanceof Animal animal) {
            if (animal instanceof TamableAnimal tameable && tameable.isTame()) return false;
            return AfkFarmAttackPolicy.allowsId(config.attackAnimals(), config.allowedAnimals(), id);
        }
        return false;
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

    private void recordPoint(Minecraft minecraft, boolean force) {
        AfkFarmConfig.RoutePoint next = new AfkFarmConfig.RoutePoint(
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        AfkFarmConfig.RoutePoint previous = recordedPoints.isEmpty() ? null : recordedPoints.getLast();
        double distance = previous == null ? Double.MAX_VALUE : Math.sqrt(distanceSquared(previous, next));
        if (force || (ticks - lastRecordedTick >= 2 && distance >= ROUTE_RECORDING_STEP)) {
            if (previous == null || distance >= 0.05) recordedPoints.add(next);
            lastRecordedTick = ticks;
        }
    }

    private void suspendForTransfer() {
        releaseControls();
        suspendedState = state;
        suspendedRemainingTicks = Math.max(0, deadline - ticks);
        state = State.WAITING_WORLD;
        transferScreenGrace = TRANSFER_SCREEN_GRACE_TICKS;
    }

    private void resumeAfterTransfer() {
        state = suspendedState;
        suspendedState = null;
        deadline = ticks + suspendedRemainingTicks;
        status = switch (state) {
            case WAITING_COMMAND -> "Host conectado · continuando comandos";
            case WAITING_MOVEMENT -> "Host conectado · continuando espera de movimiento";
            case MOVING -> "Host conectado · retomando recorrido";
            case ATTACKING -> "Host conectado · retomando búsqueda de objetivos";
            default -> "Host conectado · retomando flujo AFK";
        };
    }

    private static boolean isTransferScreen(net.minecraft.client.gui.screens.Screen screen) {
        String name = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("receiving") || name.contains("progress") || name.contains("connect")
                || name.contains("message") || name.contains("downloadterrain");
    }

    private static AfkFarmConfig.SavedRoute selectedRoute(AfkFarmConfig.Snapshot config) {
        return config.routes().stream().filter(route -> route.name().equals(config.activeRoute())).findFirst().orElse(null);
    }

    private static double distanceSquared(AfkFarmConfig.RoutePoint a, AfkFarmConfig.RoutePoint b) {
        double x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
        return x * x + y * y + z * z;
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
