package dev.roam.hooks;

import net.william278.husktowns.api.BukkitHuskTownsAPI;
import org.bukkit.Location;

/** Only loaded when HuskTowns is installed. */
final class ClaimHuskTowns {

    private ClaimHuskTowns() {
    }

    static boolean isClaimed(Location location) {
        return BukkitHuskTownsAPI.getInstance().getClaimAt(location).isPresent();
    }
}
