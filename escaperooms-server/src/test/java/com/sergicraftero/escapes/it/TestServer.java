package com.sergicraftero.escapes.it;

import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** MockBukkit server with the few extra bits the plugin needs. */
public class TestServer extends ServerMock {

    private final File container;

    public TestServer() {
        try {
            container = Files.createTempDirectory("er-worlds").toFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public File getWorldContainer() {
        return container;
    }

    /** Server-wide messages go to every online player (and nowhere else). */
    @Override
    public Iterable<? extends net.kyori.adventure.audience.Audience> audiences() {
        return getOnlinePlayers();
    }

    private final org.mockbukkit.mockbukkit.inventory.ItemFactoryMock items = new StrictItemFactory();

    /** Like a real server, items with different names or plugin data don't stack. */
    @Override
    public org.mockbukkit.mockbukkit.inventory.ItemFactoryMock getItemFactory() {
        return items;
    }

    private static final class StrictItemFactory extends org.mockbukkit.mockbukkit.inventory.ItemFactoryMock {
        @Override
        public boolean equals(org.bukkit.inventory.meta.ItemMeta a, org.bukkit.inventory.meta.ItemMeta b) {
            if (a == null || b == null) {
                return a == b || super.equals(a, b);
            }
            return super.equals(a, b) && java.util.Objects.equals(a.displayName(), b.displayName())
                    && java.util.Objects.equals(a.getPersistentDataContainer(), b.getPersistentDataContainer());
        }
    }

    @Override
    public org.bukkit.World createWorld(org.bukkit.WorldCreator creator) {
        TestWorld world = new TestWorld(creator);
        addWorld(world);
        return world;
    }
}
