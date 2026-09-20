package dev.roam.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Called after a player was teleported by Roam. */
public class RoamTeleportEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location from;
    private final Location to;

    public RoamTeleportEvent(Player player, Location from, Location to) {
        this.player = player;
        this.from = from;
        this.to = to;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getFrom() {
        return from.clone();
    }

    public Location getTo() {
        return to.clone();
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
