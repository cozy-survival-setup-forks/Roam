package dev.roam.rtp;

import dev.roam.config.Settings;

import java.util.Random;

/**
 * Picks random x and z coordinates for a teleport. The points can be spread evenly over the whole
 * area, or bunched up around a chosen distance from the centre (a gaussian spread).
 */
public final class Sampler {

    private Sampler() {
    }

    /** @return {x, z} */
    public static int[] sample(Random random, Settings settings) {
        double distance = distance(random, settings);
        double offsetX;
        double offsetZ;

        if (settings.shape() == Settings.Shape.CIRCLE) {
            double angle = random.nextDouble() * Math.PI * 2;
            offsetX = Math.cos(angle) * distance;
            offsetZ = Math.sin(angle) * distance;
        } else {
            // A square ring: a point on the edge of a square whose half side is the distance.
            double along = random.nextDouble() * 8 * distance;
            double side = distance * 2;
            int edge = (int) (along / side);
            double position = along - edge * side - distance;
            switch (Math.min(edge, 3)) {
                case 0 -> { offsetX = position; offsetZ = -distance; }
                case 1 -> { offsetX = distance; offsetZ = position; }
                case 2 -> { offsetX = -position; offsetZ = distance; }
                default -> { offsetX = -distance; offsetZ = -position; }
            }
        }
        return new int[]{(int) Math.round(settings.centerX() + offsetX), (int) Math.round(settings.centerZ() + offsetZ)};
    }

    /** How far from the centre the point is, between the minimum and maximum radius. */
    static double distance(Random random, Settings settings) {
        double min = settings.minRadius();
        double max = settings.maxRadius();

        if (!settings.gaussian()) {
            // Equal chance for every bit of area: rings further out are bigger, so they are more likely.
            return Math.sqrt(random.nextDouble() * (max * max - min * min) + min * min);
        }

        double deviation = 1.0 / settings.gaussianShrink();
        double fraction = settings.gaussianCenter();
        for (int i = 0; i < 20; i++) {
            double candidate = settings.gaussianCenter() + random.nextGaussian() * deviation;
            if (candidate >= 0 && candidate <= 1) {
                fraction = candidate;
                break;
            }
        }
        return min + (max - min) * fraction;
    }
}
