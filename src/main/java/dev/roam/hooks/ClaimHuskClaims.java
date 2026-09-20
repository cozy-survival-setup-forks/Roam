package dev.roam.hooks;

import net.william278.huskclaims.BukkitHuskClaims;
import net.william278.huskclaims.api.BukkitHuskClaimsAPI;
import org.bukkit.Location;

/** Only loaded when HuskClaims is installed. */
final class ClaimHuskClaims {

    private ClaimHuskClaims() {
    }

    static boolean isClaimed(Location location) {
        return BukkitHuskClaimsAPI.getInstance().getClaimAt(BukkitHuskClaims.Adapter.adapt(location)).isPresent();
    }
}
