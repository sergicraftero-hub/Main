package com.sergicraftero.escapes.progress;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.profile.PlayerProfile;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Levels, ranks, rewards, daily reward, quests and achievements — all driven by {@code progression.yml}.
 */
public final class ProgressService {

    private final EscapePlugin plugin;
    private YamlConfiguration yml;
    private ZoneId zone;

    public ProgressService(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration yml) {
        this.yml = yml;
        String tz = plugin.getConfig().getString("timezone", "Europe/Madrid");
        try {
            zone = ZoneId.of(tz);
        } catch (RuntimeException e) {
            zone = ZoneId.systemDefault();
        }
    }

    public YamlConfiguration yml() {
        return yml;
    }

    // ───────────────────────── levels ─────────────────────────

    public int maxLevel() {
        return yml.getInt("levels.max-level", 100);
    }

    /** Total XP needed to reach {@code level} (level 1 = 0). */
    public long xpFor(int level) {
        double base = yml.getDouble("levels.base-xp", 400);
        double exp = yml.getDouble("levels.exponent", 1.4);
        long total = 0;
        for (int l = 1; l < level; l++) {
            total += (long) Math.round(base * Math.pow(l, exp));
        }
        return total;
    }

    public int level(long xp) {
        int level = 1;
        while (level < maxLevel() && xp >= xpFor(level + 1)) {
            level++;
        }
        return level;
    }

    public int level(Player p) {
        return level(plugin.profiles().get(p).xp);
    }

    /** 0..1 progress inside the current level. */
    public double levelProgress(long xp) {
        int lvl = level(xp);
        if (lvl >= maxLevel()) {
            return 1;
        }
        long a = xpFor(lvl);
        long b = xpFor(lvl + 1);
        return (double) (xp - a) / Math.max(1, b - a);
    }

    public ConfigurationSection rank(int level) {
        ConfigurationSection best = null;
        int bestLevel = -1;
        for (Map<?, ?> m : yml.getMapList("ranks")) {
            ConfigurationSection s = com.sergicraftero.escapes.util.Yml.section(m);
            int min = s.getInt("min-level", 1);
            if (level >= min && min > bestLevel) {
                best = s;
                bestLevel = min;
            }
        }
        return best;
    }

    public String rankPrefix(Player p) {
        ConfigurationSection r = rank(level(p));
        return r == null ? "" : r.getString("prefix", "");
    }

    public String rankName(int level) {
        ConfigurationSection r = rank(level);
        return r == null ? "" : r.getString("name", "");
    }

    private double multiplier(Player p) {
        double m = 1;
        ConfigurationSection s = yml.getConfigurationSection("multipliers");
        if (s != null) {
            for (String perm : s.getKeys(false)) {
                if (p.hasPermission(perm.replace('-', '.'))) {
                    m = Math.max(m, s.getDouble(perm));
                }
            }
        }
        return m * plugin.getConfig().getDouble("events.xp-multiplier", 1.0);
    }

    public void addXp(Player p, long amount, boolean applyMultiplier) {
        if (amount <= 0) {
            return;
        }
        PlayerProfile prof = plugin.profiles().get(p);
        long gained = applyMultiplier ? Math.round(amount * multiplier(p)) : amount;
        int before = level(prof.xp);
        prof.xp += gained;
        prof.dirty = true;
        int after = level(prof.xp);
        if (after > before) {
            for (int l = before + 1; l <= after; l++) {
                levelUp(p, prof, l);
            }
        }
        plugin.lobby().refresh(p);
    }

    private void levelUp(Player p, PlayerProfile prof, int level) {
        long keys = yml.getLong("levels.keys-per-level", 25);
        ConfigurationSection special = yml.getConfigurationSection("levels.rewards." + level);
        long gems = 0;
        if (special != null) {
            keys += special.getLong("keys", 0);
            gems += special.getLong("gems", 0);
            for (String cmd : special.getStringList("commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            }
            String msg = special.getString("message");
            if (msg != null) {
                p.sendMessage(Msg.parse(msg));
            }
        }
        prof.keys += keys;
        prof.gems += gems;
        Title t = Title.title(Msg.get("level.up-title", "level", level), Msg.get("level.up-subtitle",
                "level", level, "keys", keys, "rank", Msg.parse(rankName(level))), Title.Times.times(
                Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(800)));
        p.showTitle(t);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Msg.send(p, "level.up-chat", "level", level, "keys", keys, "gems", gems);
        ConfigurationSection oldRank = rank(level - 1);
        ConfigurationSection newRank = rank(level);
        if (newRank != null && newRank != oldRank && (oldRank == null
                || !newRank.getString("name", "").equals(oldRank.getString("name", "")))) {
            Bukkit.getServer().sendMessage(Msg.get("level.rank-broadcast", "player", p.getName(),
                    "rank", Msg.parse(newRank.getString("name", ""))));
        }
        checkAchievements(p);
    }

    public void addKeys(Player p, long amount) {
        PlayerProfile prof = plugin.profiles().get(p);
        prof.keys += Math.round(amount * plugin.getConfig().getDouble("events.keys-multiplier", 1.0));
        prof.dirty = true;
        plugin.lobby().refresh(p);
    }

    // ───────────────────────── game results ─────────────────────────

    public void puzzleSolved(Player p, RoomDef room, PuzzleDef def) {
        PlayerProfile prof = plugin.profiles().get(p);
        prof.addStat("puzzles", 1);
        addXp(p, def.xp(), true);
        quest(p, "solve_puzzles", 1, room);
    }

    public void roomCompleted(Game game, List<Player> team, long timeMs, int stars, Map<UUID, Integer> solvedBy) {
        RoomDef room = game.room();
        boolean noHints = game.hintsUsed() == 0;
        for (Player p : team) {
            PlayerProfile prof = plugin.profiles().get(p);
            PlayerProfile.RoomRecord rec = prof.room(room.id);
            boolean first = rec.wins == 0;
            rec.plays++;
            rec.wins++;
            boolean record = rec.bestTimeMs == 0 || timeMs < rec.bestTimeMs;
            if (record) {
                rec.bestTimeMs = timeMs;
            }
            int newStars = Math.max(0, stars - rec.bestStars);
            rec.bestStars = Math.max(rec.bestStars, stars);
            prof.addStat("wins", 1);
            prof.addStat("plays", 1);
            prof.addStat("wing_" + room.wing + "_wins", 1);
            if (stars >= 3) {
                prof.addStat("three_stars", 1);
            }
            if (noHints) {
                prof.addStat("no_hint_wins", 1);
            }
            if (team.size() >= 2) {
                prof.addStat("party_games", 1);
            }
            prof.addStat("hints_used", game.hintsUsed());

            long xp = room.rewardXp + (long) room.starXp * stars;
            long keys = room.rewardKeys;
            if (first) {
                xp += room.firstXp;
                keys += room.firstKeys;
            }
            addXp(p, xp, true);
            addKeys(p, keys);
            Msg.send(p, "rewards.win", "xp", xp, "keys", keys, "first", first ? Msg.raw("rewards.first-time") : "",
                    "record", record ? Msg.raw("rewards.new-record") : "", "newstars", newStars);
            for (String cmd : room.rewardCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            }
            quest(p, "win_rooms", 1, room);
            quest(p, "play_rooms", 1, room);
            quest(p, "earn_stars", stars, room);
            if (noHints) {
                quest(p, "win_no_hints", 1, room);
            }
            if (team.size() >= 2) {
                quest(p, "play_with_party", 1, room);
            }
            quest(p, "win_wing", 1, room);
            checkAchievements(p);
            prof.dirty = true;
        }
    }

    public void roomFailed(Game game, List<Player> team, Map<UUID, Integer> solvedBy) {
        RoomDef room = game.room();
        for (Player p : team) {
            PlayerProfile prof = plugin.profiles().get(p);
            PlayerProfile.RoomRecord rec = prof.room(room.id);
            rec.plays++;
            rec.fails++;
            prof.addStat("plays", 1);
            prof.addStat("fails", 1);
            long xp = (long) (room.rewardXp * yml.getDouble("rewards.fail-xp-fraction", 0.25)
                    * game.solvedCount() / Math.max(1, room.mainPuzzles().size()));
            addXp(p, xp, true);
            Msg.send(p, "rewards.lose", "xp", xp);
            quest(p, "play_rooms", 1, room);
            checkAchievements(p);
        }
    }

    // ───────────────────────── daily reward ─────────────────────────

    public long today() {
        return LocalDate.now(zone).toEpochDay();
    }

    public long week() {
        LocalDate d = LocalDate.now(zone);
        return d.getYear() * 100L + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public boolean canClaimDaily(Player p) {
        return plugin.profiles().get(p).lastDailyDay != today();
    }

    /** The streak day the player would claim now (1-based, wraps around the configured rewards). */
    public int nextStreakDay(Player p) {
        PlayerProfile prof = plugin.profiles().get(p);
        boolean continues = prof.lastDailyDay == today() - 1;
        int streak = continues ? prof.dailyStreak + 1 : 1;
        if (prof.lastDailyDay == today()) {
            streak = prof.dailyStreak;
        }
        return streak;
    }

    public List<Map<?, ?>> dailyRewards() {
        return yml.getMapList("daily.rewards");
    }

    public void claimDaily(Player p) {
        PlayerProfile prof = plugin.profiles().get(p);
        if (prof.lastDailyDay == today()) {
            Msg.send(p, "daily.already");
            return;
        }
        int streak = nextStreakDay(p);
        List<Map<?, ?>> rewards = dailyRewards();
        if (rewards.isEmpty()) {
            return;
        }
        ConfigurationSection r = com.sergicraftero.escapes.util.Yml.section(rewards.get((streak - 1) % rewards.size()));
        double bonus = 1;
        String vipPerm = yml.getString("daily.vip-bonus.permission", "escaperooms.vip");
        if (p.hasPermission(vipPerm)) {
            bonus = yml.getDouble("daily.vip-bonus.multiplier", 2.0);
        }
        long keys = Math.round(r.getLong("keys", 0) * bonus);
        long gems = Math.round(r.getLong("gems", 0) * bonus);
        long xp = Math.round(r.getLong("xp", 0) * bonus);
        prof.lastDailyDay = today();
        prof.dailyStreak = streak;
        prof.keys += keys;
        prof.gems += gems;
        prof.dirty = true;
        addXp(p, xp, false);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        Msg.send(p, "daily.claimed", "day", streak, "keys", keys, "gems", gems, "xp", xp);
        checkAchievements(p);
        plugin.lobby().refresh(p);
    }

    // ───────────────────────── quests ─────────────────────────

    /** Rolls new daily/weekly quests when the day/week changed. */
    public void ensureQuests(Player p) {
        PlayerProfile prof = plugin.profiles().get(p);
        Random r = new Random(p.getUniqueId().getLeastSignificantBits() ^ today());
        if (prof.questDay != today()) {
            prof.questDay = today();
            prof.dailyQuests = pick(yml.getConfigurationSection("quests.daily"), yml.getInt("quests.daily-count", 3), r);
            for (String q : prof.dailyQuests) {
                prof.questProgress.remove("d:" + q);
                prof.questClaimed.remove("d:" + q);
            }
            prof.dirty = true;
        }
        if (prof.questWeek != week()) {
            prof.questWeek = week();
            prof.weeklyQuests = pick(yml.getConfigurationSection("quests.weekly"), yml.getInt("quests.weekly-count", 3), r);
            for (String q : prof.weeklyQuests) {
                prof.questProgress.remove("w:" + q);
                prof.questClaimed.remove("w:" + q);
            }
            prof.dirty = true;
        }
    }

    private static List<String> pick(ConfigurationSection pool, int count, Random r) {
        if (pool == null) {
            return new ArrayList<>();
        }
        List<String> ids = new ArrayList<>(pool.getKeys(false));
        Collections.shuffle(ids, r);
        return new ArrayList<>(ids.subList(0, Math.min(count, ids.size())));
    }

    public ConfigurationSection questDef(boolean daily, String id) {
        return yml.getConfigurationSection("quests." + (daily ? "daily." : "weekly.") + id);
    }

    public void quest(Player p, String type, int amount, RoomDef room) {
        ensureQuests(p);
        PlayerProfile prof = plugin.profiles().get(p);
        advance(p, prof, true, prof.dailyQuests, type, amount, room);
        advance(p, prof, false, prof.weeklyQuests, type, amount, room);
    }

    private void advance(Player p, PlayerProfile prof, boolean daily, List<String> quests, String type, int amount,
                         RoomDef room) {
        for (String id : quests) {
            ConfigurationSection q = questDef(daily, id);
            if (q == null || !type.equals(q.getString("type"))) {
                continue;
            }
            String wing = q.getString("wing");
            if (wing != null && (room == null || !wing.equals(room.wing))) {
                continue;
            }
            String key = (daily ? "d:" : "w:") + id;
            int target = q.getInt("amount", 1);
            int before = prof.questProgress.getOrDefault(key, 0);
            if (before >= target) {
                continue;
            }
            int now = Math.min(target, before + amount);
            prof.questProgress.put(key, now);
            prof.dirty = true;
            if (now >= target) {
                Msg.send(p, "quests.completed", "quest", Msg.parse(q.getString("name", id)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            }
        }
    }

    public int questProgress(Player p, boolean daily, String id) {
        return plugin.profiles().get(p).questProgress.getOrDefault((daily ? "d:" : "w:") + id, 0);
    }

    public boolean questClaimed(Player p, boolean daily, String id) {
        return plugin.profiles().get(p).questClaimed.contains((daily ? "d:" : "w:") + id);
    }

    public void claimQuest(Player p, boolean daily, String id) {
        ConfigurationSection q = questDef(daily, id);
        if (q == null) {
            return;
        }
        PlayerProfile prof = plugin.profiles().get(p);
        String key = (daily ? "d:" : "w:") + id;
        if (prof.questClaimed.contains(key) || prof.questProgress.getOrDefault(key, 0) < q.getInt("amount", 1)) {
            return;
        }
        prof.questClaimed.add(key);
        long xp = q.getLong("reward.xp", 0);
        long keys = q.getLong("reward.keys", 0);
        long gems = q.getLong("reward.gems", 0);
        prof.gems += gems;
        prof.addStat("quests", 1);
        addKeys(p, keys);
        addXp(p, xp, false);
        Msg.send(p, "quests.claimed", "quest", Msg.parse(q.getString("name", id)), "xp", xp, "keys", keys, "gems", gems);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        checkAchievements(p);
    }

    // ───────────────────────── achievements ─────────────────────────

    public long statValue(Player p, String stat) {
        PlayerProfile prof = plugin.profiles().get(p);
        return switch (stat) {
            case "level" -> level(prof.xp);
            case "stars" -> prof.totalStars();
            case "rooms_completed" -> prof.roomsCompleted();
            case "daily_streak" -> prof.dailyStreak;
            case "playtime_hours" -> prof.playtimeMs / 3_600_000L;
            default -> prof.stat(stat);
        };
    }

    public void checkAchievements(Player p) {
        ConfigurationSection all = yml.getConfigurationSection("achievements");
        if (all == null) {
            return;
        }
        PlayerProfile prof = plugin.profiles().get(p);
        for (String id : all.getKeys(false)) {
            if (prof.achievements.contains(id)) {
                continue;
            }
            ConfigurationSection a = all.getConfigurationSection(id);
            if (a == null) {
                continue;
            }
            if (statValue(p, a.getString("stat", "wins")) >= a.getLong("amount", 1)) {
                prof.achievements.add(id);
                prof.dirty = true;
                long keys = a.getLong("reward.keys", 0);
                long gems = a.getLong("reward.gems", 0);
                long xp = a.getLong("reward.xp", 0);
                prof.keys += keys;
                prof.gems += gems;
                String cosmetic = a.getString("reward.cosmetic");
                if (cosmetic != null) {
                    prof.ownedCosmetics.add(cosmetic);
                }
                Component name = Msg.parse(a.getString("name", id));
                Msg.send(p, "achievements.unlocked", "achievement", name, "description",
                        Msg.parse(a.getString("description", "")), "keys", keys, "gems", gems);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (a.getBoolean("broadcast", false)) {
                    Bukkit.getServer().sendMessage(Msg.get("achievements.broadcast", "player", p.getName(), "achievement", name));
                }
                if (xp > 0) {
                    addXp(p, xp, false); // may unlock level achievements: recursion ends because ids are recorded first
                }
            }
        }
    }

    public String formatPlaytime(long ms) {
        return Text.duration(ms);
    }
}
