package dev.roam.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import org.bukkit.Location;

/** Any WorldGuard region counts as claimed. Only loaded when WorldGuard is installed. */
final class ClaimWorldGuard {

    private ClaimWorldGuard() {
    }

    static boolean isClaimed(Location location) {
        var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return query.getApplicableRegions(BukkitAdapter.adapt(location)).size() > 0;
    }
}
