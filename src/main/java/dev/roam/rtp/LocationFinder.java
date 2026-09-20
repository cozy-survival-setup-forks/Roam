package dev.roam.rtp;

import dev.roam.RoamPlugin;
import dev.roam.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Finds safe spots. A random point is picked, its chunk is loaded without freezing the server (Paper
 * loads it on another thread), and then the spot is checked. This repeats until a safe spot is found.
 */
public final class LocationFinder {

    public record Found(Location spot, int attempts) {
    }

    private final RoamPlugin plugin;
    private final Random random = new Random();
    private Set<Material> unsafe = EnumSet.noneOf(Material.class);

    public LocationFinder(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Set<Material> blocks = EnumSet.noneOf(Material.class);
        for (String name : plugin.roamConfig().unsafeBlocks()) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.getLogger().warning("unsafe-blocks: '" + name + "' is not a block, skipping it.");
            } else {
                blocks.add(material);
            }
        }
        unsafe = blocks;
    }

    /** Searches for a safe spot. The future holds nothing if no safe spot was found in time. */
    public CompletableFuture<Optional<Found>> find(Settings settings, BooleanSupplier cancelled) {
        CompletableFuture<Optional<Found>> result = new CompletableFuture<>();
        attempt(settings, cancelled, 1, plugin.roamConfig().maxAttempts(), result);
        return result;
    }

    private void attempt(Settings settings, BooleanSupplier cancelled, int number, int max, CompletableFuture<Optional<Found>> result) {
        if (cancelled.getAsBoolean() || number > max) {
            result.complete(Optional.empty());
            return;
        }

        int[] xz = Sampler.sample(random, settings);
        World world = settings.world();

        // Points outside the border can never be used, there is no need to load their chunk.
        if (!world.getWorldBorder().isInside(new Location(world, xz[0], 64, xz[1]))) {
            attempt(settings, cancelled, number + 1, max, result);
            return;
        }

        world.getChunkAtAsync(xz[0] >> 4, xz[1] >> 4, true).whenComplete((chunk, error) -> onMainThread(() -> {
            if (error == null && chunk != null) {
                Optional<Location> spot = check(settings, xz[0], xz[1]);
                if (spot.isPresent()) {
                    result.complete(Optional.of(new Found(spot.get(), number)));
                    return;
                }
            }
            attempt(settings, cancelled, number + 1, max, result);
        }));
    }

    private void onMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Checks one column. The chunk has to be loaded. Returns the block the player would stand in. */
    public Optional<Location> check(Settings settings, int x, int z) {
        World world = settings.world();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return Optional.empty();

        int y = findY(world, x, z, settings);
        if (y == Integer.MIN_VALUE) return Optional.empty();

        String biome = world.getBiome(x, y, z).getKey().getKey();
        if (!settings.allowedBiomes().isEmpty() && !settings.allowedBiomes().contains(biome)) return Optional.empty();
        if (settings.blockedBiomes().contains(biome)) return Optional.empty();

        Location spot = new Location(world, x + 0.5, y, z + 0.5);
        if (plugin.roamConfig().avoidClaims() && plugin.hooks().isClaimed(spot)) return Optional.empty();
        return Optional.of(spot);
    }

    /** A cached spot is looked at again before use, the world may have changed. */
    public boolean stillSafe(Settings settings, Location spot) {
        World world = spot.getWorld();
        if (world == null || !world.isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) return false;
        if (!isSafeSpot(world, spot.getBlockX(), spot.getBlockY(), spot.getBlockZ())) return false;
        return !(plugin.roamConfig().avoidClaims() && plugin.hooks().isClaimed(spot));
    }

    private int findY(World world, int x, int z, Settings settings) {
        if (settings.search() == Settings.Search.SURFACE) {
            int feet = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (feet < settings.minY() || feet > settings.maxY()) return Integer.MIN_VALUE;
            return isSafeSpot(world, x, feet, z) ? feet : Integer.MIN_VALUE;
        }

        // Caves: start at a random height and look up and down for a pocket of air with a floor.
        int span = settings.maxY() - settings.minY() + 1;
        int start = random.nextInt(span);
        for (int i = 0; i < span; i++) {
            int y = settings.minY() + (start + i) % span;
            if (isSafeSpot(world, x, y, z)) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Solid ground below, and two free blocks to stand in. */
    boolean isSafeSpot(World world, int x, int y, int z) {
        Block floor = world.getBlockAt(x, y - 1, z);
        if (!floor.getType().isSolid() || unsafe.contains(floor.getType()) || Tag.LEAVES.isTagged(floor.getType())) return false;
        return isFree(world.getBlockAt(x, y, z)) && isFree(world.getBlockAt(x, y + 1, z));
    }

    private boolean isFree(Block block) {
        return block.isPassable() && !block.isLiquid() && !unsafe.contains(block.getType());
    }
}
