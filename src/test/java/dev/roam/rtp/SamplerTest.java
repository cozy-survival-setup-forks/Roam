package dev.roam.rtp;

import dev.roam.config.Settings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerTest {

    private static Settings settings(Settings.Shape shape, int min, int max, boolean gaussian, double gaussianCenter) {
        return new Settings(null, shape, 1000, -500, false, min, max, gaussian, gaussianCenter, 4,
                0, 100, Settings.Search.SURFACE, 0, 0, 0, Set.of(), Set.of(), 0, List.of());
    }

    @Test
    void squarePointsStayInTheRing() {
        Settings s = settings(Settings.Shape.SQUARE, 200, 1000, false, 0.3);
        Random random = new Random(1);

        for (int i = 0; i < 20_000; i++) {
            int[] p = Sampler.sample(random, s);
            int distance = Math.max(Math.abs(p[0] - 1000), Math.abs(p[1] + 500));
            assertTrue(distance >= 199 && distance <= 1001, "distance " + distance);
        }
    }

    @Test
    void circlePointsStayInTheRing() {
        Settings s = settings(Settings.Shape.CIRCLE, 300, 800, false, 0.3);
        Random random = new Random(2);

        for (int i = 0; i < 20_000; i++) {
            int[] p = Sampler.sample(random, s);
            double distance = Math.hypot(p[0] - 1000, p[1] + 500);
            assertTrue(distance >= 299 && distance <= 801, "distance " + distance);
        }
    }

    @Test
    void evenSpreadCoversTheAreaEvenly() {
        // Half of a disc's area lies beyond radius / sqrt(2), so about half the points should too.
        Settings s = settings(Settings.Shape.CIRCLE, 0, 1000, false, 0.3);
        Random random = new Random(3);
        int far = 0;
        int total = 40_000;
        for (int i = 0; i < total; i++) {
            int[] p = Sampler.sample(random, s);
            if (Math.hypot(p[0] - 1000, p[1] + 500) > 1000 / Math.sqrt(2)) far++;
        }
        assertEquals(0.5, far / (double) total, 0.02);
    }

    @Test
    void squarePointsReachAllFourSides() {
        Settings s = settings(Settings.Shape.SQUARE, 500, 501, false, 0.3);
        Random random = new Random(4);
        boolean north = false;
        boolean south = false;
        boolean east = false;
        boolean west = false;
        for (int i = 0; i < 2_000; i++) {
            int[] p = Sampler.sample(random, s);
            int dx = p[0] - 1000;
            int dz = p[1] + 500;
            if (dz < -400) north = true;
            if (dz > 400) south = true;
            if (dx > 400) east = true;
            if (dx < -400) west = true;
        }
        assertTrue(north && south && east && west);
    }

    @Test
    void gaussianBunchesPointsAroundTheChosenDistance() {
        Settings near = settings(Settings.Shape.CIRCLE, 0, 1000, true, 0.2);
        Settings far = settings(Settings.Shape.CIRCLE, 0, 1000, true, 0.8);
        Random random = new Random(5);

        double nearSum = 0;
        double farSum = 0;
        int total = 20_000;
        for (int i = 0; i < total; i++) {
            double n = Sampler.distance(random, near);
            assertTrue(n >= 0 && n <= 1000);
            nearSum += n;
            farSum += Sampler.distance(random, far);
        }
        // The spread is cut off at the radius limits, so the average sits a bit inside the chosen distance.
        assertTrue(nearSum / total < 350, "near " + nearSum / total);
        assertTrue(farSum / total > 650, "far " + farSum / total);
    }
}
