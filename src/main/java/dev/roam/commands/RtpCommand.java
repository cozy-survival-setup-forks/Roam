package dev.roam.commands;

import dev.roam.RoamPlugin;
import dev.roam.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <pre>
 * /rtp                        teleport in the world you are in (or run menu-command when require-world is on)
 * /rtp &lt;world&gt;                 teleport to another world
 * /rtp player &lt;name&gt; [world]   teleport someone else (roam.admin)
 * /rtp info [world]           show the settings of a world and try a search (roam.admin)
 * /rtp reset &lt;name&gt;            clear someone's cooldown (roam.admin)
 * /rtp reload                 reload the files (roam.admin)
 * </pre>
 */
public final class RtpCommand implements CommandExecutor, TabCompleter {

    private final RoamPlugin plugin;

    public RtpCommand(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var messages = plugin.messages();

        if (args.length > 0) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> {
                    if (!admin(sender)) return true;
                    plugin.reloadAll();
                    messages.send(sender, "reloaded");
                    return true;
                }
                case "player" -> {
                    if (!admin(sender)) return true;
                    return teleportOther(sender, args);
                }
                case "info" -> {
                    if (!admin(sender)) return true;
                    info(sender, args);
                    return true;
                }
                case "reset" -> {
                    if (!admin(sender)) return true;
                    return reset(sender, args);
                }
                default -> {
                    // Anything else is a world name.
                }
            }
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (args.length == 0 && plugin.roamConfig().requireWorld()) {
            plainRtp(player);
            return true;
        }
        plugin.service().request(player, args.length > 0 ? args[0] : null, false);
        return true;
    }

    /** A plain /rtp while require-world is on: no teleport, but it can open a menu. */
    private void plainRtp(Player player) {
        String command = plugin.roamConfig().menuCommand();
        String first = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        // never run /rtp from /rtp
        if (command.isEmpty() || first.equals("rtp") || first.equals("wild") || first.equals("roam:rtp")) {
            plugin.messages().send(player, "usage-world");
            return;
        }
        player.performCommand(command.replace("%player%", player.getName()));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("roam.admin")) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private boolean teleportOther(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-player");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return true;
        }
        plugin.service().request(target, args.length > 2 ? args[2] : null, true);
        plugin.messages().send(sender, "sent-player", "player", target.getName());
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-reset");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return true;
        }
        plugin.cooldowns().clear(target.getUniqueId());
        plugin.messages().send(sender, "cooldown-reset", "player", target.getName());
        return true;
    }

    /** Shows what a world's settings work out to for the sender, and tries one search. */
    private void info(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        World world = args.length > 1 ? Bukkit.getWorld(args[1]) : (player != null ? player.getWorld() : Bukkit.getWorlds().get(0));
        if (world == null) {
            plugin.messages().send(sender, "unknown-world", "world", args[1]);
            return;
        }

        String target = plugin.roamConfig().resolveWorld(world.getName());
        World landing = Bukkit.getWorld(target);
        if (landing == null) {
            plugin.messages().send(sender, "unknown-world", "world", target);
            return;
        }
        Settings s = plugin.roamConfig().settingsFor(landing, player);

        line(sender, "World", world.getName() + (landing.equals(world) ? "" : " -> " + landing.getName()) + (plugin.roamConfig().isWorldEnabled(world.getName()) ? "" : " (disabled)"));
        line(sender, "Area", s.shape().name().toLowerCase() + ", centre " + s.centerX() + " " + s.centerZ()
                + (s.followsPlayer() ? " (the player)" : "") + ", radius " + s.minRadius() + " to " + s.maxRadius()
                + (s.gaussian() ? ", gaussian" : ", even"));
        line(sender, "Height", s.minY() + " to " + s.maxY() + ", search " + s.search().name().toLowerCase()
                + (s.freeFall() > 0 ? ", free fall " + s.freeFall() : ""));
        line(sender, "Rules", "cooldown " + s.cooldownSeconds() + "s, delay " + s.delaySeconds() + "s, cost " + s.cost());
        line(sender, "Biomes", (s.allowedBiomes().isEmpty() ? "any" : "only " + s.allowedBiomes()) + (s.blockedBiomes().isEmpty() ? "" : ", not " + s.blockedBiomes()));
        line(sender, "Cache", plugin.cache().size(s) + " spots ready");

        long start = System.nanoTime();
        plugin.finder().find(s, () -> false).thenAccept(found -> {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (found.isPresent()) {
                var spot = found.get().spot();
                line(sender, "Test search", "found " + spot.getBlockX() + " " + spot.getBlockY() + " " + spot.getBlockZ()
                        + " after " + found.get().attempts() + " attempt(s), " + ms + "ms");
            } else {
                line(sender, "Test search", "no safe spot found in " + plugin.roamConfig().maxAttempts() + " attempts");
            }
        });
    }

    private void line(CommandSender to, String key, String value) {
        to.sendMessage(plugin.messages().parse("<gray>" + key + ": <white><value>", "value", value));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission("roam.admin");

        if (args.length == 1) {
            if (admin) options.addAll(List.of("player", "info", "reset", "reload"));
            options.addAll(worlds(sender));
        } else if (args.length == 2 && admin) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "player", "reset" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                case "info" -> Bukkit.getWorlds().forEach(w -> options.add(w.getName()));
                default -> { }
            }
        } else if (args.length == 3 && admin && args[0].equalsIgnoreCase("player")) {
            Bukkit.getWorlds().forEach(w -> options.add(w.getName()));
        }

        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }

    /** The worlds the sender can name in /rtp. */
    private List<String> worlds(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.roamConfig().isWorldEnabled(world.getName())) continue;
            if (plugin.roamConfig().perWorldPermission()
                    && !sender.hasPermission("roam.world.*") && !sender.hasPermission("roam.world." + world.getName())) continue;
            names.add(world.getName());
        }
        return names;
    }
}
