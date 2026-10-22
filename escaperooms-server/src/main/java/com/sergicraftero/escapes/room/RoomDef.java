package com.sergicraftero.escapes.room;

import com.sergicraftero.escapes.action.Action;
import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.util.ItemSpec;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Everything about one escape room, read from {@code rooms/<id>.yml}. */
public final class RoomDef {

    public final String id;
    public final YamlConfiguration yml;
    public final String name;
    public final String wing;
    public final String difficulty;
    public final int order;
    public final List<String> description;
    public final ItemSpec icon;
    public final String template;
    public final boolean enabled;
    public final int minPlayers;
    public final int maxPlayers;
    public final int recommendedPlayers;
    public final long timeLimitMs;
    public final int estimatedMinutes;

    // access
    public final boolean premium;
    public final int priceGems;
    public final int priceKeys;
    public final String accessPermission;
    public final int unlockLevel;
    public final List<String> unlockRooms;

    // atmosphere
    public final long playerTime;
    public final boolean rain;
    public final String spawn;
    public final int voidY;
    public final List<String> intro;
    public final long introInterval;

    // scoring
    public final long twoStarMs;
    public final int twoStarHints;
    public final long threeStarMs;
    public final int threeStarHints;
    public final int maxHints;
    public final long hintPenaltyMs;
    public final long wrongPenaltyMs;

    // rewards
    public final int rewardXp;
    public final int rewardKeys;
    public final int firstXp;
    public final int firstKeys;
    public final int starXp;
    public final List<String> rewardCommands;

    public final Map<String, ItemSpec> items = new LinkedHashMap<>();
    public final List<String> startItems;
    public final List<ConfigurationSection> zones;
    public final List<ConfigurationSection> props;
    public final List<ConfigurationSection> recipes;
    public final List<ConfigurationSection> timeline;
    public final List<ConfigurationSection> ambience;
    public final Map<String, PuzzleDef> puzzles = new LinkedHashMap<>();
    public final List<Action> onStart;
    public final List<Action> onWin;

    public RoomDef(String id, YamlConfiguration y, Logger log) {
        this.id = id;
        this.yml = y;
        name = y.getString("name", id);
        wing = y.getString("wing", "originales");
        difficulty = y.getString("difficulty", "media");
        order = y.getInt("order", 100);
        description = y.getStringList("description");
        ConfigurationSection iconSec = y.getConfigurationSection("icon");
        icon = iconSec == null ? ItemSpec.simple(id, Material.MAP) : ItemSpec.parse("icon_" + id, iconSec);
        template = y.getString("template", id);
        enabled = y.getBoolean("enabled", true);
        minPlayers = clamp(y.getInt("players.min", 1), 1, 10);
        maxPlayers = clamp(y.getInt("players.max", 10), minPlayers, 10);
        recommendedPlayers = clamp(y.getInt("players.recommended", Math.min(4, maxPlayers)), minPlayers, maxPlayers);
        timeLimitMs = minutes(y.get("time-limit"), 72000) * 50;
        estimatedMinutes = y.getInt("estimated-minutes", (int) (timeLimitMs / 60000));

        premium = y.getBoolean("access.premium", false);
        priceGems = y.getInt("access.price-gems", 0);
        priceKeys = y.getInt("access.price-keys", 0);
        accessPermission = y.getString("access.permission", "");
        unlockLevel = y.getInt("access.level", 1);
        unlockRooms = y.getStringList("access.rooms");

        playerTime = y.getLong("atmosphere.time", -1);
        rain = y.getBoolean("atmosphere.rain", false);
        spawn = y.getString("spawn", "@spawn");
        voidY = y.getInt("void-y", 0);
        intro = Yml.strings(y, "intro.lines");
        introInterval = Yml.ticks(y.get("intro.interval"), 70);

        twoStarMs = minutes(y.get("stars.two.time"), 60000) * 50;
        twoStarHints = y.getInt("stars.two.hints", 4);
        threeStarMs = minutes(y.get("stars.three.time"), 48000) * 50;
        threeStarHints = y.getInt("stars.three.hints", 0);
        maxHints = y.getInt("hints.max", 12);
        hintPenaltyMs = Yml.ticks(y.get("hints.penalty", "60s"), 1200) * 50;
        wrongPenaltyMs = Yml.ticks(y.get("hints.wrong-answer-penalty", "0s"), 0) * 50;

        rewardXp = y.getInt("rewards.xp", 300);
        rewardKeys = y.getInt("rewards.keys", 50);
        firstXp = y.getInt("rewards.first-time.xp", 500);
        firstKeys = y.getInt("rewards.first-time.keys", 100);
        starXp = y.getInt("rewards.xp-per-star", 100);
        rewardCommands = y.getStringList("rewards.commands");

        ConfigurationSection itemSec = y.getConfigurationSection("items");
        if (itemSec != null) {
            for (String key : itemSec.getKeys(false)) {
                items.put(key, ItemSpec.parse(key, itemSec.getConfigurationSection(key)));
            }
        }
        startItems = y.getStringList("start-items");
        zones = Yml.sections(y, "zones");
        props = Yml.sections(y, "props");
        recipes = Yml.sections(y, "recipes");
        timeline = Yml.sections(y, "timeline");
        ambience = Yml.sections(y, "ambience");
        for (ConfigurationSection ps : Yml.sections(y, "puzzles")) {
            String pid = ps.getString("id");
            if (pid == null) {
                log.warning("[" + id + "] Puzzle without id ignored");
                continue;
            }
            puzzles.put(pid, PuzzleDef.parse(pid, ps, log, id));
        }
        onStart = Actions.parseList(y, "on-start", log, id + "/on-start");
        onWin = Actions.parseList(y, "on-win", log, id + "/on-win");
    }

    /** Room durations: a plain number means minutes ("60"), otherwise the usual suffixes ("45m", "90s"). */
    private static long minutes(Object value, long defTicks) {
        if (value instanceof Number n) {
            return (long) (n.doubleValue() * 1200);
        }
        return Yml.ticks(value, defTicks);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Puzzles that count towards completion (not optional). */
    public List<PuzzleDef> mainPuzzles() {
        List<PuzzleDef> out = new ArrayList<>();
        for (PuzzleDef p : puzzles.values()) {
            if (!p.optional()) {
                out.add(p);
            }
        }
        return out;
    }

    public boolean isPaid() {
        return premium || priceGems > 0 || priceKeys > 0;
    }
}
