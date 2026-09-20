package dev.roam.rtp;

import dev.roam.RoamPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of a teleport that do not need real terrain: the rules that stop it before a search starts. */
class RtpFlowTest {

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

    private static String plain(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void aCooldownStopsTheTeleportAndSaysHowLongIsLeft() {
        PlayerMock player = server.addPlayer();
        plugin.cooldowns().mark(player.getUniqueId(), "world", false);

        plugin.service().request(player, null, false);

        String message = plain(player.nextComponentMessage());
        assertTrue(message.contains("again in"), message);
        assertTrue(message.contains("59s") || message.contains("1m") || message.contains("60s"), message);
    }

    @Test
    void anUnknownWorldIsReported() {
        PlayerMock player = server.addPlayer();

        plugin.service().request(player, "nowhere", false);

        assertTrue(plain(player.nextComponentMessage()).contains("nowhere"));
    }

    @Test
    void aPlayerWithoutTheUsePermissionIsTurnedAway() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "roam.use", false);

        plugin.service().request(player, null, false);

        assertTrue(plain(player.nextComponentMessage()).contains("permission"));
    }

    @Test
    void worldPermissionsOnlyApplyWhenTurnedOn() {
        server.addSimpleWorld("mining");

        // Off by default, so nothing stops a player from naming another world.
        assertFalse(plugin.roamConfig().perWorldPermission());

        plugin.getConfig().set("per-world-permission", true);
        PlayerMock other = server.addPlayer();
        plugin.service().request(other, "mining", false);
        assertTrue(plain(other.nextComponentMessage()).contains("can't use"));
    }

    @Test
    void aDisabledWorldIsRefused() {
        PlayerMock player = server.addPlayer();
        plugin.getConfig().set("worlds.world.enabled", false);

        plugin.service().request(player, null, false);

        assertTrue(plain(player.nextComponentMessage()).contains("turned off"));
    }
}
