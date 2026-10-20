package com.sergicraftero.escaperoom.storage;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Statistics and rankings in SQLite. All queries run on a single background thread; results are
 * cached in memory so placeholders and holograms never touch the database on the main thread.
 */
public final class StatsStorage {

    public record TopEntry(String players, long timeMillis, int hints, long endedAt) {
    }

    public record PlayerStats(int plays, int wins, Map<String, Long> personalBest) {
        public static final PlayerStats EMPTY = new PlayerStats(0, 0, Map.of());
    }

    private final EscapeRoomPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EscapeRoom-SQLite");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    private final Map<String, List<TopEntry>> topCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerStats> playerCache = new ConcurrentHashMap<>();

    public StatsStorage(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        executor.execute(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                File file = new File(plugin.getDataFolder(), "stats.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate("PRAGMA journal_mode=WAL");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS runs ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, room TEXT NOT NULL, time_ms INTEGER NOT NULL, "
                            + "hints INTEGER NOT NULL, won INTEGER NOT NULL, players TEXT NOT NULL, "
                            + "player_count INTEGER NOT NULL, ended_at INTEGER NOT NULL)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS run_players ("
                            + "run_id INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL, won INTEGER NOT NULL)");
                    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_runs_room ON runs(room, won, time_ms)");
                    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rp_uuid ON run_players(uuid)");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Could not open SQLite database: " + e.getMessage());
            }
        });
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
            // shutting down anyway
        }
    }

    // ───────────────────────── writes ─────────────────────────

    /**
     * Records a finished run. The callback receives the run's ranking position (1 = best) for
     * victories, or 0, and is invoked on the main thread.
     */
    public void recordRun(String room, Map<UUID, String> roster, long timeMillis, int hints, boolean won,
                          IntConsumer rankCallback) {
        String names = String.join(", ", roster.values());
        executor.execute(() -> {
            int rank = 0;
            if (connection == null) {
                return;
            }
            try {
                long runId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO runs(room, time_ms, hints, won, players, player_count, ended_at) VALUES (?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, room);
                    ps.setLong(2, timeMillis);
                    ps.setInt(3, hints);
                    ps.setInt(4, won ? 1 : 0);
                    ps.setString(5, names);
                    ps.setInt(6, roster.size());
                    ps.setLong(7, System.currentTimeMillis());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        runId = keys.next() ? keys.getLong(1) : -1;
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO run_players(run_id, uuid, name, won) VALUES (?,?,?,?)")) {
                    for (Map.Entry<UUID, String> e : roster.entrySet()) {
                        ps.setLong(1, runId);
                        ps.setString(2, e.getKey().toString());
                        ps.setString(3, e.getValue());
                        ps.setInt(4, won ? 1 : 0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                if (won) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT COUNT(*) FROM runs WHERE room=? AND won=1 AND time_ms < ?")) {
                        ps.setString(1, room);
                        ps.setLong(2, timeMillis);
                        try (ResultSet rs = ps.executeQuery()) {
                            rank = rs.next() ? rs.getInt(1) + 1 : 0;
                        }
                    }
                }
                loadTopSync(room);
                for (UUID uuid : roster.keySet()) {
                    loadPlayerSync(uuid);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not record run: " + e.getMessage());
            }
            int finalRank = rank;
            Bukkit.getScheduler().runTask(plugin, () -> {
                rankCallback.accept(finalRank);
                plugin.leaderboards().refresh(room);
            });
        });
    }

    // ───────────────────────── reads ─────────────────────────

    public void refreshTop(String room, Runnable then) {
        executor.execute(() -> {
            loadTopSync(room);
            if (then != null) {
                Bukkit.getScheduler().runTask(plugin, then);
            }
        });
    }

    public void refreshPlayer(UUID uuid) {
        executor.execute(() -> loadPlayerSync(uuid));
    }

    public void forgetPlayer(UUID uuid) {
        playerCache.remove(uuid);
    }

    private void loadTopSync(String room) {
        if (connection == null) {
            return;
        }
        int size = Math.max(1, plugin.getConfig().getInt("leaderboard.size", 10));
        List<TopEntry> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT players, time_ms, hints, ended_at FROM runs WHERE room=? AND won=1 ORDER BY time_ms ASC LIMIT ?")) {
            ps.setString(1, room);
            ps.setInt(2, Math.max(size, 10));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TopEntry(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getLong(4)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load ranking for " + room + ": " + e.getMessage());
        }
        topCache.put(room, Collections.unmodifiableList(list));
    }

    private void loadPlayerSync(UUID uuid) {
        if (connection == null) {
            return;
        }
        int plays = 0;
        int wins = 0;
        Map<String, Long> best = new ConcurrentHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(won),0) FROM run_players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    plays = rs.getInt(1);
                    wins = rs.getInt(2);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load stats: " + e.getMessage());
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT r.room, MIN(r.time_ms) FROM runs r JOIN run_players p ON p.run_id = r.id "
                        + "WHERE p.uuid=? AND r.won=1 GROUP BY r.room")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    best.put(rs.getString(1), rs.getLong(2));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load personal bests: " + e.getMessage());
        }
        playerCache.put(uuid, new PlayerStats(plays, wins, best));
    }

    /** Async lookup by player name (for /escape stats &lt;player&gt;). */
    public void lookupByName(String name, java.util.function.Consumer<PlayerStats> callback) {
        executor.execute(() -> {
            UUID uuid = null;
            if (connection != null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT uuid FROM run_players WHERE lower(name)=lower(?) LIMIT 1")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            uuid = UUID.fromString(rs.getString(1));
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("Lookup failed: " + e.getMessage());
                }
            }
            PlayerStats stats = PlayerStats.EMPTY;
            if (uuid != null) {
                loadPlayerSync(uuid);
                stats = playerCache.getOrDefault(uuid, PlayerStats.EMPTY);
            }
            PlayerStats result = stats;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    // ───────────────────────── cache accessors (main thread safe) ─────────────────────────

    public List<TopEntry> top(String room) {
        return topCache.getOrDefault(room, List.of());
    }

    public PlayerStats player(UUID uuid) {
        return playerCache.getOrDefault(uuid, PlayerStats.EMPTY);
    }
}
