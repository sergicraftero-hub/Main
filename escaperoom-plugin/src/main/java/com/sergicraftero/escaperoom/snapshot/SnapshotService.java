package com.sergicraftero.escaperoom.snapshot;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.room.Room;
import com.sergicraftero.escaperoom.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures and restores named region snapshots (snapshots/&lt;name&gt;.snap). Room snapshots are
 * named after the room; plots captured before a generated build use "&lt;room&gt;_plot".
 * WorldEdit/FAWE is used for room snapshots when installed and enabled in config.yml.
 */
public final class SnapshotService {

    private final EscapeRoomPlugin plugin;
    private final File folder;
    private final File dirtyFile;
    private final Map<String, RegionSnapshot> cache = new ConcurrentHashMap<>();
    private final WorldEditBridge worldEdit;

    public SnapshotService(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "snapshots");
        this.dirtyFile = new File(plugin.getDataFolder(), "dirty-rooms.yml");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create snapshots folder");
        }
        WorldEditBridge bridge = null;
        if (plugin.getConfig().getBoolean("snapshot.use-worldedit", true)
                && (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit"))) {
            try {
                bridge = new WorldEditBridge(plugin);
                plugin.getLogger().info("WorldEdit detected: room snapshots will use schematics.");
            } catch (Throwable t) {
                plugin.getLogger().warning("WorldEdit found but could not be hooked: " + t.getMessage());
            }
        }
        this.worldEdit = bridge;
    }

    private int perTick() {
        return Math.max(500, plugin.getConfig().getInt("snapshot.blocks-per-tick", 6000));
    }

    private File file(String name) {
        return new File(folder, name + ".snap");
    }

    public boolean has(String name) {
        return file(name).exists() || cache.containsKey(name)
                || (worldEdit != null && worldEdit.has(name));
    }

    // ───────────────────────── rooms ─────────────────────────

    public void captureRoom(Room room, Consumer<Boolean> done) {
        World world = room.world();
        if (world == null) {
            done.accept(false);
            return;
        }
        if (worldEdit != null) {
            worldEdit.capture(room.id(), world, room.region(), done);
            return;
        }
        capture(room.id(), world, room.region(), done);
    }

    public void restoreRoom(Room room, Consumer<Boolean> done) {
        World world = room.world();
        if (world != null) {
            clearEntities(world, room.region(), room.id());
        }
        if (worldEdit != null && worldEdit.has(room.id())) {
            worldEdit.restore(room.id(), world, done);
            return;
        }
        restore(room.id(), done);
    }

    // ───────────────────────── generic named snapshots ─────────────────────────

    public void capture(String name, World world, Cuboid region, Consumer<Boolean> done) {
        RegionSnapshot.capture(plugin, world, region, perTick(), snapshot -> {
            cache.put(name, snapshot);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = true;
                try {
                    snapshot.save(file(name));
                } catch (IOException e) {
                    ok = false;
                    plugin.getLogger().severe("Could not save snapshot " + name + ": " + e.getMessage());
                }
                boolean result = ok;
                Bukkit.getScheduler().runTask(plugin, () -> done.accept(result));
            });
        });
    }

    public void restore(String name, Consumer<Boolean> done) {
        RegionSnapshot cached = cache.get(name);
        if (cached != null) {
            cached.restore(plugin, perTick(), () -> done.accept(true));
            return;
        }
        File file = file(name);
        if (!file.exists()) {
            plugin.getLogger().warning("No snapshot named '" + name + "' — the room will not be reset.");
            done.accept(false);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                byte[] raw = RegionSnapshot.readFile(file);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        RegionSnapshot snapshot = RegionSnapshot.load(raw);
                        cache.put(name, snapshot);
                        snapshot.restore(plugin, perTick(), () -> done.accept(true));
                    } catch (IOException e) {
                        plugin.getLogger().severe("Corrupt snapshot " + name + ": " + e.getMessage());
                        done.accept(false);
                    }
                });
            } catch (IOException e) {
                plugin.getLogger().severe("Could not read snapshot " + name + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> done.accept(false));
            }
        });
    }

    public void delete(String name) {
        cache.remove(name);
        if (file(name).exists() && !file(name).delete()) {
            plugin.getLogger().warning("Could not delete snapshot " + name);
        }
        if (worldEdit != null) {
            worldEdit.delete(name);
        }
    }

    /** Removes dropped items and temporary entities created during a game. */
    public void clearEntities(World world, Cuboid region, String roomId) {
        for (Entity entity : world.getNearbyEntities(region.toBoundingBox())) {
            String temp = entity.getPersistentDataContainer().get(plugin.keys().temp, PersistentDataType.STRING);
            if (entity instanceof Item || roomId.equals(temp)) {
                entity.remove();
            }
        }
    }

    // ───────────────────────── crash recovery ─────────────────────────

    public synchronized void markDirty(String roomId, boolean dirty) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dirtyFile);
        List<String> list = new ArrayList<>(yaml.getStringList("rooms"));
        boolean changed = dirty ? !list.contains(roomId) && list.add(roomId) : list.remove(roomId);
        if (!changed) {
            return;
        }
        yaml.set("rooms", list);
        try {
            yaml.save(dirtyFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save dirty-rooms.yml: " + e.getMessage());
        }
    }

    /** Rooms that were mid-game when the server stopped or crashed are restored on startup. */
    public void restoreDirtyRooms() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dirtyFile);
        for (String id : yaml.getStringList("rooms")) {
            Room room = plugin.rooms().get(id);
            if (room == null) {
                markDirty(id, false);
                continue;
            }
            plugin.getLogger().info("Room '" + id + "' was interrupted by a shutdown, restoring it...");
            restoreRoom(room, ok -> markDirty(id, false));
        }
    }
}
