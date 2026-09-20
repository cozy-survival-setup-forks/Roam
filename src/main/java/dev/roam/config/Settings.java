package dev.roam.config;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Everything that decides where and how one player is teleported in one world, worked out from the
 * defaults, the world and the player's group.
 */
public record Settings(
        World world,
        Shape shape,
        int centerX,
        int centerZ,
        boolean followsPlayer,
        int minRadius,
        int maxRadius,
        boolean gaussian,
        double gaussianCenter,
        double gaussianShrink,
        int minY,
        int maxY,
        Search search,
        int cooldownSeconds,
        int delaySeconds,
        double cost,
        Set<String> allowedBiomes,
        Set<String> blockedBiomes,
        int freeFall,
        List<String> commands
) {

    public enum Shape { SQUARE, CIRCLE }

    public enum Search { SURFACE, CAVE }

    /**
     * @param player where the player is, used when the centre is set to "player"
     */
    public static Settings resolve(World world, Location player, Layers layers) {
        Shape shape = parseShape(layers.getString("shape", "square"));

        // The centre: spawn, player, or x and z.
        int centerX;
        int centerZ;
        boolean follows = false;
        ConfigurationSection centerSection = layers.getSection("center");
        String centerText = centerSection == null ? layers.getString("center", "spawn").toLowerCase(Locale.ROOT) : "";
        if (centerSection != null) {
            centerX = centerSection.getInt("x", 0);
            centerZ = centerSection.getInt("z", 0);
        } else if (centerText.equals("player") && player != null && player.getWorld().equals(world)) {
            centerX = player.getBlockX();
            centerZ = player.getBlockZ();
            follows = true;
        } else if (centerText.equals("origin") || centerText.equals("zero")) {
            centerX = 0;
            centerZ = 0;
        } else {
            Location spawn = world.getSpawnLocation();
            centerX = spawn.getBlockX();
            centerZ = spawn.getBlockZ();
        }

        int min = Math.max(0, layers.getInt("radius.min", 100));
        int max = layers.getInt("radius.max", 3000);
        if (max <= 0) max = 1000;
        if (min >= max) min = 0;

        // Keep inside the world border, and use its centre if that is asked for.
        if (layers.getBoolean("use-world-border", true)) {
            var border = world.getWorldBorder();
            int borderRadius = (int) (border.getSize() / 2) - 16;
            if (border.getSize() < 5.9E7) { // The default border is effectively infinite.
                if (layers.getBoolean("center-on-border", false)) {
                    centerX = border.getCenter().getBlockX();
                    centerZ = border.getCenter().getBlockZ();
                }
                if (borderRadius > 0 && max > borderRadius) max = borderRadius;
                if (min >= max) min = 0;
            }
        }

        boolean cave = layers.getString("search", "auto").equalsIgnoreCase("cave")
                || (layers.getString("search", "auto").equalsIgnoreCase("auto") && world.getEnvironment() == World.Environment.NETHER);
        Search search = cave ? Search.CAVE : Search.SURFACE;

        int minY = yLimit(layers.getString("y.min", "auto"), autoMinY(world, search));
        int maxY = yLimit(layers.getString("y.max", "auto"), autoMaxY(world, search));
        minY = Math.max(minY, world.getMinHeight());
        maxY = Math.min(maxY, world.getMaxHeight() - 2);
        if (minY >= maxY) {
            minY = autoMinY(world, search);
            maxY = autoMaxY(world, search);
        }

        boolean gaussian = layers.getString("distribution", "even").equalsIgnoreCase("gaussian");

        return new Settings(
                world, shape, centerX, centerZ, follows, min, max,
                gaussian,
                clamp(layers.getDouble("gaussian.center", 0.3), 0, 1),
                Math.max(0.5, layers.getDouble("gaussian.shrink", 4)),
                minY, maxY, search,
                Math.max(0, layers.getInt("cooldown", 60)),
                Math.max(0, layers.getInt("delay", 3)),
                Math.max(0, layers.getDouble("cost", 0)),
                lowerSet(layers.getStringList("allowed-biomes")),
                lowerSet(layers.getStringList("blocked-biomes")),
                Math.max(0, layers.getInt("free-fall", 0)),
                layers.getStringList("commands")
        );
    }

    private static Shape parseShape(String text) {
        return text.equalsIgnoreCase("circle") ? Shape.CIRCLE : Shape.SQUARE;
    }

    private static int autoMinY(World world, Search search) {
        if (search == Search.CAVE) {
            // Below 32 the nether is mostly lava lakes.
            return world.getEnvironment() == World.Environment.NETHER ? 32 : world.getMinHeight() + 8;
        }
        return world.getMinHeight();
    }

    private static int autoMaxY(World world, Search search) {
        // The nether has a bedrock roof, its logical height is the usable part.
        return search == Search.CAVE ? world.getLogicalHeight() - 8 : world.getMaxHeight() - 2;
    }

    private static int yLimit(String text, int auto) {
        if (text == null || text.equalsIgnoreCase("auto")) return auto;
        try {
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return auto;
        }
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static Set<String> lowerSet(List<String> list) {
        Set<String> set = new HashSet<>();
        for (String entry : list) {
            String name = entry.toLowerCase(Locale.ROOT);
            set.add(name.contains(":") ? name.substring(name.indexOf(':') + 1) : name);
        }
        return set;
    }

    /** Settings that give the same spots share cached locations. Spots that follow the player are never cached. */
    public String cacheKey() {
        return world.getName() + '|' + shape + '|' + centerX + ',' + centerZ + '|' + minRadius + '-' + maxRadius
                + '|' + gaussian + gaussianCenter + gaussianShrink + '|' + minY + '-' + maxY + '|' + search
                + '|' + freeFall + '|' + new java.util.TreeSet<>(allowedBiomes) + new java.util.TreeSet<>(blockedBiomes);
    }
}
