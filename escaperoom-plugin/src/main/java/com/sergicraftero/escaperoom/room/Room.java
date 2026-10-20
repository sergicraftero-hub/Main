package com.sergicraftero.escaperoom.room;

import com.sergicraftero.escaperoom.action.ActionList;
import com.sergicraftero.escaperoom.puzzle.Puzzle;
import com.sergicraftero.escaperoom.puzzle.PuzzleRegistry;
import com.sergicraftero.escaperoom.util.BlockPos;
import com.sergicraftero.escaperoom.util.Cfg;
import com.sergicraftero.escaperoom.util.Cuboid;
import com.sergicraftero.escaperoom.util.LocUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A room definition loaded from rooms/&lt;id&gt;.yml. Simple settings can be edited in game and are
 * written back to the same YAML, so hand-written puzzle sections are preserved.
 */
public final class Room {

    private final String id;
    private final File file;
    private final YamlConfiguration config;

    private String displayName;
    private String worldName;
    private Cuboid region;
    private String spawn;
    private String exit;
    private String lobby;
    private String leaderboard;

    private boolean enabled;
    private int timeLimit;
    private int minPlayers;
    private int maxPlayers;
    private int countdown;
    private boolean hintsAllowed;
    private int maxHints;
    private int hintPenalty;
    private boolean night;
    private String finalPuzzle;
    private int winDelay;

    private List<String> rewardCommands = List.of();
    private double rewardMoney;

    private final Map<String, ItemSpec> items = new LinkedHashMap<>();
    private final Map<String, Puzzle> puzzles = new LinkedHashMap<>();
    private final Map<BlockPos, Puzzle> interactIndex = new HashMap<>();
    private final Map<BlockPos, Puzzle> placeIndex = new HashMap<>();
    private final List<Trigger> triggers = new ArrayList<>();
    private final List<AmbientEffect> ambient = new ArrayList<>();
    private final Map<BlockPos, String> lecterns = new LinkedHashMap<>();
    private final Map<BlockPos, List<String>> containers = new LinkedHashMap<>();
    private ActionList startActions = ActionList.EMPTY;
    private List<String> startItems = List.of();

    private Room(String id, File file, YamlConfiguration config) {
        this.id = id;
        this.file = file;
        this.config = config;
    }

    /** Creates a fresh room around a region. */
    public static Room create(String id, File file, World world, Cuboid region, Location spawn) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("display-name", "<gold>" + id);
        yaml.set("world", world.getName());
        yaml.set("region.min", region.min().serialize());
        yaml.set("region.max", region.max().serialize());
        yaml.set("spawn", LocUtil.serialize(spawn));
        yaml.set("exit", LocUtil.serialize(spawn));
        yaml.set("lobby", LocUtil.serialize(spawn));
        yaml.set("settings.enabled", false);
        yaml.set("settings.time-limit", 1800);
        yaml.set("settings.min-players", 1);
        yaml.set("settings.max-players", 4);
        yaml.set("settings.hints-allowed", true);
        yaml.set("settings.max-hints", -1);
        yaml.set("settings.hint-penalty", 60);
        yaml.createSection("puzzles");
        Room room = new Room(id, file, yaml);
        room.reload(Logger.getLogger("EscapeRoom"));
        return room;
    }

    public static Room load(File file, Logger logger) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = yaml.getString("id", file.getName().replace(".yml", ""));
        Room room = new Room(id, file, yaml);
        room.reload(logger);
        return room;
    }

    private void reload(Logger logger) {
        displayName = config.getString("display-name", id);
        worldName = config.getString("world", "world");
        region = Cuboid.parse(config.getString("region.min", "0,0,0"), config.getString("region.max", "0,0,0"));
        spawn = config.getString("spawn");
        exit = config.getString("exit", spawn);
        lobby = config.getString("lobby", exit);
        leaderboard = config.getString("leaderboard");

        ConfigurationSection s = config.getConfigurationSection("settings");
        if (s == null) {
            s = config.createSection("settings");
        }
        enabled = s.getBoolean("enabled", true);
        timeLimit = s.getInt("time-limit", 1800);
        minPlayers = Math.max(1, s.getInt("min-players", 1));
        maxPlayers = Math.max(minPlayers, s.getInt("max-players", 4));
        countdown = s.getInt("countdown", -1);
        hintsAllowed = s.getBoolean("hints-allowed", true);
        maxHints = s.getInt("max-hints", -1);
        hintPenalty = s.getInt("hint-penalty", 60);
        night = s.getBoolean("night", false);
        finalPuzzle = s.getString("final-puzzle");
        winDelay = s.getInt("win-delay", 0);

        rewardCommands = config.getStringList("rewards.commands");
        rewardMoney = config.getDouble("rewards.money", 0);

        items.clear();
        ConfigurationSection itemSection = config.getConfigurationSection("items");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(false)) {
                items.put(key, ItemSpec.load(id, key, itemSection.getConfigurationSection(key)));
            }
        }

        puzzles.clear();
        interactIndex.clear();
        placeIndex.clear();
        ConfigurationSection puzzleSection = config.getConfigurationSection("puzzles");
        if (puzzleSection != null) {
            for (String key : puzzleSection.getKeys(false)) {
                try {
                    Puzzle puzzle = PuzzleRegistry.create(id, key, puzzleSection.getConfigurationSection(key), logger);
                    puzzles.put(key, puzzle);
                    puzzle.interactables().forEach(pos -> interactIndex.put(pos, puzzle));
                    puzzle.placeables().forEach(pos -> placeIndex.put(pos, puzzle));
                } catch (Exception e) {
                    logger.warning("[" + id + "] Could not load puzzle '" + key + "': " + e.getMessage());
                }
            }
        }

        triggers.clear();
        for (ConfigurationSection t : Cfg.sections(config, "triggers")) {
            triggers.add(new Trigger(t.getString("id", "trigger" + triggers.size()),
                    Cuboid.parse(t.getString("min", "0,0,0"), t.getString("max", "0,0,0")),
                    ActionList.parse(Cfg.sections(t, "actions"), logger, id + " trigger")));
        }

        ambient.clear();
        for (ConfigurationSection a : Cfg.sections(config, "ambient")) {
            try {
                ambient.add(AmbientEffect.load(a));
            } catch (Exception e) {
                logger.warning("[" + id + "] Invalid ambient effect: " + e.getMessage());
            }
        }

        lecterns.clear();
        ConfigurationSection lecternSection = config.getConfigurationSection("setup.lecterns");
        if (lecternSection != null) {
            for (String key : lecternSection.getKeys(false)) {
                lecterns.put(BlockPos.parse(key), lecternSection.getString(key));
            }
        }
        containers.clear();
        ConfigurationSection containerSection = config.getConfigurationSection("setup.containers");
        if (containerSection != null) {
            for (String key : containerSection.getKeys(false)) {
                containers.put(BlockPos.parse(key), containerSection.getStringList(key));
            }
        }
        startActions = ActionList.parse(Cfg.sections(config, "start-actions"), logger, id + " start");
        startItems = config.getStringList("start-items");
    }

    public void save() throws IOException {
        config.set("display-name", displayName);
        config.set("world", worldName);
        config.set("region.min", region.min().serialize());
        config.set("region.max", region.max().serialize());
        config.set("spawn", spawn);
        config.set("exit", exit);
        config.set("lobby", lobby);
        config.set("leaderboard", leaderboard);
        config.set("settings.enabled", enabled);
        config.set("settings.time-limit", timeLimit);
        config.set("settings.min-players", minPlayers);
        config.set("settings.max-players", maxPlayers);
        config.set("settings.countdown", countdown);
        config.set("settings.hints-allowed", hintsAllowed);
        config.set("settings.max-hints", maxHints);
        config.set("settings.hint-penalty", hintPenalty);
        config.set("settings.night", night);
        config.save(file);
    }

    // ───────────────────────── getters ─────────────────────────

    public String id() {
        return id;
    }

    public File file() {
        return file;
    }

    public YamlConfiguration config() {
        return config;
    }

    public String displayName() {
        return displayName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public String worldName() {
        return worldName;
    }

    public Cuboid region() {
        return region;
    }

    public boolean contains(Location loc) {
        return loc.getWorld() != null && loc.getWorld().getName().equals(worldName) && region.contains(loc);
    }

    public Location spawn() {
        return LocUtil.parse(world(), spawn);
    }

    public Location exit() {
        return LocUtil.parse(world(), exit);
    }

    public Location lobby() {
        return LocUtil.parse(world(), lobby);
    }

    public Location leaderboard() {
        return leaderboard == null ? null : LocUtil.parse(world(), leaderboard);
    }

    public boolean enabled() {
        return enabled;
    }

    public int timeLimit() {
        return timeLimit;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int countdown() {
        return countdown;
    }

    public boolean hintsAllowed() {
        return hintsAllowed;
    }

    public int maxHints() {
        return maxHints;
    }

    public int hintPenalty() {
        return hintPenalty;
    }

    public boolean night() {
        return night;
    }

    public String finalPuzzle() {
        return finalPuzzle;
    }

    public int winDelay() {
        return winDelay;
    }

    public List<String> rewardCommands() {
        return rewardCommands;
    }

    public double rewardMoney() {
        return rewardMoney;
    }

    public Map<String, ItemSpec> items() {
        return Collections.unmodifiableMap(items);
    }

    public Map<String, Puzzle> puzzles() {
        return Collections.unmodifiableMap(puzzles);
    }

    public Puzzle interactPuzzle(BlockPos pos) {
        return interactIndex.get(pos);
    }

    public Puzzle placePuzzle(BlockPos pos) {
        return placeIndex.get(pos);
    }

    public List<Trigger> triggers() {
        return triggers;
    }

    public List<AmbientEffect> ambient() {
        return ambient;
    }

    public Map<BlockPos, String> lecterns() {
        return lecterns;
    }

    public Map<BlockPos, List<String>> containers() {
        return containers;
    }

    public ActionList startActions() {
        return startActions;
    }

    public List<String> startItems() {
        return startItems;
    }

    // ───────────────────────── setters (in-game editing) ─────────────────────────

    public void displayName(String value) {
        displayName = value;
    }

    public void region(Cuboid value) {
        region = value;
    }

    public void world(World world) {
        worldName = world.getName();
    }

    public void spawn(Location loc) {
        spawn = LocUtil.serialize(loc);
    }

    public void exit(Location loc) {
        exit = LocUtil.serialize(loc);
    }

    public void lobby(Location loc) {
        lobby = LocUtil.serialize(loc);
    }

    public void leaderboard(Location loc) {
        leaderboard = loc == null ? null : LocUtil.serialize(loc);
    }

    public void enabled(boolean value) {
        enabled = value;
    }

    public void timeLimit(int value) {
        timeLimit = value;
    }

    public void minPlayers(int value) {
        minPlayers = Math.max(1, value);
        maxPlayers = Math.max(minPlayers, maxPlayers);
    }

    public void maxPlayers(int value) {
        maxPlayers = Math.max(minPlayers, value);
    }

    public void countdown(int value) {
        countdown = value;
    }

    public void hintsAllowed(boolean value) {
        hintsAllowed = value;
    }

    public void maxHints(int value) {
        maxHints = value;
    }

    public void hintPenalty(int value) {
        hintPenalty = value;
    }

    public void night(boolean value) {
        night = value;
    }
}
