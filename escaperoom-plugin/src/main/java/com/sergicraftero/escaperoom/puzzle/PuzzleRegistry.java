package com.sergicraftero.escaperoom.puzzle;

import com.sergicraftero.escaperoom.puzzle.types.ButtonCodePuzzle;
import com.sergicraftero.escaperoom.puzzle.types.CauldronPuzzle;
import com.sergicraftero.escaperoom.puzzle.types.ChatRiddlePuzzle;
import com.sergicraftero.escaperoom.puzzle.types.HiddenItemPuzzle;
import com.sergicraftero.escaperoom.puzzle.types.KeyItemPuzzle;
import com.sergicraftero.escaperoom.puzzle.types.LeverPuzzle;
import com.sergicraftero.escaperoom.puzzle.types.PlacementPuzzle;
import com.sergicraftero.escaperoom.puzzle.types.PressurePlatePuzzle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Maps puzzle type names (as used in room YAML) to factories. */
public final class PuzzleRegistry {

    @FunctionalInterface
    public interface Factory {
        Puzzle create(String roomId, String id, ConfigurationSection section, Logger logger);
    }

    private static final Map<String, Factory> FACTORIES = new ConcurrentHashMap<>();

    static {
        register("levers", LeverPuzzle::new);
        register("buttons", ButtonCodePuzzle::new);
        register("key", KeyItemPuzzle::new);
        register("plates", PressurePlatePuzzle::new);
        register("riddle", ChatRiddlePuzzle::new);
        register("hidden-item", HiddenItemPuzzle::new);
        register("placement", PlacementPuzzle::new);
        register("cauldron", CauldronPuzzle::new);
    }

    private PuzzleRegistry() {
    }

    /** Registers a custom puzzle type (other plugins may call this before rooms load). */
    public static void register(String type, Factory factory) {
        FACTORIES.put(type.toLowerCase(Locale.ROOT), factory);
    }

    public static Set<String> types() {
        return FACTORIES.keySet();
    }

    public static Puzzle create(String roomId, String id, ConfigurationSection section, Logger logger) {
        String type = section.getString("type", "").toLowerCase(Locale.ROOT);
        Factory factory = FACTORIES.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown puzzle type '" + type + "'");
        }
        return factory.create(roomId, id, section, logger);
    }
}
