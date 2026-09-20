package dev.roam.rtp;

import dev.roam.RoamPlugin;
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

    public CooldownStore(RoamPlugin plugin) {
        this(new File(plugin.getDataFolder(), "cooldowns.yml"), plugin.getLogger());
    }

    CooldownStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        lastUse.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
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

    public void clear(UUID player) {
        lastUse.remove(player);
        dirty = true;
    }

    /** Writes the file if something changed. Safe to call often. */
    public void saveIfNeeded() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        long day = 24L * 60 * 60 * 1000;
        for (Map.Entry<UUID, Map<String, Long>> player : new HashMap<>(lastUse).entrySet()) {
            for (Map.Entry<String, Long> world : player.getValue().entrySet()) {
                // Old entries cannot matter any more, keep the file small.
                if (now - world.getValue() > 7 * day) continue;
                yaml.set(player.getKey() + "." + world.getKey(), world.getValue());
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not save cooldowns.yml", ex);
            dirty = true;
        }
    }
}
