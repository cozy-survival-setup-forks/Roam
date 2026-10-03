package dev.roam.hooks;

import dev.roam.RoamPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Optional plugins: Vault for the cost, and claim plugins so nobody lands inside a claim.
 * Each one is only touched when it is installed, so Roam runs fine without all of them.
 */
public final class Hooks {

    private final RoamPlugin plugin;
    private @Nullable Money money;
    private final List<Predicate<Location>> claimChecks = new ArrayList<>();

    public Hooks(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        money = null;
        claimChecks.clear();

        if (enabled("Vault")) {
            money = VaultMoney.create();
            if (money == null) plugin.getLogger().info("Vault is installed but no economy plugin is, costs are ignored.");
        }

        if (enabled("WorldGuard")) add("WorldGuard", location -> ClaimWorldGuard.isClaimed(location));
        if (enabled("GriefPrevention")) add("GriefPrevention", location -> ClaimGriefPrevention.isClaimed(location));
        if (enabled("HuskClaims")) add("HuskClaims", location -> ClaimHuskClaims.isClaimed(location));
        if (enabled("HuskTowns")) add("HuskTowns", location -> ClaimHuskTowns.isClaimed(location));
    }

    private boolean enabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    private void add(String name, Predicate<Location> check) {
        AtomicBoolean broken = new AtomicBoolean(false);
        claimChecks.add(location -> {
            // if the check cannot be trusted the spot counts as claimed: better no teleport than one into a claim
            if (broken.get()) return true;
            try {
                return check.test(location);
            } catch (Throwable error) {
                broken.set(true);
                plugin.getLogger().severe("The " + name + " check failed (" + error.getClass().getSimpleName() + "), so every spot is refused until"
                        + " /roam reload. Set avoid-claims: false in config.yml to teleport without checking claims.");
                return true;
            }
        });
        plugin.getLogger().info("Avoiding claims from " + name + ".");
    }

    public @Nullable Money money() {
        return money;
    }

    /** True if the spot is inside a claim or region of any installed claim plugin. */
    public boolean isClaimed(Location location) {
        for (Predicate<Location> check : claimChecks) {
            if (check.test(location)) return true;
        }
        return false;
    }
}
