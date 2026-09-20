package dev.roam.rtp;

import dev.roam.RoamPlugin;
import dev.roam.TimeFormat;
import dev.roam.api.RoamPreTeleportEvent;
import dev.roam.api.RoamTeleportEvent;
import dev.roam.config.Settings;
import dev.roam.hooks.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One random teleport from the command to the landing: permissions, cooldown, cost, the warm up
 * delay, finding a spot, teleporting and the effects afterwards.
 */
public final class RtpService {

    /** A player waiting out the delay. */
    private final class Session {
        final Player player;
        final Location start;
        final Settings settings;
        final CompletableFuture<Optional<LocationFinder.Found>> search;
        final boolean free;
        boolean cancelled = false;
        BukkitTask task;
        int ticks = 0;

        Session(Player player, Settings settings, CompletableFuture<Optional<LocationFinder.Found>> search, boolean free) {
            this.player = player;
            this.start = player.getLocation();
            this.settings = settings;
            this.search = search;
            this.free = free;
        }
    }

    private final RoamPlugin plugin;
    private final Map<UUID, Session> waiting = new HashMap<>();
    private final Map<UUID, Boolean> busy = new HashMap<>();
    private final Map<UUID, Long> invulnerableUntil = new HashMap<>();
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();

    public RtpService(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- starting a teleport ----

    /**
     * @param worldName the world the player asked for, or null for the world they are in
     * @param forced    an admin or the server sends the player: no permissions, cooldown, cost or delay
     */
    public void request(Player player, @Nullable String worldName, boolean forced) {
        var config = plugin.roamConfig();
        var messages = plugin.messages();
        UUID id = player.getUniqueId();

        if (busy.containsKey(id)) {
            messages.send(player, "already-teleporting");
            return;
        }
        if (!forced && !player.hasPermission("roam.use")) {
            messages.send(player, "no-permission");
            return;
        }

        World asked = worldName == null ? player.getWorld() : Bukkit.getWorld(worldName);
        if (asked == null) {
            messages.send(player, "unknown-world", "world", worldName == null ? "" : worldName);
            return;
        }
        if (!forced && worldName != null && config.perWorldPermission() && !canUseWorld(player, asked.getName())) {
            messages.send(player, "no-world-permission", "world", asked.getName());
            return;
        }

        World target = Bukkit.getWorld(config.resolveWorld(asked.getName()));
        if (target == null) {
            messages.send(player, "unknown-world", "world", config.resolveWorld(asked.getName()));
            return;
        }
        if (!config.isWorldEnabled(asked.getName()) || !config.isWorldEnabled(target.getName())) {
            if (!forced) {
                messages.send(player, "world-disabled", "world", asked.getName());
                return;
            }
        }

        Settings settings = config.settingsFor(target, player);
        boolean skipCooldown = forced || player.hasPermission("roam.bypass.cooldown");
        boolean skipCost = forced || player.hasPermission("roam.bypass.cost");
        boolean skipDelay = forced || player.hasPermission("roam.bypass.delay");

        if (!skipCooldown) {
            long left = plugin.cooldowns().remainingMillis(id, target.getName(), config.cooldownPerWorld(), settings.cooldownSeconds());
            if (left > 0) {
                messages.send(player, "cooldown", "time", TimeFormat.format(left));
                return;
            }
        }

        Money money = plugin.hooks().money();
        double cost = skipCost || money == null ? 0 : settings.cost();
        if (cost > 0 && !money.has(player, cost)) {
            messages.send(player, "not-enough-money", "cost", money.format(cost));
            return;
        }

        busy.put(id, true);
        plugin.cache().register(settings);

        CompletableFuture<Optional<LocationFinder.Found>> search = search(settings);
        int delay = skipDelay ? 0 : settings.delaySeconds();
        if (delay <= 0) {
            search.whenComplete((found, error) -> finish(player, settings, error != null ? Optional.empty() : found, cost, !skipCooldown));
            return;
        }

        Session session = new Session(player, settings, search, false);
        waiting.put(id, session);
        messages.send(player, "delay-start", "seconds", String.valueOf(delay));
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session, delay, cost, !skipCooldown), 5L, 5L);
    }

    private CompletableFuture<Optional<LocationFinder.Found>> search(Settings settings) {
        if (settings.followsPlayer()) return plugin.finder().find(settings, () -> false);

        // A spot that was found in advance, or a fresh search if there is none.
        return plugin.cache().pollAsync(settings).thenCompose(ready -> {
            if (ready.isPresent()) {
                return CompletableFuture.completedFuture(Optional.of(new LocationFinder.Found(ready.get(), 0)));
            }
            return plugin.finder().find(settings, () -> false);
        });
    }

    private boolean canUseWorld(Player player, String world) {
        return player.hasPermission("roam.world.*") || player.hasPermission("roam.world." + world);
    }

    // ---- the delay ----

    private void tick(Session session, int delaySeconds, double cost, boolean setCooldown) {
        Player player = session.player;
        if (session.cancelled || !player.isOnline()) {
            end(session);
            return;
        }

        if (plugin.roamConfig().cancelOnMove() && moved(session.start, player.getLocation())) {
            cancel(player, "delay-cancelled-move");
            return;
        }

        session.ticks += 5;
        if (session.ticks % 20 == 0) {
            int left = delaySeconds - session.ticks / 20;
            if (left > 0) {
                player.sendActionBar(plugin.messages().component("countdown", "seconds", String.valueOf(left)));
                sound(player, "countdown");
            }
        }

        if (session.ticks >= delaySeconds * 20) {
            end(session);
            if (!session.search.isDone()) plugin.messages().send(player, "still-searching");
            session.search.whenComplete((found, error) -> finish(player, session.settings, error != null ? Optional.empty() : found, cost, setCooldown));
        }
    }

    private boolean moved(Location from, Location to) {
        return from.getWorld() != to.getWorld()
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    /** Stops a waiting teleport. Does nothing if the player is not waiting. */
    public void cancel(Player player, String reasonKey) {
        Session session = waiting.get(player.getUniqueId());
        if (session == null) return;
        session.cancelled = true;
        end(session);
        busy.remove(player.getUniqueId());
        plugin.messages().send(player, reasonKey);
    }

    public boolean isWaiting(Player player) {
        return waiting.containsKey(player.getUniqueId());
    }

    private void end(Session session) {
        if (session.task != null) session.task.cancel();
        waiting.remove(session.player.getUniqueId());
        if (session.cancelled) busy.remove(session.player.getUniqueId());
    }

    // ---- landing ----

    /** Lands the player. Whatever goes wrong, the player must not stay stuck as "already teleporting". */
    private void finish(Player player, Settings settings, Optional<LocationFinder.Found> found, double cost, boolean setCooldown) {
        try {
            land(player, settings, found, cost, setCooldown);
        } catch (Throwable problem) {
            busy.remove(player.getUniqueId());
            plugin.getLogger().warning("A random teleport for " + player.getName() + " failed: " + problem);
        }
    }

    private void land(Player player, Settings settings, Optional<LocationFinder.Found> found, double cost, boolean setCooldown) {
        UUID id = player.getUniqueId();
        var messages = plugin.messages();

        if (!player.isOnline()) {
            busy.remove(id);
            return;
        }
        if (found.isEmpty()) {
            messages.send(player, "failed");
            busy.remove(id);
            return;
        }

        Location destination = found.get().spot().clone();
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        if (settings.freeFall() > 0) {
            destination.setY(Math.min(destination.getY() + settings.freeFall(), settings.world().getMaxHeight() - 1));
        }

        RoamPreTeleportEvent pre = new RoamPreTeleportEvent(player, destination);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) {
            busy.remove(id);
            return;
        }
        Location target = pre.getDestination();

        Money money = plugin.hooks().money();
        boolean charged = false;
        if (cost > 0 && money != null) {
            if (!money.withdraw(player, cost)) {
                messages.send(player, "not-enough-money", "cost", money.format(cost));
                busy.remove(id);
                return;
            }
            charged = true;
        }

        if (player.isInsideVehicle()) player.leaveVehicle();
        Location from = player.getLocation();
        boolean refund = charged;
        player.teleportAsync(target).whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                if (refund && money != null && player.isOnline()) money.deposit(player, cost);
                messages.send(player, "failed");
                busy.remove(id);
                return;
            }
            try {
                arrived(player, settings, from, target, cost, refund, setCooldown);
            } finally {
                busy.remove(id);
            }
        }));
    }

    private void arrived(Player player, Settings settings, Location from, Location to, double cost, boolean paid, boolean setCooldown) {
        UUID id = player.getUniqueId();
        var config = plugin.roamConfig();
        var messages = plugin.messages();

        if (setCooldown && settings.cooldownSeconds() > 0) {
            plugin.cooldowns().mark(id, settings.world().getName(), config.cooldownPerWorld());
        }

        long now = System.currentTimeMillis();
        if (config.invulnerableSeconds() > 0) invulnerableUntil.put(id, now + config.invulnerableSeconds() * 1000L);
        if (settings.freeFall() > 0) {
            // Falling from the chosen height must not hurt. This ends when they land.
            fallProtectedUntil.put(id, now + 60_000L);
            watchLanding(player);
        }

        sound(player, "arrive");
        String x = String.valueOf(to.getBlockX());
        String y = String.valueOf(to.getBlockY());
        String z = String.valueOf(to.getBlockZ());
        if (messages.has("title.title") || messages.has("title.subtitle")) {
            player.showTitle(Title.title(
                    messages.component("title.title", "world", settings.world().getName(), "x", x, "y", y, "z", z),
                    messages.component("title.subtitle", "world", settings.world().getName(), "x", x, "y", y, "z", z),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
        messages.send(player, "teleported", "world", settings.world().getName(), "x", x, "y", y, "z", z);
        if (paid && plugin.hooks().money() != null) {
            messages.send(player, "charged", "cost", plugin.hooks().money().format(cost));
        }

        for (String command : settings.commands()) {
            String line = command
                    .replace("%player%", player.getName())
                    .replace("%world%", settings.world().getName())
                    .replace("%x%", x).replace("%y%", y).replace("%z%", z);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }

        Bukkit.getPluginManager().callEvent(new RoamTeleportEvent(player, from, to));
        busy.remove(id);
    }

    /** Removes the fall protection soon after the player touches the ground. */
    private void watchLanding(Player player) {
        int[] ticks = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks[0] += 5;
            boolean landed = ticks[0] >= 15 && player.isOnGround();
            if (!player.isOnline() || landed || ticks[0] > 1200) {
                fallProtectedUntil.remove(player.getUniqueId());
                task[0].cancel();
            }
        }, 5L, 5L);
    }

    private void sound(Player player, String name) {
        String key = plugin.roamConfig().sound(name);
        if (!key.isBlank()) player.playSound(player.getLocation(), key.trim(), 1f, 1f);
    }

    // ---- protection while landing ----

    /** True if damage should be cancelled: the short invulnerability, or a fall from free fall. */
    public boolean blocksDamage(UUID player, boolean fall) {
        long now = System.currentTimeMillis();
        Long until = invulnerableUntil.get(player);
        if (until != null) {
            if (now < until) return true;
            invulnerableUntil.remove(player);
        }
        if (fall) {
            Long protectedUntil = fallProtectedUntil.get(player);
            if (protectedUntil != null) {
                if (now < protectedUntil) return true;
                fallProtectedUntil.remove(player);
            }
        }
        return false;
    }

    public void forget(UUID player) {
        Session session = waiting.remove(player);
        if (session != null && session.task != null) session.task.cancel();
        busy.remove(player);
        invulnerableUntil.remove(player);
        fallProtectedUntil.remove(player);
    }
}
