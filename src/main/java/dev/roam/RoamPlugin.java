package dev.roam;

import dev.roam.commands.RtpCommand;
import dev.roam.config.RoamConfig;
import dev.roam.hooks.Hooks;
import dev.roam.hooks.RoamExpansion;
import dev.roam.safe.ConfigMigrator;
import dev.roam.safe.Doctor;
import dev.roam.safe.FileBackups;
import dev.roam.safe.Guard;
import dev.roam.safe.Health;
import dev.roam.safe.Prep;
import dev.roam.safe.ServerId;
import dev.roam.rtp.CooldownStore;
import dev.roam.rtp.LocationCache;
import dev.roam.rtp.LocationFinder;
import dev.roam.rtp.RtpService;
import org.bukkit.Bukkit;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Roam: random teleport with simple settings. */
public class RoamPlugin extends JavaPlugin {

    private static final int CONFIG_VERSION = 1;
    private static final int LANG_VERSION = 1;

    private final List<Prep.Spec> files = List.of(
            new Prep.Spec("config.yml", "config-version", CONFIG_VERSION, Prep.configMigrator(CONFIG_VERSION), rules -> {
                rules.range("defaults.radius.min", 0, 1_000_000);
                rules.range("defaults.radius.max", 1, 1_000_000);
                rules.range("defaults.cooldown", 0, 31_536_000);
                rules.range("defaults.delay", 0, 3600);
                rules.range("max-attempts", 1, 10_000);
                rules.range("cache.size", 0, 1000);
            }),
            new Prep.Spec("messages.yml", "lang-version", LANG_VERSION, new ConfigMigrator("lang-version", LANG_VERSION), null));

    private boolean started;
    private RoamConfig config;
    private Messages messages;
    private CooldownStore cooldowns;
    private Hooks hooks;
    private LocationFinder finder;
    private LocationCache cache;
    private RtpService service;

    @Override
    public void onEnable() {
        try {
            enableInner();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Roam could not start, check config.yml and messages.yml for mistakes", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void enableInner() {
        saveDefaultConfig();
        Health.storage("YAML files in the plugin folder (config.yml, messages.yml, cooldowns.yml, data.yml)");
        Prep.startup(this, files);
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
        boolean beacon = getConfig().getBoolean("metrics.enabled", true);
        Metrics.start(this, ServerId.resolve(getDataFolder().toPath(),
                ServerId.inYaml(new File(getDataFolder(), "data.yml").toPath(), getLogger()), beacon, getLogger()));
        started = true;
        Banner.print(this, "Thanks for sending every player somewhere new.");
    }

    @Override
    public void onDisable() {
        if (cache != null) cache.stop();
        // written here, on this thread: the periodic save is synchronized with it, so one that is running is waited for
        if (cooldowns != null) cooldowns.saveIfNeeded();
    }

    /** The text of /roamrtp doctor. */
    public List<String> doctor() {
        List<String> extra = new ArrayList<>(Prep.versionLines(this, files));
        extra.add("Cooldowns kept: " + cooldowns.tracked() + " player(s)");
        extra.add("Pending writes: " + (cooldowns.pending() ? 1 : 0) + " (cooldowns are written about every 30 seconds and when the server stops)");
        return Doctor.report(getName(), getPluginMeta().getVersion(), extra);
    }

    /** /roamrtp backup now: a verified copy of the settings and data files. */
    public boolean backupNow() {
        cooldowns.saveIfNeeded();
        List<String> names = new ArrayList<>(Prep.fileNames(files));
        names.add("cooldowns.yml");
        names.add("data.yml");
        return FileBackups.snapshot(getDataFolder().toPath(), names, 5, getLogger());
    }

    /**
     * Reloads config.yml, messages.yml and the plugin hooks.
     *
     * @return false if a file has a mistake in it: that file is left as it was loaded before
     */
    public boolean reloadAll() {
        if (started) {
            List<Guard.Problem> problems = Prep.validate(this, files);
            if (!problems.isEmpty()) {
                Prep.logRejected(this, problems);
                return false;
            }
        }
        boolean ok = config.load();
        ok &= messages.load();
        cooldowns.keepFor(config.longestCooldown());
        hooks.load();
        finder.reload();
        cache.start();
        return ok;
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
