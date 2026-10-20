package com.sergicraftero.escaperoom.room;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import org.bukkit.Location;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Loads, stores and indexes rooms (plugins/EscapeRoom/rooms/*.yml). */
public final class RoomManager {

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9-]{1,32}");

    private final EscapeRoomPlugin plugin;
    private final File folder;
    private final Map<String, Room> rooms = new LinkedHashMap<>();

    public RoomManager(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "rooms");
    }

    public void loadAll() {
        rooms.clear();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create rooms folder");
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Room room = Room.load(file, plugin.getLogger());
                rooms.put(room.id(), room);
            } catch (Exception e) {
                plugin.getLogger().severe("Could not load room " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + rooms.size() + " escape room(s).");
    }

    /** Re-reads a single room file (after it was generated or edited by hand). */
    public Room reload(String id) {
        File file = fileFor(id);
        if (!file.exists()) {
            rooms.remove(id);
            return null;
        }
        Room room = Room.load(file, plugin.getLogger());
        rooms.put(room.id(), room);
        return room;
    }

    public File fileFor(String id) {
        return new File(folder, id.toLowerCase(Locale.ROOT) + ".yml");
    }

    public void register(Room room) {
        rooms.put(room.id(), room);
    }

    public boolean delete(String id) {
        Room room = rooms.remove(id);
        return room != null && room.file().delete();
    }

    public Room get(String id) {
        return id == null ? null : rooms.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Room> all() {
        return Collections.unmodifiableCollection(rooms.values());
    }

    public Room roomAt(Location location) {
        for (Room room : rooms.values()) {
            if (room.contains(location)) {
                return room;
            }
        }
        return null;
    }
}
