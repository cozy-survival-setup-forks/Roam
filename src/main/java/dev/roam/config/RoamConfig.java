package dev.roam.config;

import dev.roam.RoamPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads config.yml. The rules are simple: {@code defaults} apply everywhere, {@code worlds.<name>} changes
 * them for one world and {@code groups.<name>} changes them for players with the permission
 * {@code roam.group.<name>}. A group wins over a world, a world wins over the defaults.
 */
public final class RoamConfig {

    private final RoamPlugin plugin;

    public RoamConfig(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        validate();
    }

    private ConfigurationSection root() {
        return plugin.getConfig();
    }

    // ---- world rules ----

    /** The settings for a player in a world, with their group and the world rules applied. */
    public Settings settingsFor(World world, @Nullable Player player) {
        return Settings.resolve(world, player == null ? null : player.getLocation(), layers(world.getName(), player));
    }

    public Layers layers(String worldName, @Nullable Player player) {
        ConfigurationSection group = player == null ? null : groupOf(player);
        return new Layers(group, worldSection(worldName), root().getConfigurationSection("defaults"));
    }

    public @Nullable ConfigurationSection worldSection(String worldName) {
        ConfigurationSection worlds = root().getConfigurationSection("worlds");
        if (worlds == null) return null;
        for (String key : worlds.getKeys(false)) {
            if (key.equalsIgnoreCase(worldName)) return worlds.getConfigurationSection(key);
        }
        return null;
    }

    /** A world can be turned off with {@code enabled: false}. Worlds that are not listed work with the defaults. */
    public boolean isWorldEnabled(String worldName) {
        ConfigurationSection section = worldSection(worldName);
        return section == null || section.getBoolean("enabled", true);
    }

    /** The world a world sends players to, or the same world. Follows a few steps so chains work. */
    public String resolveWorld(String worldName) {
        String current = worldName;
        for (int i = 0; i < 4; i++) {
            ConfigurationSection section = worldSection(current);
            String target = section == null ? null : section.getString("redirect");
            if (target == null || target.isBlank() || target.equalsIgnoreCase(current)) break;
            current = target;
        }
        return current;
    }

    public @Nullable ConfigurationSection groupOf(Player player) {
        ConfigurationSection groups = root().getConfigurationSection("groups");
        if (groups == null) return null;
        for (String name : groups.getKeys(false)) {
            if (player.hasPermission("roam.group." + name)) return groups.getConfigurationSection(name);
        }
        return null;
    }

    // ---- general options ----

    public boolean perWorldPermission() {
        return root().getBoolean("per-world-permission", false);
    }

    public boolean cooldownPerWorld() {
        return root().getBoolean("cooldown-per-world", false);
    }

    public boolean cancelOnMove() {
        return root().getBoolean("delay-cancel.move", true);
    }

    public boolean cancelOnDamage() {
        return root().getBoolean("delay-cancel.damage", true);
    }

    public int maxAttempts() {
        return Math.max(1, root().getInt("max-attempts", 30));
    }

    public int invulnerableSeconds() {
        return Math.max(0, root().getInt("invulnerable-seconds", 5));
    }

    public boolean cacheEnabled() {
        return root().getBoolean("cache.enabled", true);
    }

    public int cacheSize() {
        return Math.max(1, root().getInt("cache.size", 6));
    }

    public boolean avoidClaims() {
        return root().getBoolean("avoid-claims", true);
    }

    public List<String> unsafeBlocks() {
        return root().getStringList("unsafe-blocks");
    }

    public boolean firstJoinEnabled() {
        return root().getBoolean("first-join.enabled", false);
    }

    public String firstJoinWorld() {
        return root().getString("first-join.world", "world");
    }

    public boolean onDeathEnabled() {
        return root().getBoolean("on-death.enabled", false);
    }

    public boolean onDeathRespectBed() {
        return root().getBoolean("on-death.respect-bed", true);
    }

    public String sound(String name) {
        return root().getString("sounds." + name, "");
    }

    // ---- checks ----

    /** Warns about settings that look wrong, so a typo does not have to be hunted down. */
    private void validate() {
        ConfigurationSection defaults = root().getConfigurationSection("defaults");
        if (defaults == null) {
            plugin.getLogger().warning("config.yml has no 'defaults' section, built-in values are used.");
        }
        checkRadius("defaults", defaults);


        ConfigurationSection worlds = root().getConfigurationSection("worlds");
        if (worlds != null) {
            Set<String> loaded = new HashSet<>();
            for (World world : Bukkit.getWorlds()) loaded.add(world.getName().toLowerCase());
            for (String name : worlds.getKeys(false)) {
                if (!loaded.contains(name.toLowerCase())) {
                    plugin.getLogger().warning("worlds." + name + " is set up, but no world with that name is loaded (names are case sensitive on some systems).");
                }
                ConfigurationSection section = worlds.getConfigurationSection(name);
                checkRadius("worlds." + name, section);
                String redirect = section == null ? null : section.getString("redirect");
                if (redirect != null && !loaded.contains(redirect.toLowerCase())) {
                    plugin.getLogger().warning("worlds." + name + ".redirect points to '" + redirect + "', which is not a loaded world.");
                }
            }
        }
    }

    private void checkRadius(String path, @Nullable ConfigurationSection section) {
        if (section == null) return;
        int min = section.getInt("radius.min", 0);
        int max = section.getInt("radius.max", Integer.MAX_VALUE);
        if (min < 0 || (section.isSet("radius.max") && max <= 0)) {
            plugin.getLogger().warning(path + ": radius values must be above zero.");
        } else if (section.isSet("radius.max") && section.isSet("radius.min") && min >= max) {
            plugin.getLogger().warning(path + ": radius.min (" + min + ") must be smaller than radius.max (" + max + "), the minimum is ignored.");
        }
    }
}
