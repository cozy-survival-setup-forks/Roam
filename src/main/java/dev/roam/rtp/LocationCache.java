package dev.roam.rtp;

import dev.roam.RoamPlugin;
import dev.roam.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps a few safe spots ready for every set of settings in use, found in the background, so
 * {@code /rtp} can teleport straight away. A spot is checked again when it is used.
 */
public final class LocationCache {

    private static final int MAX_ENTRIES = 32;

    private static final class Entry {
        final Settings settings;
        final Deque<Location> spots = new ArrayDeque<>();
        boolean searching = false;

        Entry(Settings settings) {
            this.settings = settings;
        }
    }

    private final RoamPlugin plugin;
    private final Map<String, Entry> entries = new HashMap<>();
    private @Nullable BukkitTask task;

    public LocationCache(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.roamConfig().cacheEnabled()) return;

        // Every enabled world gets its default spots ready.
        for (World world : Bukkit.getWorlds()) {
            if (plugin.roamConfig().isWorldEnabled(world.getName())) register(plugin.roamConfig().settingsFor(world, null));
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refill, 100L, 40L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        entries.clear();
    }

    /** Makes sure spots are kept ready for these settings. Settings that follow the player cannot be cached. */
    public void register(Settings settings) {
        if (settings.followsPlayer() || !plugin.roamConfig().cacheEnabled()) return;
        if (entries.size() >= MAX_ENTRIES && !entries.containsKey(settings.cacheKey())) return;
        entries.putIfAbsent(settings.cacheKey(), new Entry(settings));
    }

    /** A ready spot for these settings, or null. */
    public @Nullable Location poll(Settings settings) {
        Entry entry = entries.get(settings.cacheKey());
        if (entry == null) return null;
        while (!entry.spots.isEmpty()) {
            Location spot = entry.spots.poll();
            if (plugin.finder().stillSafe(settings, spot)) return spot;
        }
        return null;
    }

    public int size(Settings settings) {
        Entry entry = entries.get(settings.cacheKey());
        return entry == null ? 0 : entry.spots.size();
    }

    private void refill() {
        int target = plugin.roamConfig().cacheSize();
        for (Entry entry : entries.values()) {
            if (entry.searching || entry.spots.size() >= target) continue;
            entry.searching = true;
            plugin.finder().find(entry.settings, () -> false).whenComplete((found, error) -> {
                entry.searching = false;
                if (error == null && found != null && found.isPresent()) entry.spots.add(found.get().spot());
            });
        }
    }
}
