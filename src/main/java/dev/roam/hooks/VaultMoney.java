package dev.roam.hooks;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/** Vault economy. Only loaded when Vault is installed. */
final class VaultMoney implements Money {

    private final Economy economy;

    private VaultMoney(Economy economy) {
        this.economy = economy;
    }

    static @Nullable Money create() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : new VaultMoney(provider.getProvider());
    }

    @Override
    public boolean has(Player player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public void deposit(Player player, double amount) {
        economy.depositPlayer(player, amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
