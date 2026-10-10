package dev.roam.rtp;

import dev.roam.RoamPlugin;
import dev.roam.safe.Health;
import dev.roam.safe.SafeIo;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * When each player last teleported. It is saved to cooldowns.yml, so restarting the server does not
 * reset anyone's cooldown.
 */
public final class CooldownStore {

    private static final String ALL_WORLDS = "*";

    private final Logger logger;
    private final File file;
    private final Map<UUID, Map<String, Long>> lastUse = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    /** Entries older than this cannot matter any more: the longest cooldown that is set anywhere. */
    private volatile long keepMillis = 7L * 24 * 60 * 60 * 1000;

    public CooldownStore(RoamPlugin plugin) {
        this(new File(plugin.getDataFolder(), "cooldowns.yml"), plugin.getLogger());
    }

    CooldownStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Sets how long an entry is worth keeping, the longest cooldown in seconds. At least a day. */
    public void keepFor(long seconds) {
        keepMillis = Math.max(24L * 60 * 60, seconds) * 1000L;
    }

    public void load() {
        lastUse.clear();
        if (!file.exists()) return;
        // a damaged file is restored from its .bak; with none, it is kept as .broken-<time> and everyone starts without a
        // cooldown (a cooldown that is lost only lets a player teleport a little earlier)
        YamlConfiguration yaml = SafeIo.loadYaml(file.toPath(), SafeIo.Policy.SETTINGS, logger).yaml;
        SafeIo.refreshBackup(file.toPath());
        for (String id : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                var section = yaml.getConfigurationSection(id);
                if (section == null) continue;
                Map<String, Long> worlds = new ConcurrentHashMap<>();
                for (String world : section.getKeys(false)) worlds.put(world, section.getLong(world));
                lastUse.put(uuid, worlds);
            } catch (IllegalArgumentException ignored) {
                // Not a player id, skip it.
            }
        }
    }

    private String key(String world, boolean perWorld) {
        return perWorld ? world : ALL_WORLDS;
    }

    /** How many milliseconds are left of the cooldown, 0 if the player can teleport. */
    public long remainingMillis(UUID player, String world, boolean perWorld, int cooldownSeconds) {
        return remainingMillis(player, world, perWorld, cooldownSeconds, System.currentTimeMillis());
    }

    long remainingMillis(UUID player, String world, boolean perWorld, int cooldownSeconds, long now) {
        if (cooldownSeconds <= 0) return 0;
        Map<String, Long> worlds = lastUse.get(player);
        if (worlds == null) return 0;
        Long last = worlds.get(key(world, perWorld));
        if (last == null) return 0;
        return Math.max(0, last + cooldownSeconds * 1000L - now);
    }

    public void mark(UUID player, String world, boolean perWorld) {
        lastUse.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).put(key(world, perWorld), System.currentTimeMillis());
        dirty = true;
    }

    /** How many players have a cooldown entry. */
    public int tracked() {
        return lastUse.size();
    }

    /** Whether there is something that is not on disk yet. */
    public boolean pending() {
        return dirty;
    }

    public void clear(UUID player) {
        lastUse.remove(player);
        dirty = true;
    }

    /** Writes the file if something changed. Safe to call often. */
    public synchronized void saveIfNeeded() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> player : new HashMap<>(lastUse).entrySet()) {
            // Old entries cannot matter any more, keep the file small.
            player.getValue().values().removeIf(last -> now - last > keepMillis);
            for (Map.Entry<String, Long> world : player.getValue().entrySet()) {
                yaml.set(player.getKey() + "." + world.getKey(), world.getValue());
            }
        }
        try {
            // through a temporary file that is flushed to disk, with the previous version kept as cooldowns.yml.bak
            SafeIo.writeYaml(file.toPath(), yaml.saveToString());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not save cooldowns.yml", ex);
            Health.failure("cooldowns.yml could not be saved: " + ex.getMessage());
            dirty = true;
        }
    }
}
