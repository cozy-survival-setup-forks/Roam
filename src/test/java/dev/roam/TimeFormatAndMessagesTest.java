package dev.roam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatAndMessagesTest {

    @Test
    void timeIsShort() {
        assertEquals("0s", TimeFormat.format(0));
        assertEquals("30s", TimeFormat.format(30_000));
        assertEquals("2s", TimeFormat.format(1_001)); // Rounded up, so the player never sees 0s while waiting.
        assertEquals("1m 5s", TimeFormat.format(65_000));
        assertEquals("1h 1m", TimeFormat.format(3_660_000));
        assertEquals("1d 2h", TimeFormat.format(93_600_000));
    }

    @Test
    void oldColourCodesBecomeTags() {
        assertEquals("<#ff8800>Hi", Messages.convertLegacy("&#ff8800Hi"));
        assertEquals("<gray>a<red>b<bold>c<reset>", Messages.convertLegacy("&7a&cb&lc&r"));
        assertEquals("Salt&Pepper", Messages.convertLegacy("Salt&Pepper"));
    }
}
