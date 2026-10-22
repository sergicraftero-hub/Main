package com.sergicraftero.escapes.room;

import com.sergicraftero.escapes.action.Action;
import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * A puzzle as written in the room YAML. Common keys:
 * <pre>
 * type: keypad            # see PuzzleTypes
 * name: "La caja fuerte"  # shown in the hint menu
 * objective: "Abre la caja fuerte del despacho"
 * requires: [otro_puzle]  # becomes active when all of these are solved
 * hints: ["...", "...", "..."]
 * xp: 25
 * hidden: false           # true: not listed as objective (secret puzzles)
 * on-activate: [...]      # actions when it becomes active
 * on-solve: [...]         # actions when solved
 * on-fail: [...]          # actions on a wrong attempt
 * </pre>
 * Type specific keys stay in {@link #section()}.
 */
public record PuzzleDef(String id, String type, String name, String objective, List<String> requires,
                        List<String> hints, int xp, boolean hidden, boolean optional, List<Action> onActivate,
                        List<Action> onSolve, List<Action> onFail, ConfigurationSection section) {

    public static PuzzleDef parse(String id, ConfigurationSection s, Logger log, String roomId) {
        String ctx = roomId + "/" + id;
        return new PuzzleDef(id, s.getString("type", "interact").toLowerCase(Locale.ROOT),
                s.getString("name", id), s.getString("objective", ""), Yml.strings(s, "requires"),
                Yml.strings(s, "hints"), s.getInt("xp", 20), s.getBoolean("hidden", false),
                s.getBoolean("optional", false),
                Actions.parseList(s, "on-activate", log, ctx), Actions.parseList(s, "on-solve", log, ctx),
                Actions.parseList(s, "on-fail", log, ctx), s);
    }

    public String str(String key, String def) {
        return section.getString(key, def);
    }

    public List<String> list(String key) {
        return Yml.strings(section, key);
    }
}
