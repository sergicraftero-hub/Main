package com.sergicraftero.escaperoom.display;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.room.Room;
import com.sergicraftero.escaperoom.storage.StatsStorage;
import com.sergicraftero.escaperoom.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ranking holograms (TextDisplay) placed at each room's leaderboard location. */
public final class LeaderboardService {

    private final EscapeRoomPlugin plugin;
    private final Map<String, TextDisplay> displays = new HashMap<>();

    public LeaderboardService(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
        // Holograms can only be spawned in loaded chunks: retry the missing ones now and then.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Room room : plugin.rooms().all()) {
                TextDisplay display = displays.get(room.id());
                Location loc = room.leaderboard();
                if ((display == null || !display.isValid()) && loc != null && loc.getWorld() != null
                        && loc.isChunkLoaded()) {
                    refresh(room.id());
                }
            }
        }, 200L, 200L);
    }

    public void refreshAll() {
        for (Room room : plugin.rooms().all()) {
            plugin.stats().refreshTop(room.id(), () -> refresh(room.id()));
        }
    }

    public void refresh(String roomId) {
        Room room = plugin.rooms().get(roomId);
        if (room == null) {
            remove(roomId);
            return;
        }
        Location loc = room.leaderboard();
        if (loc == null || loc.getWorld() == null || !loc.isChunkLoaded()) {
            remove(roomId);
            return;
        }
        TextDisplay display = displays.get(roomId);
        if (display == null || !display.isValid() || !display.getLocation().getWorld().equals(loc.getWorld())
                || display.getLocation().distanceSquared(loc) > 0.01) {
            remove(roomId);
            for (Entity old : loc.getWorld().getNearbyEntities(loc, 2, 4, 2)) {
                if (roomId.equals(old.getPersistentDataContainer().get(plugin.keys().leaderboard, PersistentDataType.STRING))) {
                    old.remove();
                }
            }
            display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBackgroundColor(Color.fromARGB(150, 12, 8, 4));
                d.setShadowed(true);
                d.setLineWidth(260);
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.getPersistentDataContainer().set(plugin.keys().leaderboard, PersistentDataType.STRING, roomId);
            });
            displays.put(roomId, display);
        }
        display.text(render(room));
    }

    private Component render(Room room) {
        int size = Math.max(1, plugin.getConfig().getInt("leaderboard.size", 10));
        List<StatsStorage.TopEntry> top = plugin.stats().top(room.id());
        Component text = plugin.messages().get("leaderboard-header", Messages.phm("room", room.displayName()));
        if (top.isEmpty()) {
            return text.append(Component.newline()).append(plugin.messages().get("leaderboard-empty"));
        }
        for (int i = 0; i < Math.min(size, top.size()); i++) {
            StatsStorage.TopEntry e = top.get(i);
            text = text.append(Component.newline()).append(plugin.messages().get("leaderboard-line",
                    Messages.ph("rank", i + 1), Messages.ph("players", e.players()),
                    Messages.ph("time", Text.formatTimePrecise(e.timeMillis())), Messages.ph("hints", e.hints())));
        }
        return text;
    }

    public void remove(String roomId) {
        TextDisplay display = displays.remove(roomId);
        if (display != null) {
            display.remove();
        }
    }

    public void removeAll() {
        displays.values().forEach(Entity::remove);
        displays.clear();
    }
}
