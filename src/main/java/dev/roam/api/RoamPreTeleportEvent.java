package dev.roam.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a safe spot was found and the player is about to be teleported (and charged).
 * Cancel it to stop the teleport, or change the destination.
 */
public class RoamPreTeleportEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private Location destination;
    private boolean cancelled = false;

    public RoamPreTeleportEvent(Player player, Location destination) {
        this.player = player;
        this.destination = destination;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getDestination() {
        return destination.clone();
    }

    public void setDestination(Location destination) {
        this.destination = destination.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
