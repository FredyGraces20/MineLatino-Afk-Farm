package com.minelatino.afkfarm;

import java.util.List;

/** Stateless route math kept outside Minecraft so overshoot and look-ahead behavior can be tested. */
public final class RecordedRouteNavigator {
    private static final int START_SEARCH_POINTS = 32;
    private static final int RESYNC_POINTS = 12;
    private static final int LOOK_AHEAD_POINTS = 8;
    private static final double LOOK_AHEAD_DISTANCE = 1.35;

    private RecordedRouteNavigator() {}

    public static int startingIndex(List<AfkFarmConfig.RoutePoint> points, double x, double y, double z) {
        if (points == null || points.isEmpty()) return 0;
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(points.size(), START_SEARCH_POINTS); i++) {
            double distance = weightedDistanceSquared(points.get(i), x, y, z);
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }

    public static int advance(List<AfkFarmConfig.RoutePoint> points, int current,
                              double x, double y, double z, double radius) {
        if (points == null || points.isEmpty()) return 0;
        int last = points.size() - 1;
        int best = Math.max(0, Math.min(current, last));
        int searchEnd = Math.min(last, best + RESYNC_POINTS);
        double bestDistance = weightedDistanceSquared(points.get(best), x, y, z);
        for (int i = best + 1; i <= searchEnd; i++) {
            double distance = weightedDistanceSquared(points.get(i), x, y, z);
            if (distance + 0.04 < bestDistance) { best = i; bestDistance = distance; }
        }
        while (best < last && horizontalDistanceSquared(points.get(best), x, z) <= radius * radius
                && Math.abs(points.get(best).y() - y) <= 1.25) best++;
        return best;
    }

    public static int lookAheadIndex(List<AfkFarmConfig.RoutePoint> points, int current) {
        if (points == null || points.isEmpty()) return 0;
        int last = points.size() - 1;
        int result = Math.max(0, Math.min(current, last));
        double distance = 0;
        while (result < last && result - current < LOOK_AHEAD_POINTS && distance < LOOK_AHEAD_DISTANCE) {
            distance += horizontalDistance(points.get(result), points.get(result + 1));
            result++;
        }
        return result;
    }

    public static double maximumRise(List<AfkFarmConfig.RoutePoint> points, int from, int to, double playerY) {
        if (points == null || points.isEmpty()) return 0;
        double highest = playerY;
        for (int i = Math.max(0, from); i <= Math.min(points.size() - 1, to); i++)
            highest = Math.max(highest, points.get(i).y());
        return highest - playerY;
    }

    private static double weightedDistanceSquared(AfkFarmConfig.RoutePoint point, double x, double y, double z) {
        double dx = point.x() - x, dy = (point.y() - y) * 0.7, dz = point.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double horizontalDistanceSquared(AfkFarmConfig.RoutePoint point, double x, double z) {
        double dx = point.x() - x, dz = point.z() - z;
        return dx * dx + dz * dz;
    }

    private static double horizontalDistance(AfkFarmConfig.RoutePoint a, AfkFarmConfig.RoutePoint b) {
        double dx = a.x() - b.x(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
