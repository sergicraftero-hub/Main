package com.sergicraftero.escapes.it;

import com.sergicraftero.escapes.EscapePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Boots the plugin on MockBukkit. */
class PluginIT {

    private ServerMock server;
    private EscapePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        plugin = MockBukkit.load(EscapePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enablesAndLoadsRooms() {
        assertTrue(plugin.isEnabled(), "plugin disabled itself");
        assertTrue(plugin.rooms().all().size() >= 3, "rooms not loaded");
        PlayerMock p = new TestPlayer(server, "Tester");
        server.addPlayer(p);
        server.getScheduler().performTicks(60);
        assertNotNull(plugin.profiles().get(p));
        assertTrue(plugin.lobby().inLobby(p), "player not sent to the lobby");
    }
}
