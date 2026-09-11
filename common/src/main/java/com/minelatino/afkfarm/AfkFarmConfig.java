package com.minelatino.afkfarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local settings for the AFK workflow. Nothing in this file controls attack frequency. */
public final class AfkFarmConfig {
    public static final int MAX_COMMANDS = 10;
    public static final int MAX_ALLOWED_ENTITIES = 64;
    public static final int MAX_ROUTES = 20;
    public static final int MAX_ROUTE_POINTS = 8192;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AfkFarmConfig current;

    private static final class Data {
        int version = 4;
        boolean autoReconnect = false;
        boolean commandsEnabled = false;
        List<String> commands = new ArrayList<>();
        int postJoinDelaySeconds = 10;
        int betweenCommandsDelaySeconds = 3;
        int movementStartDelaySeconds = 10;
        double targetX = 0;
        double targetY = 64;
        double targetZ = 0;
        double arrivalRadius = 1.5;
        boolean navigationEnabled = false;
        boolean autoAttackEnabled = false;
        boolean attackHostileMobs = false;
        boolean attackAnimals = false;
        boolean attackArtificialPlayers = false;
        List<String> allowedHostileMobs = new ArrayList<>();
        List<String> allowedAnimals = new ArrayList<>();
        double maxCameraRotationDegreesPerTick = 8.0;
        String activeRoute = "";
        List<SavedRoute> routes = new ArrayList<>();
    }

    public record RoutePoint(double x, double y, double z) {}
    public record SavedRoute(String name, List<RoutePoint> points) {}

    public record Snapshot(
            boolean autoReconnect,
            boolean commandsEnabled,
            List<String> commands,
            int postJoinDelaySeconds,
            int betweenCommandsDelaySeconds,
            int movementStartDelaySeconds,
            double targetX,
            double targetY,
            double targetZ,
            double arrivalRadius,
            boolean navigationEnabled,
            boolean autoAttackEnabled,
            boolean attackHostileMobs,
            boolean attackAnimals,
            boolean attackArtificialPlayers,
            List<String> allowedHostileMobs,
            List<String> allowedAnimals,
            double maxCameraRotationDegreesPerTick,
            String activeRoute,
            List<SavedRoute> routes) {}

    private final Path path;
    private Data data;

    private AfkFarmConfig(Path path, Data data) {
        this.path = path;
        this.data = normalize(data == null ? new Data() : data);
    }

    public static synchronized AfkFarmConfig get(Path gameDirectory) {
        Path path = gameDirectory.resolve("config/minelatino-afk-farm/afk-farm.json");
        if (current != null && current.path.equals(path)) return current;
        Data loaded = null;
        try {
            if (Files.isRegularFile(path)) loaded = GSON.fromJson(Files.readString(path), Data.class);
        } catch (Exception ignored) {
            // A missing or damaged file always falls back to safe disabled defaults.
        }
        current = new AfkFarmConfig(path, loaded);
        current.save();
        return current;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(data.autoReconnect, data.commandsEnabled, List.copyOf(data.commands),
                data.postJoinDelaySeconds, data.betweenCommandsDelaySeconds, data.movementStartDelaySeconds,
                data.targetX, data.targetY, data.targetZ, data.arrivalRadius, data.navigationEnabled,
                data.autoAttackEnabled, data.attackHostileMobs, data.attackAnimals, data.attackArtificialPlayers,
                List.copyOf(data.allowedHostileMobs), List.copyOf(data.allowedAnimals),
                data.maxCameraRotationDegreesPerTick, data.activeRoute,
                data.routes.stream().map(route -> new SavedRoute(route.name(), List.copyOf(route.points()))).toList());
    }

    public synchronized void setAutoReconnect(boolean value) { data.autoReconnect = value; save(); }
    public synchronized void setCommandsEnabled(boolean value) { data.commandsEnabled = value; save(); }
    public synchronized void setNavigationEnabled(boolean value) { data.navigationEnabled = value; save(); }
    public synchronized void setAutoAttackEnabled(boolean value) { data.autoAttackEnabled = value; save(); }
    public synchronized void setAttackHostileMobs(boolean value) { data.attackHostileMobs = value; save(); }
    public synchronized void setAttackAnimals(boolean value) { data.attackAnimals = value; save(); }
    public synchronized void setAttackArtificialPlayers(boolean value) { data.attackArtificialPlayers = value; save(); }

    public synchronized void setCommands(List<String> commands) {
        data.commands = sanitizeCommands(commands);
        save();
    }

    public synchronized void setDelays(int postJoin, int between, int movement) {
        data.postJoinDelaySeconds = clamp(postJoin, 0, 300);
        data.betweenCommandsDelaySeconds = clamp(between, 0, 60);
        data.movementStartDelaySeconds = clamp(movement, 0, 300);
        save();
    }

    public synchronized void setNavigation(double x, double y, double z, double radius, double rotation) {
        data.targetX = finite(x, 0);
        data.targetY = finite(y, 64);
        data.targetZ = finite(z, 0);
        data.arrivalRadius = clamp(finite(radius, 1.5), 0.25, 32);
        data.maxCameraRotationDegreesPerTick = clamp(finite(rotation, 8), 0.5, 30);
        save();
    }

    public synchronized void setAllowedEntities(List<String> hostile, List<String> animals) {
        data.allowedHostileMobs = sanitizeIds(hostile);
        data.allowedAnimals = sanitizeIds(animals);
        save();
    }

    public synchronized void saveRoute(String name, List<RoutePoint> points) {
        String safeName = sanitizeRouteName(name);
        List<RoutePoint> safePoints = sanitizePoints(points);
        if (safePoints.size() < 2) throw new IllegalArgumentException("El recorrido necesita al menos 2 puntos");
        Map<String, SavedRoute> routes = new LinkedHashMap<>();
        for (SavedRoute route : data.routes) routes.put(route.name(), route);
        routes.put(safeName, new SavedRoute(safeName, safePoints));
        data.routes = routes.values().stream().limit(MAX_ROUTES).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        data.activeRoute = safeName;
        save();
    }

    public synchronized void selectRoute(String name) {
        String safeName = name == null ? "" : name.trim();
        data.activeRoute = data.routes.stream().anyMatch(route -> route.name().equals(safeName)) ? safeName : "";
        save();
    }

    public synchronized void deleteRoute(String name) {
        data.routes.removeIf(route -> route.name().equals(name));
        if (data.activeRoute.equals(name)) data.activeRoute = data.routes.isEmpty() ? "" : data.routes.getFirst().name();
        save();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(path.getParent());
            Path pending = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(pending, GSON.toJson(data));
            try {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    private static Data normalize(Data value) {
        // alpha.7 introduced the conservative disguise detector behind a new
        // disabled flag. Existing farms therefore detected the entity but
        // silently refused to attack it. Migrate only profiles that had already
        // opted into both automatic and hostile-mob attacks.
        if (value.version < 4 && value.autoAttackEnabled && value.attackHostileMobs)
            value.attackArtificialPlayers = true;
        value.version = 4;
        value.postJoinDelaySeconds = clamp(value.postJoinDelaySeconds, 0, 300);
        value.betweenCommandsDelaySeconds = clamp(value.betweenCommandsDelaySeconds, 0, 60);
        value.movementStartDelaySeconds = clamp(value.movementStartDelaySeconds, 0, 300);
        value.targetX = finite(value.targetX, 0);
        value.targetY = finite(value.targetY, 64);
        value.targetZ = finite(value.targetZ, 0);
        value.arrivalRadius = clamp(finite(value.arrivalRadius, 1.5), 0.25, 32);
        value.maxCameraRotationDegreesPerTick = clamp(finite(value.maxCameraRotationDegreesPerTick, 8), 0.5, 30);
        value.commands = sanitizeCommands(value.commands);
        value.allowedHostileMobs = sanitizeIds(value.allowedHostileMobs);
        value.allowedAnimals = sanitizeIds(value.allowedAnimals);
        value.routes = sanitizeRoutes(value.routes);
        if (value.activeRoute == null || value.routes.stream().noneMatch(route -> route.name().equals(value.activeRoute)))
            value.activeRoute = value.routes.isEmpty() ? "" : value.routes.getFirst().name();
        return value;
    }

    private static ArrayList<SavedRoute> sanitizeRoutes(List<SavedRoute> values) {
        Map<String, SavedRoute> unique = new LinkedHashMap<>();
        if (values != null) for (SavedRoute route : values) {
            if (route == null) continue;
            try {
                String name = sanitizeRouteName(route.name());
                List<RoutePoint> points = sanitizePoints(route.points());
                if (points.size() >= 2) unique.put(name, new SavedRoute(name, points));
            } catch (IllegalArgumentException ignored) {}
            if (unique.size() >= MAX_ROUTES) break;
        }
        return new ArrayList<>(unique.values());
    }

    private static String sanitizeRouteName(String value) {
        String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (name.isBlank()) name = "Recorrido";
        if (name.length() > 48) name = name.substring(0, 48).trim();
        return name;
    }

    private static List<RoutePoint> sanitizePoints(List<RoutePoint> values) {
        if (values == null) return List.of();
        ArrayList<RoutePoint> result = new ArrayList<>();
        for (RoutePoint point : values) {
            if (point == null || !validWorldCoordinate(point.x()) || !Double.isFinite(point.y()) || !validWorldCoordinate(point.z())) continue;
            double y = clamp(point.y(), -2048, 2048);
            RoutePoint safe = new RoutePoint(point.x(), y, point.z());
            if (result.isEmpty() || distanceSquared(result.getLast(), safe) >= 0.0025) result.add(safe);
            if (result.size() >= MAX_ROUTE_POINTS) break;
        }
        return List.copyOf(result);
    }

    private static boolean validWorldCoordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000;
    }

    private static double distanceSquared(RoutePoint a, RoutePoint b) {
        double x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
        return x * x + y * y + z * z;
    }

    private static List<String> sanitizeCommands(List<String> values) {
        if (values == null) return new ArrayList<>();
        return values.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .map(value -> value.startsWith("/") ? value.substring(1).trim() : value)
                .filter(value -> !value.isBlank() && value.length() <= 256)
                .limit(MAX_COMMANDS).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static List<String> sanitizeIds(List<String> values) {
        if (values == null) return new ArrayList<>();
        return values.stream().filter(java.util.Objects::nonNull).map(value -> value.trim().toLowerCase())
                .filter(value -> value.equals("*") || value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                .distinct().limit(MAX_ALLOWED_ENTITIES)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
