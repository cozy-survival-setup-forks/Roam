package dev.roam.hooks;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

/** Only loaded when GriefPrevention is installed. */
final class ClaimGriefPrevention {

    private ClaimGriefPrevention() {
    }

    static boolean isClaimed(Location location) {
        return GriefPrevention.instance.dataStore.getClaimAt(location, true, null) != null;
    }
}
