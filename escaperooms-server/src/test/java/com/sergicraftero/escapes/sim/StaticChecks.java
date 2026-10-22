package com.sergicraftero.escapes.sim;

import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.sergicraftero.escapes.sim.RoomSimulator.id;
import static com.sergicraftero.escapes.sim.RoomSimulator.map;
import static com.sergicraftero.escapes.sim.RoomSimulator.maps;
import static com.sergicraftero.escapes.sim.RoomSimulator.strings;

/** Room YAML ↔ map checks that don't need a simulation: markers, block types, items, ids. */
final class StaticChecks {

    private StaticChecks() {
    }

    static List<String> check(Map<String, Object> room, Template t) {
        List<String> out = new ArrayList<>();
        Map<String, Object> items = map(room.get("items"));
        Map<String, Map<String, Object>> puzzles = new HashMap<>();
        for (Map<String, Object> p : maps(room, "puzzles")) {
            puzzles.put(String.valueOf(p.get("id")), p);
        }
        Set<String> propIds = new java.util.HashSet<>();
        for (Map<String, Object> p : maps(room, "props")) {
            if (p.get("id") != null && !propIds.add(String.valueOf(p.get("id")))) {
                out.add("duplicate prop id " + p.get("id"));
            }
        }
        ref(out, t, "spawn", String.valueOf(room.getOrDefault("spawn", "@spawn")));
        Map<Long, String> owners = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : puzzles.entrySet()) {
            String pid = e.getKey();
            Map<String, Object> p = e.getValue();
            String type = String.valueOf(p.get("type"));
            if (!RoomsTest.TYPES.contains(type)) {
                out.add(pid + ": unknown type " + type);
            }
            for (String r : strings(p, "requires")) {
                if (!puzzles.containsKey(r)) {
                    out.add(pid + ": requires unknown " + r);
                }
            }
            if (!type.equals("gate") && p.get("objective") == null && !Boolean.TRUE.equals(p.get("hidden"))) {
                out.add(pid + ": no objective text");
            }
            if (!type.equals("gate") && strings(p, "hints").size() < 2 && !Boolean.TRUE.equals(p.get("hidden"))) {
                out.add(pid + ": needs at least 2 hints");
            }
            for (String key : List.of("at", "levers", "lamps", "buttons", "plates", "keys", "listen", "start", "reset", "grid", "region")) {
                for (String r : strings(p, key)) {
                    if (!ref(out, t, pid + "." + key, r)) {
                        continue;
                    }
                    for (int[] c : Refs.points(r, t)) {
                        String block = t.get(c[0], c[1], c[2]);
                        String bid = id(block);
                        switch (key) {
                            case "levers" -> expect(out, pid, key, c, block, bid.equals("minecraft:lever"));
                            case "lamps", "grid" -> expect(out, pid, key, c, block, block.contains("lit="));
                            case "plates" -> expect(out, pid, key, c, block, bid.endsWith("_pressure_plate"));
                            case "region" -> {
                            }
                            default -> {
                                if (!type.equals("reach")) {
                                    expect(out, pid, key, c, block, !bid.equals("minecraft:air") && !bid.equals("minecraft:light"));
                                }
                            }
                        }
                        if (!key.equals("region") && !key.equals("lamps") && !type.equals("reach")) {
                            String prev = owners.put(Template.key(c[0], c[1], c[2]), pid);
                            if (prev != null && !prev.equals(pid)) {
                                out.add("block " + c[0] + "," + c[1] + "," + c[2] + " used by both " + prev + " and " + pid);
                            }
                        }
                    }
                }
            }
            for (Map<String, Object> f : maps(p, "frames")) {
                String at = String.valueOf(f.get("at"));
                if (!ref(out, t, pid + ".frames", at)) {
                    continue;
                }
                int[] c = Refs.point(at, t);
                String facing = f.get("facing") != null ? String.valueOf(f.get("facing")) : Refs.facing(at, t);
                if (facing == null) {
                    out.add(pid + ": frame " + at + " has no facing");
                    continue;
                }
                if (!RoomSimulator.passable(t.get(c[0], c[1], c[2]))) {
                    out.add(pid + ": frame " + at + " is inside a block " + t.get(c[0], c[1], c[2]));
                }
                int bx = c[0] - dx(facing);
                int bz = c[2] - dz(facing);
                int by = c[1] - (facing.equals("up") ? 1 : facing.equals("down") ? -1 : 0);
                if (RoomSimulator.passable(t.get(bx, by, bz))) {
                    out.add(pid + ": frame " + at + " has nothing behind it (" + facing + ")");
                }
                item(out, items, pid + ".frames", f.get("item"));
                item(out, items, pid + ".frames", f.get("start-item"));
            }
            item(out, items, pid + ".item", p.get("item"));
            item(out, items, pid + ".weight-item", p.get("weight-item"));
            for (String i : strings(p, "items")) {
                item(out, items, pid + ".items", i);
            }
            actions(out, t, items, puzzles, propIds, pid, maps(p, "on-solve"));
            actions(out, t, items, puzzles, propIds, pid, maps(p, "on-activate"));
            if (type.equals("choice") && p.get("npc") != null && !propIds.contains(String.valueOf(p.get("npc")))) {
                out.add(pid + ": npc prop " + p.get("npc") + " missing");
            }
            if (type.equals("choice") && maps(p, "options").stream().noneMatch(o -> Boolean.TRUE.equals(o.get("correct")))) {
                out.add(pid + ": no correct option");
            }
            if (type.equals("keypad") && p.get("code") == null) {
                out.add(pid + ": keypad without code");
            }
            if (type.equals("answer") && strings(p, "answers").isEmpty()) {
                out.add(pid + ": answer without answers");
            }
        }
        for (Map<String, Object> pr : maps(room, "props")) {
            String pid = "prop " + pr.get("id");
            String type = String.valueOf(pr.get("type"));
            for (String r : strings(pr, "at")) {
                if (!ref(out, t, pid, r)) {
                    continue;
                }
                int[] c = Refs.point(r, t);
                String block = t.get(c[0], c[1], c[2]);
                String bid = id(block);
                switch (type) {
                    case "npc" -> {
                        if (!RoomSimulator.passable(block) || !RoomSimulator.passable(t.get(c[0], c[1] + 1, c[2]))) {
                            out.add(pid + ": npc inside blocks at " + r);
                        }
                    }
                    case "search", "workbench", "clue" -> expect(out, pid, "at", c, block, !bid.equals("minecraft:air") && !bid.equals("minecraft:light"));
                    case "container" -> expect(out, pid, "at", c, block, bid.endsWith("chest") || bid.endsWith("barrel")
                            || bid.endsWith("shulker_box") || bid.endsWith("hopper") || bid.endsWith("dispenser") || bid.endsWith("dropper"));
                    case "book", "lectern" -> expect(out, pid, "at", c, block, bid.equals("minecraft:lectern"));
                    case "sign" -> expect(out, pid, "at", c, block, bid.endsWith("_sign"));
                    default -> {
                    }
                }
                if (type.equals("search") || type.equals("workbench") || type.equals("clue")) {
                    String prev = owners.get(Template.key(c[0], c[1], c[2]));
                    if (prev != null) {
                        out.add(pid + " shares its block with puzzle " + prev + " (the puzzle wins the click)");
                    }
                }
            }
            for (String key : List.of("region", "to")) {
                if (pr.get(key) != null) {
                    ref(out, t, pid + "." + key, String.valueOf(pr.get(key)));
                }
            }
            for (String r : strings(pr, "requires")) {
                if (!puzzles.containsKey(r)) {
                    out.add(pid + ": requires unknown " + r);
                }
            }
            if (pr.get("until") != null && !puzzles.containsKey(String.valueOf(pr.get("until")))) {
                out.add(pid + ": until unknown " + pr.get("until"));
            }
            item(out, items, pid, pr.get("item"));
            item(out, items, pid, pr.get("requires-item"));
            for (String s : strings(pr, "items")) {
                item(out, items, pid, s.split("\\*")[0].trim());
            }
            actions(out, t, items, puzzles, propIds, pid, maps(pr, "actions"));
        }
        redstoneChecks(out, t);
        for (Map<String, Object> z : maps(room, "zones")) {
            ref(out, t, "zone " + z.get("id"), String.valueOf(z.get("region")));
            if (z.get("locked-until") != null && !puzzles.containsKey(String.valueOf(z.get("locked-until")))) {
                out.add("zone " + z.get("id") + ": locked-until unknown puzzle");
            }
        }
        for (Map<String, Object> r : maps(room, "recipes")) {
            for (String in : strings(r, "inputs")) {
                item(out, items, "recipe " + r.get("id"), in);
            }
            item(out, items, "recipe " + r.get("id"), r.get("output"));
        }
        for (String s : strings(room, "start-items")) {
            item(out, items, "start-items", s);
        }
        for (Map<String, Object> tl : maps(room, "timeline")) {
            actions(out, t, items, puzzles, propIds, "timeline", maps(tl, "actions"));
        }
        actions(out, t, items, puzzles, propIds, "on-start", maps(room, "on-start"));
        return out;
    }

    /**
     * Copper bulbs toggle on any redstone pulse: a vanilla button, lever or pressure plate next to one
     * (or powering the block next to one) would scramble a puzzle. Buttons pressed by players are vanilla.
     */
    private static void redstoneChecks(List<String> out, Template t) {
        List<int[]> powered = new ArrayList<>();
        for (int y = 0; y < t.sizeY(); y++) {
            for (int z = 0; z < t.sizeZ(); z++) {
                for (int x = 0; x < t.sizeX(); x++) {
                    String d = t.get(x, y, z);
                    String bid = id(d);
                    boolean source = bid.endsWith("_button") || bid.equals("minecraft:lever") || bid.endsWith("_pressure_plate");
                    if (!source) {
                        continue;
                    }
                    powered.add(new int[]{x, y, z});
                    if (bid.endsWith("_pressure_plate") || d.contains("face=floor")) {
                        powered.add(new int[]{x, y - 1, z});
                    } else if (d.contains("face=ceiling")) {
                        powered.add(new int[]{x, y + 1, z});
                    } else {
                        String facing = d.replaceAll(".*facing=([a-z]+).*", "$1");
                        powered.add(new int[]{x - dx(facing), y, z - dz(facing)});
                    }
                }
            }
        }
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] p : powered) {
            for (int[] d : around) {
                String n = t.get(p[0] + d[0], p[1] + d[1], p[2] + d[2]);
                if (id(n).endsWith("copper_bulb")) {
                    out.add("copper bulb at " + (p[0] + d[0]) + "," + (p[1] + d[1]) + "," + (p[2] + d[2])
                            + " can be toggled by redstone from " + p[0] + "," + p[1] + "," + p[2]);
                }
            }
        }
    }

    private static void actions(List<String> out, Template t, Map<String, Object> items, Map<String, Map<String, Object>> puzzles,
                                Set<String> props, String ctx, List<Map<String, Object>> actions) {
        Set<String> known = Set.of("message", "msg", "actionbar", "title", "sound", "particle", "give", "take", "blocks",
                "set-blocks", "clear", "open", "close", "light", "unlight", "power", "unpower", "teleport", "tp", "narrative",
                "story", "time", "effect", "potion", "spawn-prop", "show", "remove-prop", "hide", "solve", "checkpoint",
                "command", "firework", "lightning", "player-time", "win", "lose", "objective");
        for (Map<String, Object> a : actions) {
            String type = String.valueOf(a.get("type"));
            if (!known.contains(type)) {
                out.add(ctx + ": unknown action " + type);
            }
            for (String key : List.of("at", "region", "to")) {
                for (String r : strings(a, key)) {
                    ref(out, t, ctx + "." + type, r);
                }
            }
            item(out, items, ctx + "." + type, a.get("item"));
            Object puzzle = a.get("puzzle");
            if (puzzle != null && !puzzles.containsKey(String.valueOf(puzzle))) {
                out.add(ctx + ": unknown puzzle " + puzzle);
            }
            Object prop = a.get("prop");
            if (prop != null && !props.contains(String.valueOf(prop))) {
                out.add(ctx + ": unknown prop " + prop);
            }
        }
    }

    private static boolean ref(List<String> out, Template t, String ctx, String ref) {
        if (ref == null || ref.equals("null")) {
            return false;
        }
        if (!Refs.exists(ref, t)) {
            out.add(ctx + ": marker '" + ref + "' not in the map");
            return false;
        }
        return true;
    }

    private static void item(List<String> out, Map<String, Object> items, String ctx, Object item) {
        if (item == null) {
            return;
        }
        String s = String.valueOf(item);
        boolean vanilla = s.contains(":") || s.equals(s.toUpperCase());
        if (!vanilla && !items.containsKey(s)) {
            out.add(ctx + ": unknown item '" + s + "'");
        }
    }

    private static void expect(List<String> out, String pid, String key, int[] c, String block, boolean ok) {
        if (!ok) {
            out.add(pid + "." + key + " at " + c[0] + "," + c[1] + "," + c[2] + " is " + block);
        }
    }

    static int dx(String f) {
        return f.equals("east") ? 1 : f.equals("west") ? -1 : 0;
    }

    static int dz(String f) {
        return f.equals("south") ? 1 : f.equals("north") ? -1 : 0;
    }
}
