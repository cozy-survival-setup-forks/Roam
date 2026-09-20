package dev.roam.rtp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownStoreTest {

    @TempDir
    File dir;

    private CooldownStore store() {
        return new CooldownStore(new File(dir, "cooldowns.yml"), Logger.getLogger("test"));
    }

    @Test
    void cooldownCountsDown() {
        CooldownStore store = store();
        UUID player = UUID.randomUUID();

        store.mark(player, "world", false);
        long now = System.currentTimeMillis();

        long left = store.remainingMillis(player, "world", false, 60, now);
        assertTrue(left > 59_000 && left <= 60_000, "left " + left);
        assertEquals(0, store.remainingMillis(player, "world", false, 60, now + 61_000));
        assertEquals(0, store.remainingMillis(player, "world", false, 0, now)); // no cooldown set
    }

    @Test
    void oneCooldownForAllWorldsUnlessPerWorld() {
        CooldownStore store = store();
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();

        store.mark(player, "world", false);
        assertTrue(store.remainingMillis(player, "nether", false, 60, now) > 0);

        UUID other = UUID.randomUUID();
        store.mark(other, "world", true);
        assertTrue(store.remainingMillis(other, "world", true, 60, now) > 0);
        assertEquals(0, store.remainingMillis(other, "nether", true, 60, now));
    }

    @Test
    void cooldownsSurviveARestart() {
        UUID player = UUID.randomUUID();
        CooldownStore first = store();
        first.mark(player, "world", false);
        first.saveIfNeeded();

        CooldownStore second = store();
        second.load();

        assertTrue(second.remainingMillis(player, "world", false, 600) > 0);
    }

    @Test
    void clearRemovesTheCooldown() {
        CooldownStore store = store();
        UUID player = UUID.randomUUID();
        store.mark(player, "world", false);

        store.clear(player);

        assertEquals(0, store.remainingMillis(player, "world", false, 600));
    }
}
