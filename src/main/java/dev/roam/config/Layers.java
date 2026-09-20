package dev.roam.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Looks a setting up in several config sections, most specific first (a group, then a world, then the
 * defaults). Each setting is looked up on its own, so a world can change only the maximum radius and
 * still get the minimum radius from the defaults.
 * <p>
 * Numbers are read the way Bukkit reads them, so {@code 50}, {@code 50.0} and {@code "50"} all work.
 */
public final class Layers {

    private final List<ConfigurationSection> layers = new ArrayList<>();

    public Layers(ConfigurationSection... sections) {
        for (ConfigurationSection section : sections) {
            if (section != null) layers.add(section);
        }
    }

    private ConfigurationSection layerWith(String path) {
        for (ConfigurationSection layer : layers) {
            if (layer.isSet(path)) return layer;
        }
        return null;
    }

    public boolean has(String path) {
        return layerWith(path) != null;
    }

    public int getInt(String path, int fallback) {
        ConfigurationSection layer = layerWith(path);
        if (layer == null) return fallback;
        Object value = layer.get(path);
        if (value instanceof Number number) return number.intValue();
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public double getDouble(String path, double fallback) {
        ConfigurationSection layer = layerWith(path);
        if (layer == null) return fallback;
        Object value = layer.get(path);
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public boolean getBoolean(String path, boolean fallback) {
        ConfigurationSection layer = layerWith(path);
        if (layer == null) return fallback;
        Object value = layer.get(path);
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value).trim().toLowerCase();
        if (text.equals("true") || text.equals("yes") || text.equals("on")) return true;
        if (text.equals("false") || text.equals("no") || text.equals("off")) return false;
        return fallback;
    }

    public String getString(String path, String fallback) {
        ConfigurationSection layer = layerWith(path);
        if (layer == null) return fallback;
        Object value = layer.get(path);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    public List<String> getStringList(String path) {
        ConfigurationSection layer = layerWith(path);
        return layer == null ? List.of() : layer.getStringList(path);
    }

    /** The section at a path, from the most specific layer that has one. */
    public ConfigurationSection getSection(String path) {
        for (ConfigurationSection layer : layers) {
            ConfigurationSection section = layer.getConfigurationSection(path);
            if (section != null) return section;
        }
        return null;
    }
}
