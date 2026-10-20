package com.sergicraftero.escaperoom.puzzle;

import com.sergicraftero.escaperoom.action.ActionList;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.util.BlockPos;
import com.sergicraftero.escaperoom.util.Cfg;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Shared fields and helpers for puzzle implementations. */
public abstract class AbstractPuzzle implements Puzzle {

    private final String id;
    private final String type;
    private final String displayName;
    private final List<String> requires;
    private final List<String> hints;
    private final ActionList onSolve;
    private volatile PuzzleState state = PuzzleState.LOCKED;

    protected AbstractPuzzle(String id, String type, ConfigurationSection section, Logger logger) {
        this.id = id;
        this.type = type;
        this.displayName = section.getString("name", id);
        this.requires = section.getStringList("requires");
        this.hints = section.getStringList("hints");
        this.onSolve = ActionList.parse(Cfg.sections(section, "on-solve"), logger, "puzzle " + id);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public List<String> requires() {
        return requires;
    }

    @Override
    public List<String> hints() {
        return hints;
    }

    @Override
    public ActionList onSolve() {
        return onSolve;
    }

    @Override
    public PuzzleState state() {
        return state;
    }

    @Override
    public void state(PuzzleState state) {
        this.state = state;
    }

    protected void solve(Game game, Player player) {
        game.solve(this, player);
    }

    protected static void sound(Game game, Location at, String key, float volume, float pitch) {
        Sound sound = Sound.sound(Key.key(key), Sound.Source.BLOCK, volume, pitch);
        for (Player p : game.participants()) {
            p.playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

    /** Reads a section of "x,y,z": value pairs preserving order. */
    protected static Map<BlockPos, Object> positionMap(ConfigurationSection section, String path) {
        Map<BlockPos, Object> map = new LinkedHashMap<>();
        ConfigurationSection s = section.getConfigurationSection(path);
        if (s == null) {
            return map;
        }
        for (String key : s.getKeys(false)) {
            map.put(BlockPos.parse(key), s.get(key));
        }
        return map;
    }
}
