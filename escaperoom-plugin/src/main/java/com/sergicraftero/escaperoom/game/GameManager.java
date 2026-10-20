package com.sergicraftero.escaperoom.game;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.room.Room;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Owns one {@link Game} per room and the global tick loop. */
public final class GameManager {

    private final EscapeRoomPlugin plugin;
    private final Map<String, Game> games = new LinkedHashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private final BukkitTask ticker;

    public GameManager(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Game game : games.values()) {
                if (game.isRunning()) {
                    game.tick();
                }
            }
        }, 1L, 1L);
    }

    public Game game(Room room) {
        Game game = games.get(room.id());
        if (game == null) {
            game = new Game(plugin, this, room);
            games.put(room.id(), game);
        } else if (game.room() != room) {
            game.room(room);
        }
        return game;
    }

    public Game existing(String roomId) {
        return games.get(roomId);
    }

    public Collection<Game> games() {
        return games.values();
    }

    public Game gameOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public Game gameOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    /** True if the player is inside a running/ending game (not just queued). */
    public boolean isPlaying(Player player) {
        Game game = gameOf(player);
        return game != null && game.isPlaying(player.getUniqueId());
    }

    void index(UUID uuid, Game game) {
        byPlayer.put(uuid, game);
    }

    void unindex(UUID uuid) {
        byPlayer.remove(uuid);
    }

    public boolean join(Player player, Room room) {
        Game current = gameOf(player);
        if (current != null) {
            plugin.messages().send(player, "join-already-other",
                    com.sergicraftero.escaperoom.config.Messages.phm("room", current.room().displayName()));
            return false;
        }
        return game(room).join(player);
    }

    public void leave(Player player, boolean quitting) {
        Game game = gameOf(player);
        if (game != null) {
            game.leave(player, quitting);
        }
    }

    /** Stops and forgets the game of a room (room deleted or reloaded). */
    public void remove(String roomId) {
        Game game = games.remove(roomId);
        if (game != null) {
            game.stop();
            game.shutdown();
        }
    }

    public void shutdown() {
        ticker.cancel();
        for (Game game : games.values()) {
            game.shutdown();
        }
        byPlayer.clear();
    }
}
