package dev.roam;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesAndMessagesTest {

    private ServerMock server;
    private RoamPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(RoamPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMessageMissingFromAnOldFileFallsBackToTheBundledText() throws Exception {
        Files.writeString(new File(plugin.getDataFolder(), "messages.yml").toPath(), "prefix: \"\"\nteleported: \"Hi\"\n");
        assertTrue(plugin.messages().load());
        assertTrue(plugin.messages().has("charged"));
    }

    @Test
    void aMessageSetToNothingStaysOff() throws Exception {
        Files.writeString(new File(plugin.getDataFolder(), "messages.yml").toPath(), "charged: \"\"\n");
        plugin.messages().load();
        assertFalse(plugin.messages().has("charged"));
    }

    @Test
    void aBrokenFileKeepsWhatWasLoadedAndReloadSaysSo() throws Exception {
        File messages = new File(plugin.getDataFolder(), "messages.yml");
        Files.writeString(messages.toPath(), "teleported: [unclosed\n");
        assertFalse(plugin.messages().load());
        assertTrue(plugin.messages().has("charged"));

        plugin.getConfig().set("worlds.world.enabled", false);
        Files.writeString(new File(plugin.getDataFolder(), "config.yml").toPath(), "defaults: {unclosed\n");
        assertFalse(plugin.roamConfig().load());
        assertFalse(plugin.roamConfig().isWorldEnabled("world"));
    }

    @Test
    void aRedirectNeedsPermissionForWhereItLeads() {
        server.addSimpleWorld("lobby");
        server.addSimpleWorld("wild");
        plugin.getConfig().set("per-world-permission", true);
        plugin.getConfig().set("worlds.lobby.redirect", "wild");
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "roam.world.lobby", true);

        plugin.service().request(player, "lobby", false);

        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.nextComponentMessage());
        assertTrue(text.contains("wild"), text);
        assertEquals(0, plugin.cooldowns().remainingMillis(player.getUniqueId(), "wild", false, 60));
    }
}
