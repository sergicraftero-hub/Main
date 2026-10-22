package com.sergicraftero.escapes.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the server remembers about a player. Stored as JSON (one row per player). */
public final class PlayerProfile {

    /** Progress on one room. */
    public static final class RoomRecord {
        public int plays;
        public int wins;
        public int fails;
        public long bestTimeMs;
        public int bestStars;
    }

    public transient UUID uuid; // the row key; Gson cannot reflect into java.util.UUID
    public String name;
    public long xp;
    public long keys;
    public long gems;
    public long firstJoin;
    public long lastJoin;
    public long playtimeMs;

    public Map<String, RoomRecord> rooms = new HashMap<>();
    public Set<String> ownedRooms = new HashSet<>();
    public Set<String> ownedCosmetics = new HashSet<>();
    public Map<String, String> equipped = new HashMap<>();
    public Set<String> achievements = new HashSet<>();
    public Map<String, Long> stats = new HashMap<>();
    public Map<String, Boolean> settings = new HashMap<>();

    // daily reward
    public long lastDailyDay = -1;
    public int dailyStreak;

    // quests
    public long questDay = -1;
    public long questWeek = -1;
    public List<String> dailyQuests = new ArrayList<>();
    public List<String> weeklyQuests = new ArrayList<>();
    public Map<String, Integer> questProgress = new HashMap<>();
    public Set<String> questClaimed = new HashSet<>();

    // season pass
    public String season = "";
    public long seasonXp;
    public boolean seasonPremium;
    public Set<Integer> seasonClaimed = new HashSet<>();

    public transient boolean dirty;
    public transient long sessionStart;

    public RoomRecord room(String id) {
        return rooms.computeIfAbsent(id, k -> new RoomRecord());
    }

    public long stat(String key) {
        return stats.getOrDefault(key, 0L);
    }

    public void addStat(String key, long amount) {
        stats.merge(key, amount, Long::sum);
        dirty = true;
    }

    public boolean setting(String key, boolean def) {
        return settings.getOrDefault(key, def);
    }

    public int totalStars() {
        int n = 0;
        for (RoomRecord r : rooms.values()) {
            n += r.bestStars;
        }
        return n;
    }

    public int roomsCompleted() {
        int n = 0;
        for (RoomRecord r : rooms.values()) {
            if (r.wins > 0) {
                n++;
            }
        }
        return n;
    }

    public boolean completed(String room) {
        RoomRecord r = rooms.get(room);
        return r != null && r.wins > 0;
    }

    /** Fills fields that may be null after loading an older JSON. */
    void repair() {
        if (rooms == null) {
            rooms = new HashMap<>();
        }
        if (ownedRooms == null) {
            ownedRooms = new HashSet<>();
        }
        if (ownedCosmetics == null) {
            ownedCosmetics = new HashSet<>();
        }
        if (equipped == null) {
            equipped = new HashMap<>();
        }
        if (achievements == null) {
            achievements = new HashSet<>();
        }
        if (stats == null) {
            stats = new HashMap<>();
        }
        if (settings == null) {
            settings = new HashMap<>();
        }
        if (dailyQuests == null) {
            dailyQuests = new ArrayList<>();
        }
        if (weeklyQuests == null) {
            weeklyQuests = new ArrayList<>();
        }
        if (questProgress == null) {
            questProgress = new HashMap<>();
        }
        if (questClaimed == null) {
            questClaimed = new HashSet<>();
        }
        if (seasonClaimed == null) {
            seasonClaimed = new HashSet<>();
        }
        if (season == null) {
            season = "";
        }
    }
}
