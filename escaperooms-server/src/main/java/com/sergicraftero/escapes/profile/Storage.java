package com.sergicraftero.escapes.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sergicraftero.escapes.EscapePlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite (default) or MySQL storage. All writes go through one background thread; profile loads happen at
 * pre-login (already off the main thread).
 */
public final class Storage {

    public record Run(String room, String players, long timeMs, int hints, int stars, long at) {
    }

    public record LevelEntry(String name, long xp) {
    }

    private final EscapePlugin plugin;
    private final Gson gson = new GsonBuilder().create();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EscapeRooms-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;
    private boolean mysql;
    private String url;
    private String user;
    private String password;

    // leaderboard caches (refreshed in the background)
    private final Map<String, List<Run>> bestByRoom = new ConcurrentHashMap<>();
    private final Map<String, List<Run>> weeklyByRoom = new ConcurrentHashMap<>();
    private volatile List<LevelEntry> topLevels = List.of();

    public Storage(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("database");
        String type = c == null ? "sqlite" : c.getString("type", "sqlite");
        mysql = type.equalsIgnoreCase("mysql");
        if (mysql) {
            url = "jdbc:mysql://" + c.getString("host", "localhost") + ":" + c.getInt("port", 3306) + "/"
                    + c.getString("database", "escaperooms") + "?useSSL=" + c.getBoolean("ssl", false)
                    + "&autoReconnect=true&characterEncoding=utf8";
            user = c.getString("user", "root");
            password = c.getString("password", "");
        } else {
            File file = new File(plugin.getDataFolder(), "data.db");
            url = "jdbc:sqlite:" + file.getAbsolutePath();
        }
        connect();
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS er_profiles (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), "
                    + "xp BIGINT, data " + (mysql ? "MEDIUMTEXT" : "TEXT") + ", updated BIGINT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS er_runs (id " + (mysql ? "BIGINT AUTO_INCREMENT" : "INTEGER")
                    + " PRIMARY KEY " + (mysql ? "" : "AUTOINCREMENT") + ", room VARCHAR(64), players VARCHAR(255), "
                    + "time_ms BIGINT, hints INT, stars INT, private_game INT, at BIGINT)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS er_runs_room ON er_runs (room, time_ms)".replace(
                    "IF NOT EXISTS ", mysql ? "" : "IF NOT EXISTS "));
        } catch (SQLException e) {
            if (!mysql || !e.getMessage().toLowerCase().contains("duplicate")) {
                throw e;
            }
        }
    }

    private void connect() throws SQLException {
        try {
            Class.forName(mysql ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC"); // drivers bundled with Paper
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + e.getMessage());
        }
        connection = mysql ? DriverManager.getConnection(url, user, password) : DriverManager.getConnection(url);
        if (!mysql) {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
            }
        }
    }

    private synchronized Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || (mysql && !connection.isValid(2))) {
            connect();
        }
        return connection;
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // closing anyway
        }
    }

    // ───────────────────────── profiles ─────────────────────────

    /** Blocking load (call off the main thread). Never returns null. */
    public synchronized PlayerProfile load(UUID uuid, String name) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT data FROM er_profiles WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerProfile p = gson.fromJson(rs.getString(1), PlayerProfile.class);
                    if (p != null) {
                        p.repair();
                        p.uuid = uuid;
                        p.name = name;
                        return p;
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().severe("Could not load profile of " + name + ": " + e.getMessage());
        }
        PlayerProfile p = new PlayerProfile();
        p.uuid = uuid;
        p.name = name;
        p.firstJoin = System.currentTimeMillis();
        p.dirty = true;
        return p;
    }

    public void saveAsync(PlayerProfile p) {
        String json = gson.toJson(p);
        UUID uuid = p.uuid;
        String name = p.name;
        long xp = p.xp;
        p.dirty = false;
        executor.execute(() -> save(uuid, name, xp, json));
    }

    public void saveSync(PlayerProfile p) {
        save(p.uuid, p.name, p.xp, gson.toJson(p));
        p.dirty = false;
    }

    private synchronized void save(UUID uuid, String name, long xp, String json) {
        String sql = mysql
                ? "INSERT INTO er_profiles (uuid, name, xp, data, updated) VALUES (?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE name=VALUES(name), xp=VALUES(xp), data=VALUES(data), updated=VALUES(updated)"
                : "INSERT INTO er_profiles (uuid, name, xp, data, updated) VALUES (?,?,?,?,?) "
                + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, xp=excluded.xp, data=excluded.data, updated=excluded.updated";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, xp);
            ps.setString(4, json);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save profile of " + name + ": " + e.getMessage());
        }
    }

    /** Loads an offline player's profile by name (admin commands). Blocking. */
    public synchronized UUID findByName(String name) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT uuid FROM er_profiles WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Lookup failed: " + e.getMessage());
        }
        return null;
    }

    public void runAsync(Runnable r) {
        executor.execute(r);
    }

    // ───────────────────────── runs & leaderboards ─────────────────────────

    public void recordRun(String room, List<String> players, long timeMs, int hints, int stars, boolean privateGame) {
        String names = String.join(", ", players);
        if (names.length() > 250) {
            names = names.substring(0, 247) + "...";
        }
        String n = names;
        executor.execute(() -> {
            synchronized (this) {
                try (PreparedStatement ps = conn().prepareStatement(
                        "INSERT INTO er_runs (room, players, time_ms, hints, stars, private_game, at) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setString(1, room);
                    ps.setString(2, n);
                    ps.setLong(3, timeMs);
                    ps.setInt(4, hints);
                    ps.setInt(5, stars);
                    ps.setInt(6, privateGame ? 1 : 0);
                    ps.setLong(7, System.currentTimeMillis());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Could not record run: " + e.getMessage());
                }
            }
            refreshRoom(room);
        });
    }

    public void refreshAllAsync(List<String> rooms) {
        executor.execute(() -> {
            for (String r : rooms) {
                refreshRoom(r);
            }
            refreshLevels();
        });
    }

    private synchronized void refreshRoom(String room) {
        bestByRoom.put(room, queryRuns(room, 0));
        weeklyByRoom.put(room, queryRuns(room, System.currentTimeMillis() - 7L * 24 * 3600 * 1000));
    }

    private List<Run> queryRuns(String room, long since) {
        List<Run> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT players, time_ms, hints, stars, at FROM er_runs WHERE room = ? AND at >= ? ORDER BY time_ms ASC LIMIT 10")) {
            ps.setString(1, room);
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Run(room, rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getLong(5)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Leaderboard query failed: " + e.getMessage());
        }
        return out;
    }

    private synchronized void refreshLevels() {
        List<LevelEntry> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT name, xp FROM er_profiles ORDER BY xp DESC LIMIT 10");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new LevelEntry(rs.getString(1), rs.getLong(2)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Level leaderboard failed: " + e.getMessage());
        }
        topLevels = out;
    }

    public List<Run> best(String room) {
        return bestByRoom.getOrDefault(room, List.of());
    }

    public List<Run> weekly(String room) {
        return weeklyByRoom.getOrDefault(room, List.of());
    }

    public List<LevelEntry> topLevels() {
        return topLevels;
    }
}
