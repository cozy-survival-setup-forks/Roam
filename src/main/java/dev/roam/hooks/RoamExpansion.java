package dev.roam.hooks;

import dev.roam.RoamPlugin;
import dev.roam.TimeFormat;
import dev.roam.config.Settings;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI values, for scoreboards and menus:
 * %roam_cooldown% (seconds left), %roam_cooldown_formatted%, %roam_ready% and %roam_cost%.
 * They describe /rtp in the world the player is in. Only loaded when PlaceholderAPI is installed.
 */
public final class RoamExpansion extends PlaceholderExpansion {

    private final RoamPlugin plugin;

    public RoamExpansion(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "roam";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Roam";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        World target = Bukkit.getWorld(plugin.roamConfig().resolveWorld(player.getWorld().getName()));
        if (target == null) return "";
        Settings settings = plugin.roamConfig().settingsFor(target, player);
        long left = plugin.cooldowns().remainingMillis(player.getUniqueId(), target.getName(),
                plugin.roamConfig().cooldownPerWorld(), settings.cooldownSeconds());

        return switch (params.toLowerCase()) {
            case "cooldown" -> String.valueOf((left + 999) / 1000);
            case "cooldown_formatted" -> left <= 0 ? "0s" : TimeFormat.format(left);
            case "ready" -> String.valueOf(left <= 0);
            case "cost" -> String.valueOf(settings.cost());
            default -> null;
        };
    }
}
