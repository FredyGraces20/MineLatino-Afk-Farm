package com.minelatino.afkfarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class RecordedRouteNavigatorTest {
    private static final List<AfkFarmConfig.RoutePoint> STRAIGHT = List.of(
            p(0, 64, 0), p(0, 64, 0.5), p(0, 64, 1), p(0, 64, 1.5),
            p(0, 64, 2), p(0, 64, 2.5), p(0, 64, 3));

    @Test void startsAtClosestEarlyPointAndResynchronizesAfterOvershoot() {
        assertEquals(2, RecordedRouteNavigator.startingIndex(STRAIGHT, 0, 64, 1.1));
        assertTrue(RecordedRouteNavigator.advance(STRAIGHT, 1, 0, 64, 2.1, .7) >= 4);
    }

    @Test void aimsAheadInsteadOfTurningForEveryDenseSample() {
        assertTrue(RecordedRouteNavigator.lookAheadIndex(STRAIGHT, 1) >= 4);
    }

    @Test void detectsAnUpcomingRecordedJump() {
        var route = List.of(p(0,64,0), p(0,64,1), p(0,65,1.5));
        assertEquals(1.0, RecordedRouteNavigator.maximumRise(route, 0, 2, 64));
    }

    @Test void resynchronizationCannotSkipAnEntireLoopedRoute() {
        var longRoute = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> p(0, 64, i)).toList();
        assertEquals(12, RecordedRouteNavigator.advance(longRoute, 0, 0, 64, 20, .7));
    }

    private static AfkFarmConfig.RoutePoint p(double x, double y, double z) {
        return new AfkFarmConfig.RoutePoint(x, y, z);
    }
}
