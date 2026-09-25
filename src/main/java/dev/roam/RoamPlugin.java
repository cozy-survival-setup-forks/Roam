package dev.roam;

import dev.roam.commands.RtpCommand;
import dev.roam.config.RoamConfig;
import dev.roam.hooks.Hooks;
import dev.roam.hooks.RoamExpansion;
import dev.roam.rtp.CooldownStore;
import dev.roam.rtp.LocationCache;
import dev.roam.rtp.LocationFinder;
import dev.roam.rtp.RtpService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Roam: random teleport with simple settings. */
public class RoamPlugin extends JavaPlugin {

    private RoamConfig config;
    private Messages messages;
    private CooldownStore cooldowns;
    private Hooks hooks;
    private LocationFinder finder;
    private LocationCache cache;
    private RtpService service;

    @Override
    public void onEnable() {
        config = new RoamConfig(this);
        messages = new Messages(this);
        cooldowns = new CooldownStore(this);
        hooks = new Hooks(this);
        finder = new LocationFinder(this);
        cache = new LocationCache(this);
        service = new RtpService(this);

        cooldowns.load();

        // Optional plugins load before this one, worlds are loaded by now.
        reloadAll();

        RtpCommand command = new RtpCommand(this);
        for (String name : new String[]{"roamrtp", "roam"}) {
            PluginCommand rtp = getCommand(name);
            if (rtp != null) {
                rtp.setExecutor(command);
                rtp.setTabCompleter(command);
            }
        }
        Bukkit.getPluginManager().registerEvents(new RoamListener(this), this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RoamExpansion(this).register();
        }

        // Cooldowns are written to disk a little after they change.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, cooldowns::saveIfNeeded, 600L, 600L);
    }

    @Override
    public void onDisable() {
        cache.stop();
        cooldowns.saveIfNeeded();
    }

    /** Reloads config.yml, messages.yml and the plugin hooks. */
    public void reloadAll() {
        config.load();
        messages.load();
        hooks.load();
        finder.reload();
        cache.start();
    }

    public RoamConfig roamConfig() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public CooldownStore cooldowns() {
        return cooldowns;
    }

    public Hooks hooks() {
        return hooks;
    }

    public LocationFinder finder() {
        return finder;
    }

    public LocationCache cache() {
        return cache;
    }

    public RtpService service() {
        return service;
    }

}
