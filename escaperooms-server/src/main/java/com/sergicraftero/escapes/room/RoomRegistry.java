package com.sergicraftero.escapes.room;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.puzzle.PuzzleTypes;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads {@code rooms/*.yml}. Bundled rooms are copied on first start; existing files are never overwritten. */
public final class RoomRegistry {

    private final EscapePlugin plugin;
    private final Map<String, RoomDef> rooms = new LinkedHashMap<>();

    public RoomRegistry(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "rooms");
        dir.mkdirs();
        copyDefaults(dir);
        rooms.clear();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        List<RoomDef> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                String id = f.getName().substring(0, f.getName().length() - 4);
                try {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                    list.add(new RoomDef(id, y, plugin.getLogger()));
                } catch (RuntimeException e) {
                    plugin.getLogger().severe("Room " + id + " could not be loaded: " + e.getMessage());
                }
            }
        }
        list.sort(Comparator.comparingInt((RoomDef r) -> wingOrder(r.wing)).thenComparingInt(r -> r.order).thenComparing(r -> r.id));
        for (RoomDef r : list) {
            rooms.put(r.id, r);
        }
        plugin.getLogger().info("Loaded " + rooms.size() + " rooms");
    }

    private void copyDefaults(File dir) {
        try (InputStream index = plugin.getResource("rooms/index.txt")) {
            if (index == null) {
                return;
            }
            String[] ids = new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\\s+");
            for (String id : ids) {
                if (id.isBlank()) {
                    continue;
                }
                File target = new File(dir, id + ".yml");
                if (target.exists()) {
                    continue;
                }
                try (InputStream in = plugin.getResource("rooms/" + id + ".yml")) {
                    if (in != null) {
                        Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not copy default rooms: " + e.getMessage());
        }
    }

    public int wingOrder(String wing) {
        return plugin.getConfig().getInt("wings." + wing + ".order", 99);
    }

    public RoomDef get(String id) {
        return id == null ? null : rooms.get(id.toLowerCase());
    }

    public List<RoomDef> all() {
        return new ArrayList<>(rooms.values());
    }

    public List<RoomDef> wing(String wing) {
        List<RoomDef> out = new ArrayList<>();
        for (RoomDef r : rooms.values()) {
            if (r.wing.equals(wing)) {
                out.add(r);
            }
        }
        return out;
    }

    public List<String> ids() {
        return new ArrayList<>(rooms.keySet());
    }

    /**
     * Checks a room against its template: unknown puzzle types, missing markers, unknown items, requirement
     * cycles. Returns human readable problems (empty = fine).
     */
    public List<String> validate(RoomDef room, Template t) {
        List<String> problems = new ArrayList<>();
        if (t == null) {
            problems.add("no template '" + room.template + "'");
            return problems;
        }
        checkRef(problems, "spawn", room.spawn, t);
        for (PuzzleDef p : room.puzzles.values()) {
            if (!PuzzleTypes.names().contains(p.type())) {
                problems.add(p.id() + ": unknown type " + p.type());
            }
            for (String req : p.requires()) {
                if (!room.puzzles.containsKey(req)) {
                    problems.add(p.id() + ": requires unknown puzzle " + req);
                }
            }
            for (String key : List.of("at", "levers", "lamps", "buttons", "plates", "keys", "listen", "start", "reset", "region", "grid")) {
                for (String ref : Yml.strings(p.section(), key)) {
                    checkRef(problems, p.id() + "." + key, ref, t);
                }
            }
            for (ConfigurationSection f : Yml.sections(p.section(), "frames")) {
                checkRef(problems, p.id() + ".frames", f.getString("at"), t);
                checkItem(problems, room, p.id() + ".frames", f.getString("item"));
            }
            for (String item : Yml.strings(p.section(), "items")) {
                checkItem(problems, room, p.id() + ".items", item);
            }
            checkItem(problems, room, p.id() + ".item", p.section().getString("item"));
            checkActions(problems, room, t, p.id() + ".on-solve", p.section(), "on-solve");
            checkActions(problems, room, t, p.id() + ".on-activate", p.section(), "on-activate");
        }
        for (ConfigurationSection prop : room.props) {
            String id = prop.getString("id", "?");
            if (prop.contains("at")) {
                for (String ref : Yml.strings(prop, "at")) {
                    checkRef(problems, "prop " + id, ref, t);
                }
            }
            if (prop.contains("region")) {
                checkRef(problems, "prop " + id + ".region", prop.getString("region"), t);
            }
            if (prop.contains("to")) {
                checkRef(problems, "prop " + id + ".to", prop.getString("to"), t);
            }
            checkItem(problems, room, "prop " + id, prop.getString("item"));
            checkItem(problems, room, "prop " + id + ".requires-item", prop.getString("requires-item"));
            for (String r : Yml.strings(prop, "requires")) {
                if (!room.puzzles.containsKey(r)) {
                    problems.add("prop " + id + ": requires unknown puzzle " + r);
                }
            }
            for (String spec : Yml.strings(prop, "items")) {
                checkItem(problems, room, "prop " + id + ".items", spec.split("\\*")[0].trim());
            }
        }
        for (ConfigurationSection z : room.zones) {
            checkRef(problems, "zone " + z.getString("id", "?"), z.getString("region"), t);
        }
        for (ConfigurationSection r : room.recipes) {
            for (String in : Yml.strings(r, "inputs")) {
                checkItem(problems, room, "recipe " + r.getString("id"), in);
            }
            checkItem(problems, room, "recipe " + r.getString("id"), r.getString("output"));
        }
        for (String s : room.startItems) {
            checkItem(problems, room, "start-items", s);
        }
        // requirement cycles
        for (PuzzleDef p : room.puzzles.values()) {
            if (cycle(room, p.id(), p.id(), new java.util.HashSet<>())) {
                problems.add(p.id() + ": circular requirements");
            }
        }
        return problems;
    }

    private boolean cycle(RoomDef room, String start, String current, java.util.Set<String> seen) {
        PuzzleDef p = room.puzzles.get(current);
        if (p == null || !seen.add(current)) {
            return false;
        }
        for (String r : p.requires()) {
            if (r.equals(start) || cycle(room, start, r, seen)) {
                return true;
            }
        }
        return false;
    }

    private void checkActions(List<String> problems, RoomDef room, Template t, String ctx, ConfigurationSection s, String key) {
        for (ConfigurationSection a : Yml.sections(s, key)) {
            for (String k : List.of("at", "region", "to")) {
                for (String ref : Yml.strings(a, k)) {
                    checkRef(problems, ctx + "." + a.getString("type"), ref, t);
                }
            }
            if (a.contains("item")) {
                checkItem(problems, room, ctx, a.getString("item"));
            }
            String puzzle = a.getString("puzzle");
            if (puzzle != null && !room.puzzles.containsKey(puzzle)) {
                problems.add(ctx + ": unknown puzzle " + puzzle);
            }
            String prop = a.getString("prop");
            if (prop != null && room.props.stream().noneMatch(pr -> prop.equals(pr.getString("id")))) {
                problems.add(ctx + ": unknown prop " + prop);
            }
        }
    }

    private static void checkRef(List<String> problems, String ctx, String ref, Template t) {
        if (ref == null) {
            return;
        }
        if (!Refs.exists(ref, t)) {
            problems.add(ctx + ": position '" + ref + "' not found in the map");
        }
    }

    private static void checkItem(List<String> problems, RoomDef room, String ctx, String item) {
        if (item == null || item.isBlank()) {
            return;
        }
        if (!room.items.containsKey(item) && org.bukkit.Material.matchMaterial(item) == null) {
            problems.add(ctx + ": unknown item '" + item + "'");
        }
    }
}
