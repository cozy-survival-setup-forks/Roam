package dev.roam;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Teleporting new players and respawning players, and the protection right after a teleport. */
public final class RoamListener implements Listener {

    private final RoamPlugin plugin;

    public RoamListener(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var config = plugin.roamConfig();
        if (!config.firstJoinEnabled() || player.hasPlayedBefore()) return;
        // A moment later, so the player has finished loading in.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.service().request(player, config.firstJoinWorld(), true);
        }, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var config = plugin.roamConfig();
        if (!config.onDeathEnabled()) return;
        boolean hasSpawn = event.isBedSpawn() || event.isAnchorSpawn();
        if (config.onDeathRespectBed() && hasSpawn) return;

        Player player = event.getPlayer();
        String world = event.getRespawnLocation().getWorld().getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.service().request(player, world, true);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (plugin.service().blocksDamage(player.getUniqueId(), event.getCause() == EntityDamageEvent.DamageCause.FALL)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.roamConfig().cancelOnDamage() && plugin.service().isWaiting(player) && event.getFinalDamage() > 0) {
            plugin.service().cancel(player, "delay-cancelled-damage");
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.cache().forget(event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.cache().watch(event.getWorld());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.service().forget(event.getPlayer().getUniqueId());
    }
}
