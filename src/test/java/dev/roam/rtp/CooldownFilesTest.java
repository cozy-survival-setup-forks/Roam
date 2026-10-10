package dev.roam.rtp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownFilesTest {

    @TempDir
    File dir;

    private CooldownStore store() {
        return new CooldownStore(new File(dir, "cooldowns.yml"), Logger.getLogger("test"));
    }

    private long files(String prefix) {
        return java.util.Arrays.stream(Objects.requireNonNull(dir.list())).filter(n -> n.startsWith(prefix)).count();
    }

    @Test
    void aBrokenFileIsKeptNotOverwritten() throws Exception {
        Files.writeString(new File(dir, "cooldowns.yml").toPath(), "a: [unclosed\n");
        CooldownStore store = store();
        store.load();
        assertEquals(1, files("cooldowns.yml.broken-"));

        store.mark(UUID.randomUUID(), "world", false);
        store.saveIfNeeded();
        assertEquals(1, files("cooldowns.yml.broken-"));
        assertTrue(new File(dir, "cooldowns.yml").exists());
        assertEquals(0, files("cooldowns.yml.tmp"));
    }

    @Test
    void aDamagedFileComesBackFromItsBackup() throws Exception {
        CooldownStore store = store();
        UUID player = UUID.randomUUID();
        store.mark(player, "world", false);
        store.saveIfNeeded();
        store.mark(UUID.randomUUID(), "world", false); // a second save makes the first one the backup
        store.saveIfNeeded();
        assertTrue(new File(dir, "cooldowns.yml.bak").exists());

        Files.writeString(new File(dir, "cooldowns.yml").toPath(), "a: [unclosed\n");
        CooldownStore again = store();
        again.load();
        assertTrue(again.remainingMillis(player, "world", false, 3600, System.currentTimeMillis()) > 0);
        assertEquals(1, files("cooldowns.yml.broken-"));
    }

    @Test
    void aLongCooldownSurvivesARestart() {
        CooldownStore store = store();
        store.keepFor(30L * 24 * 60 * 60);
        UUID player = UUID.randomUUID();
        store.mark(player, "world", false);
        store.saveIfNeeded();

        CooldownStore again = store();
        again.load();
        long left = again.remainingMillis(player, "world", false, 30 * 24 * 60 * 60, System.currentTimeMillis());
        assertTrue(left > 29L * 24 * 60 * 60 * 1000, "left " + left);
    }
}
