package dev.roam.hooks;

import org.bukkit.entity.Player;

/** What Roam needs from an economy. */
public interface Money {

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    void deposit(Player player, double amount);

    String format(double amount);
}
