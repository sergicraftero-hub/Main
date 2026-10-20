package com.sergicraftero.escaperoom;

import com.sergicraftero.escaperoom.build.BuildService;
import com.sergicraftero.escaperoom.command.EscapeCommand;
import com.sergicraftero.escaperoom.command.Selections;
import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.display.AmbientService;
import com.sergicraftero.escaperoom.display.LeaderboardService;
import com.sergicraftero.escaperoom.game.GameManager;
import com.sergicraftero.escaperoom.game.InventoryStore;
import com.sergicraftero.escaperoom.hook.VaultHook;
import com.sergicraftero.escaperoom.listener.PlayerListener;
import com.sergicraftero.escaperoom.listener.RoomListener;
import com.sergicraftero.escaperoom.room.Room;
import com.sergicraftero.escaperoom.room.RoomManager;
import com.sergicraftero.escaperoom.snapshot.SnapshotService;
import com.sergicraftero.escaperoom.storage.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * EscapeRoom: turns regions of the world into playable escape rooms and can generate the
 * "Torre del Alquimista" map with all its puzzles.
 */
public final class EscapeRoomPlugin extends JavaPlugin {

    private static EscapeRoomPlugin instance;

    private Keys keys;
    private Messages messages;
    private InventoryStore inventories;
    private StatsStorage stats;
    private SnapshotService snapshots;
    private RoomManager rooms;
    private GameManager games;
    private LeaderboardService leaderboards;
    private AmbientService ambient;
    private BuildService builds;
    private Selections selections;
    private VaultHook vault;

    public static EscapeRoomPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        keys = new Keys(this);
        messages = new Messages(this);
        messages.load();
        inventories = new InventoryStore(this);
        stats = new StatsStorage(this);
        stats.init();
        snapshots = new SnapshotService(this);
        rooms = new RoomManager(this);
        rooms.loadAll();
        games = new GameManager(this);
        leaderboards = new LeaderboardService(this);
        ambient = new AmbientService(this);
        builds = new BuildService(this);
        selections = new Selections();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new RoomListener(this), this);

        PluginCommand command = getCommand("escape");
        if (command != null) {
            EscapeCommand executor = new EscapeCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new com.sergicraftero.escaperoom.hook.EscapePlaceholders(this).register();
                getLogger().info("PlaceholderAPI placeholders registered (%escaperoom_...%).");
            } catch (Throwable t) {
                getLogger().warning("Could not register placeholders: " + t.getMessage());
            }
        }

        // Things that need worlds and other plugins fully loaded.
        Bukkit.getScheduler().runTask(this, () -> {
            vault = VaultHook.tryHook(getLogger());
            removeOrphanTempEntities();
            snapshots.restoreDirtyRooms();
            leaderboards.refreshAll();
            for (Player player : Bukkit.getOnlinePlayers()) {
                stats.refreshPlayer(player.getUniqueId());
            }
        });
    }

    @Override
    public void onDisable() {
        if (builds != null) {
            builds.shutdown();
        }
        if (games != null) {
            games.shutdown();
        }
        if (ambient != null) {
            ambient.shutdown();
        }
        if (leaderboards != null) {
            leaderboards.removeAll();
        }
        if (stats != null) {
            stats.close();
        }
        instance = null;
    }

    /** Displays spawned mid-game are not persistent, but clean up anything left after a crash. */
    private void removeOrphanTempEntities() {
        for (Room room : rooms.all()) {
            World world = room.world();
            if (world == null) {
                continue;
            }
            for (Entity entity : world.getNearbyEntities(room.region().toBoundingBox())) {
                if (entity.getPersistentDataContainer().has(keys.temp, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    public Keys keys() {
        return keys;
    }

    public Messages messages() {
        return messages;
    }

    public InventoryStore inventories() {
        return inventories;
    }

    public StatsStorage stats() {
        return stats;
    }

    public SnapshotService snapshots() {
        return snapshots;
    }

    public RoomManager rooms() {
        return rooms;
    }

    public GameManager games() {
        return games;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    public BuildService builds() {
        return builds;
    }

    public Selections selections() {
        return selections;
    }

    public VaultHook vault() {
        return vault;
    }
}
