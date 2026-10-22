package com.sergicraftero.escapes.sim;

import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays a room without a server: a walking player (no jumping over gaps) explores the map, picks up what it can,
 * solves every puzzle whose blocks it can reach and whose items it has, applies the puzzle actions to the blocks
 * (doors open, walls vanish) and repeats. Reports puzzles that can never be solved and places that can be reached
 * too early.
 */
public final class RoomSimulator {

    public final Template t;
    public final Map<String, Object> room;
    private final String[] blocks;
    private final int sx;
    private final int sy;
    private final int sz;

    public final List<String> problems = new ArrayList<>();
    public final List<String> log = new ArrayList<>();
    final Map<String, Integer> inventory = new HashMap<>();
    final Set<String> solved = new HashSet<>();
    final Set<String> active = new HashSet<>();
    final Set<String> propsSpawned = new HashSet<>();
    final Set<String> propsUsed = new HashSet<>();
    final Set<Long> containersLooted = new HashSet<>();
    final Set<String> forcedProps = new HashSet<>();
    final List<int[]> starts = new ArrayList<>();
    Set<Long> reachable = new HashSet<>();

    @SuppressWarnings("unchecked")
    public RoomSimulator(Template t, Map<String, Object> room) {
        this.t = t;
        this.room = room;
        sx = t.sizeX();
        sy = t.sizeY();
        sz = t.sizeZ();
        blocks = new String[sx * sy * sz];
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    blocks[idx(x, y, z)] = t.get(x, y, z);
                }
            }
        }
    }

    private int idx(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    static long key(int x, int y, int z) {
        return Template.key(x, y, z);
    }

    String get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return "minecraft:air";
        }
        return blocks[idx(x, y, z)];
    }

    void set(int x, int y, int z, String b) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return;
        }
        blocks[idx(x, y, z)] = b;
    }

    static String id(String data) {
        int i = data.indexOf('[');
        return i < 0 ? data : data.substring(0, i);
    }

    // ───────────────────────── block physics (approximate) ─────────────────────────

    static boolean passable(String data) {
        String id = id(data).replace("minecraft:", "");
        if (id.equals("air") || id.equals("cave_air") || id.equals("light") || id.equals("structure_void")) {
            return true;
        }
        if (id.endsWith("_door") || id.endsWith("_trapdoor") || id.endsWith("_fence_gate")) {
            return data.contains("open=true");
        }
        return id.endsWith("_carpet") || id.endsWith("_pressure_plate") || id.endsWith("_button") || id.equals("lever")
                || id.endsWith("torch") || id.equals("redstone_wire") || id.endsWith("rail") || id.endsWith("_sign")
                || id.endsWith("_banner") || id.equals("ladder") || id.equals("vine") || id.equals("cobweb")
                || id.equals("snow") || id.equals("tripwire") || id.equals("water") || id.equals("short_grass")
                || id.equals("tall_grass") || id.equals("fern") || id.equals("large_fern") || id.equals("pink_petals")
                || id.endsWith("_sapling") || id.equals("dead_bush") || id.equals("sugar_cane") || id.equals("glow_lichen")
                || id.equals("sculk_vein") || id.equals("moss_carpet") || id.equals("tripwire_hook") || id.equals("string")
                || id.equals("nether_portal") || id.equals("seagrass") || id.equals("kelp") || id.equals("kelp_plant")
                || id.equals("hanging_roots") || id.equals("spore_blossom") || id.equals("cave_vines") || id.equals("cave_vines_plant")
                || id.equals("weeping_vines") || id.equals("twisting_vines") || id.equals("weeping_vines_plant")
                || id.equals("big_dripleaf_stem") || id.equals("small_dripleaf") || id.equals("bubble_column")
                || isFlower(id);
    }

    static boolean isFlower(String id) {
        return switch (id) {
            case "poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
                    "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac",
                    "rose_bush", "peony", "torchflower", "brown_mushroom", "red_mushroom", "crimson_roots", "warped_roots",
                    "nether_sprouts", "crimson_fungus", "warped_fungus", "lily_pad", "wildflowers", "leaf_litter" -> true;
            default -> false;
        };
    }

    /** Blocks too tall to step onto (fences, walls, closed gates): never a floor. */
    static boolean tall(String data) {
        String id = id(data).replace("minecraft:", "");
        return id.endsWith("_fence") || id.endsWith("_wall") || (id.endsWith("_fence_gate") && !data.contains("open=true"))
                || id.equals("iron_bars") || id.endsWith("glass_pane") || id.equals("chain") || id.equals("iron_chain");
    }

    static boolean climbable(String data) {
        String id = id(data).replace("minecraft:", "");
        return id.equals("ladder") || id.equals("vine") || id.equals("water") || id.equals("scaffolding")
                || id.equals("twisting_vines") || id.equals("weeping_vines") || id.equals("cave_vines");
    }

    boolean free(int x, int y, int z) {
        return passable(get(x, y, z));
    }

    boolean support(int x, int y, int z) {
        String d = get(x, y, z);
        return !passable(d) && !tall(d) && !id(d).equals("minecraft:air");
    }

    /** A cell where a player's feet can be. */
    boolean standable(int x, int y, int z) {
        if (y < 1 || y + 1 >= sy || x < 0 || z < 0 || x >= sx || z >= sz) {
            return false;
        }
        if (!free(x, y, z) || !free(x, y + 1, z)) {
            return false;
        }
        return support(x, y - 1, z) || climbable(get(x, y, z));
    }

    // ───────────────────────── exploration ─────────────────────────

    /** From a free cell, fall until something supports the feet. Returns -1 when falling out of the map. */
    int land(int x, int y, int z) {
        while (y > 0 && !support(x, y - 1, z) && !climbable(get(x, y, z))) {
            y--;
        }
        return y <= 0 ? -1 : y;
    }

    Set<Long> explore() {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int[] s : starts) {
            int y = land(s[0], s[1], s[2]);
            if (y >= 0 && standable(s[0], y, s[2])) {
                if (seen.add(key(s[0], y, s[2]))) {
                    queue.add(new int[]{s[0], y, s[2]});
                }
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int x = c[0];
            int y = c[1];
            int z = c[2];
            for (int[] d : dirs) {
                int nx = x + d[0];
                int nz = z + d[1];
                // walk / fall
                if (free(nx, y, nz) && free(nx, y + 1, nz)) {
                    int ly = land(nx, y, nz);
                    if (ly >= 0 && standable(nx, ly, nz) && seen.add(key(nx, ly, nz))) {
                        queue.add(new int[]{nx, ly, nz});
                    }
                }
                // step up one block
                if (support(nx, y, nz) && free(nx, y + 1, nz) && free(nx, y + 2, nz) && free(x, y + 2, z)
                        && standable(nx, y + 1, nz) && seen.add(key(nx, y + 1, nz))) {
                    queue.add(new int[]{nx, y + 1, nz});
                }
            }
            // ladders / water
            if (climbable(get(x, y, z)) || climbable(get(x, y + 1, z))) {
                if (free(x, y + 2, z) && standable(x, y + 1, z) && seen.add(key(x, y + 1, z))) {
                    queue.add(new int[]{x, y + 1, z});
                }
                // getting off the top of a ladder onto a floor
                for (int[] d : dirs) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (support(nx, y + 1, nz) && free(nx, y + 2, nz) && free(nx, y + 3, nz) && free(x, y + 2, z)
                            && standable(nx, y + 2, nz) && seen.add(key(nx, y + 2, nz))) {
                        queue.add(new int[]{nx, y + 2, nz});
                    }
                }
            }
            if (y > 1 && climbable(get(x, y - 1, z)) && seen.add(key(x, y - 1, z))) {
                queue.add(new int[]{x, y - 1, z});
            }
        }
        return seen;
    }

    /** Can a player standing somewhere reachable click this block (≤ 4.5 blocks, clear line of sight)? */
    boolean usable(int tx, int ty, int tz) {
        for (long k : reachable) {
            int x = Template.keyX(k);
            int y = Template.keyY(k);
            int z = Template.keyZ(k);
            double ex = x + 0.5;
            double ey = y + 1.62;
            double ez = z + 0.5;
            double cx = tx + 0.5;
            double cy = ty + 0.5;
            double cz = tz + 0.5;
            double dist = Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy) + (ez - cz) * (ez - cz));
            if (dist > 4.5) {
                continue;
            }
            if (lineOfSight(ex, ey, ez, cx, cy, cz, tx, ty, tz)) {
                return true;
            }
        }
        return false;
    }

    private boolean lineOfSight(double ex, double ey, double ez, double cx, double cy, double cz, int tx, int ty, int tz) {
        double dist = Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy) + (ez - cz) * (ez - cz));
        int steps = (int) (dist / 0.1);
        for (int i = 1; i < steps; i++) {
            double f = (double) i / steps;
            int bx = (int) Math.floor(ex + (cx - ex) * f);
            int by = (int) Math.floor(ey + (cy - ey) * f);
            int bz = (int) Math.floor(ez + (cz - ez) * f);
            if (bx == tx && by == ty && bz == tz) {
                return true;
            }
            String d = get(bx, by, bz);
            if (!passable(d) && !id(d).endsWith("_slab") && !id(d).endsWith("lantern") && !id(d).endsWith("candle")
                    && !id(d).equals("minecraft:end_rod")) {
                return false;
            }
        }
        return true;
    }

    boolean usable(String ref) {
        for (int[] p : Refs.points(ref, t)) {
            if (usable(p[0], p[1], p[2])) {
                return true;
            }
        }
        return false;
    }

    boolean anyUsable(List<String> refs) {
        for (String r : refs) {
            if (usable(r)) {
                return true;
            }
        }
        return false;
    }

    boolean allUsable(List<String> refs) {
        for (String r : refs) {
            for (int[] p : Refs.points(r, t)) {
                if (!usable(p[0], p[1], p[2])) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean regionReachable(String ref) {
        Template.Marker m = Refs.box(ref, t);
        if (m == null) {
            return false;
        }
        for (long k : reachable) {
            int x = Template.keyX(k);
            int y = Template.keyY(k);
            int z = Template.keyZ(k);
            if (m.contains(x, y, z) || m.contains(x, y + 1, z)) {
                return true;
            }
        }
        return false;
    }

    boolean feetReachable(int x, int y, int z) {
        return reachable.contains(key(x, y, z));
    }

    // ───────────────────────── yaml helpers ─────────────────────────

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    static List<String> strings(Map<String, Object> m, String key) {
        Object o = m.get(key);
        List<String> out = new ArrayList<>();
        if (o instanceof Collection<?> c) {
            for (Object x : c) {
                out.add(String.valueOf(x));
            }
        } else if (o != null) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> maps(Map<String, Object> m, String key) {
        Object o = m.get(key);
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof Collection<?> c) {
            for (Object x : c) {
                if (x instanceof Map<?, ?>) {
                    out.add((Map<String, Object>) x);
                } else if (x instanceof String s) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("type", s);
                    out.add(mm);
                }
            }
        } else if (o instanceof Map<?, ?> mm) {
            for (Map.Entry<?, ?> e : mm.entrySet()) {
                if (e.getValue() instanceof Map<?, ?>) {
                    Map<String, Object> v = new LinkedHashMap<>((Map<String, Object>) e.getValue());
                    v.putIfAbsent("id", String.valueOf(e.getKey()));
                    out.add(v);
                }
            }
        }
        return out;
    }

    Map<String, Map<String, Object>> puzzles() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> p : maps(room, "puzzles")) {
            out.put(String.valueOf(p.get("id")), p);
        }
        return out;
    }

    List<Map<String, Object>> props() {
        return maps(room, "props");
    }

    // ───────────────────────── inventory ─────────────────────────

    void give(String item, int n) {
        inventory.merge(item, n, Integer::sum);
    }

    boolean has(String item) {
        return inventory.getOrDefault(item, 0) > 0;
    }

    int count(String item) {
        return inventory.getOrDefault(item, 0);
    }

    void take(String item, int n) {
        inventory.computeIfPresent(item, (k, v) -> v - n <= 0 ? null : v - n);
    }

    // ───────────────────────── running ─────────────────────────

    public boolean run() {
        Map<String, Map<String, Object>> puzzles = puzzles();
        String spawn = String.valueOf(room.getOrDefault("spawn", "@spawn"));
        if (!Refs.exists(spawn, t)) {
            problems.add("spawn " + spawn + " missing");
            return false;
        }
        starts.add(Refs.point(spawn, t));
        for (String item : strings(room, "start-items")) {
            give(item, 1);
        }
        applyActions(maps(room, "on-start"));
        refreshProps();
        lockChecks(puzzles, true);
        int guard = 0;
        while (solved.size() < puzzles.size() && guard++ < 500) {
            activate(puzzles);
            reachable = explore();
            collect();
            String next = null;
            for (String id : puzzles.keySet()) {
                if (active.contains(id) && !solved.contains(id) && solvable(id, puzzles.get(id))) {
                    next = id;
                    break;
                }
            }
            if (next == null) {
                if (craftNeeded(puzzles)) {
                    continue;
                }
                break;
            }
            consume(next, puzzles.get(next));
            solved.add(next);
            log.add("solved " + next);
            applyActions(maps(puzzles.get(next), "on-solve"));
            refreshProps();
            activate(puzzles);
            lockChecks(puzzles, false);
        }
        for (String id : puzzles.keySet()) {
            if (!solved.contains(id)) {
                problems.add("puzzle '" + id + "' can't be solved: " + why(id, puzzles.get(id)));
            }
        }
        reachable = explore();
        for (Map<String, Object> z : maps(room, "zones")) {
            String region = String.valueOf(z.get("region"));
            if (Refs.exists(region, t) && !regionReachable(region)) {
                problems.add("zone '" + z.get("id") + "' is never reachable");
            }
        }
        return problems.isEmpty();
    }

    /** Zones with locked-until must be unreachable until that puzzle is solved. */
    private void lockChecks(Map<String, Map<String, Object>> puzzles, boolean first) {
        reachable = explore();
        for (Map<String, Object> z : maps(room, "zones")) {
            Object lock = z.get("locked-until");
            if (lock == null) {
                continue;
            }
            String region = String.valueOf(z.get("region"));
            if (!solved.contains(String.valueOf(lock)) && regionReachable(region)) {
                String msg = "zone '" + z.get("id") + "' reachable before '" + lock + "' (solved: " + solved + ")";
                if (!problems.contains(msg)) {
                    problems.add(msg);
                }
            }
        }
        for (Map.Entry<String, Map<String, Object>> e : puzzles.entrySet()) {
            Map<String, Object> p = e.getValue();
            if ("reach".equals(p.get("type")) && !active.contains(e.getKey()) && !first) {
                String region = String.valueOf(p.getOrDefault("region", p.get("at")));
                if (regionReachable(region) && !solved.contains(e.getKey())) {
                    String msg = "reach puzzle '" + e.getKey() + "' region reachable before it is active";
                    if (!problems.contains(msg)) {
                        problems.add(msg);
                    }
                }
            }
        }
    }

    private void activate(Map<String, Map<String, Object>> puzzles) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, Map<String, Object>> e : puzzles.entrySet()) {
                if (active.contains(e.getKey())) {
                    continue;
                }
                if (solved.containsAll(strings(e.getValue(), "requires"))) {
                    active.add(e.getKey());
                    applyActions(maps(e.getValue(), "on-activate"));
                    if ("gate".equals(e.getValue().get("type"))) {
                        solved.add(e.getKey());
                        log.add("gate " + e.getKey());
                        applyActions(maps(e.getValue(), "on-solve"));
                    }
                    changed = true;
                    refreshProps();
                }
            }
        }
    }

    private boolean propWanted(Map<String, Object> p) {
        String id = String.valueOf(p.get("id"));
        if (forcedProps.contains(id)) {
            return true;
        }
        if (Boolean.TRUE.equals(p.get("manual"))) {
            return false;
        }
        if (!solved.containsAll(strings(p, "requires"))) {
            return false;
        }
        Object until = p.get("until");
        return until == null || !solved.contains(String.valueOf(until));
    }

    private void refreshProps() {
        for (Map<String, Object> p : props()) {
            String id = String.valueOf(p.get("id"));
            if (propWanted(p) && propsSpawned.add(id)) {
                if ("block".equals(p.get("type"))) {
                    for (int[] c : Refs.points(String.valueOf(p.get("at")), t)) {
                        set(c[0], c[1], c[2], String.valueOf(p.get("block")));
                    }
                }
            }
        }
    }

    /** Picks up everything reachable: search spots, containers, teleporters. */
    private void collect() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map<String, Object> p : props()) {
                String id = String.valueOf(p.get("id"));
                if (!propsSpawned.contains(id) || !propWanted(p) || propsUsed.contains(id)) {
                    continue;
                }
                String type = String.valueOf(p.get("type"));
                List<String> ats = strings(p, "at");
                String at = ats.isEmpty() ? "null" : ats.get(0);
                switch (type) {
                    case "search" -> {
                        Object tool = p.get("requires-item");
                        if (tool != null && !has(String.valueOf(tool))) {
                            continue;
                        }
                        if (!usable(at)) {
                            continue;
                        }
                        if (tool != null && Boolean.TRUE.equals(p.get("consume-item"))) {
                            take(String.valueOf(tool), 1);
                        }
                        Object item = p.get("item");
                        if (item != null) {
                            give(String.valueOf(item), p.get("amount") instanceof Number n ? n.intValue() : 1);
                        }
                        for (String spec : strings(p, "items")) {
                            String[] parts = spec.split("\\*");
                            give(parts[0].trim(), parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1);
                        }
                        applyActions(maps(p, "actions"));
                        propsUsed.add(id);
                        changed = true;
                    }
                    case "container" -> {
                        if (!usable(at)) {
                            continue;
                        }
                        for (String spec : strings(p, "items")) {
                            String[] parts = spec.split("\\*");
                            give(parts[0].trim(), parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1);
                        }
                        propsUsed.add(id);
                        changed = true;
                    }
                    case "teleporter" -> {
                        boolean ok = p.containsKey("region") ? regionReachable(String.valueOf(p.get("region"))) : usable(at);
                        if (ok) {
                            starts.add(Refs.point(String.valueOf(p.get("to")), t));
                            propsUsed.add(id);
                            reachable = explore();
                            changed = true;
                        }
                    }
                    default -> {
                    }
                }
            }
            // container tiles baked in the map ("item:x" specs)
            for (Map.Entry<Long, Map<String, Object>> e : t.tiles().entrySet()) {
                if (containersLooted.contains(e.getKey()) || !"container".equals(e.getValue().get("type"))) {
                    continue;
                }
                long k = e.getKey();
                if (usable(Template.keyX(k), Template.keyY(k), Template.keyZ(k))) {
                    containersLooted.add(k);
                    Object items = e.getValue().get("items");
                    if (items instanceof Collection<?> c) {
                        for (Object spec : c) {
                            String s = String.valueOf(spec);
                            if (s.startsWith("item:")) {
                                String[] parts = s.substring(5).split("\\*");
                                give(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                            }
                        }
                    }
                    changed = true;
                }
            }
        }
    }

    private List<String> neededItems(Map<String, Object> p) {
        List<String> out = new ArrayList<>();
        String type = String.valueOf(p.get("type"));
        switch (type) {
            case "interact" -> {
                if (p.get("item") != null) {
                    out.add(String.valueOf(p.get("item")));
                }
            }
            case "deliver", "obtain" -> out.addAll(strings(p, "items"));
            case "frames" -> {
                for (Map<String, Object> f : maps(p, "frames")) {
                    Object item = f.get("item");
                    if (item != null && !String.valueOf(item).equals(String.valueOf(f.get("start-item")))) {
                        out.add(String.valueOf(item));
                    }
                }
            }
            case "plates" -> {
                if (p.get("weight-item") != null) {
                    int plates = 0;
                    for (String r : strings(p, "plates")) {
                        plates += Refs.points(r, t).size();
                    }
                    for (int i = 0; i < plates; i++) {
                        out.add(String.valueOf(p.get("weight-item")));
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    private boolean itemsAvailable(List<String> items) {
        Map<String, Integer> need = new HashMap<>();
        items.forEach(i -> need.merge(i, 1, Integer::sum));
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            if (count(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    private String npcAt(String npc) {
        for (Map<String, Object> p : props()) {
            if (npc.equals(String.valueOf(p.get("id")))) {
                return propWanted(p) ? String.valueOf(p.get("at")) : null;
            }
        }
        return null;
    }

    boolean solvable(String id, Map<String, Object> p) {
        String type = String.valueOf(p.get("type"));
        if (!itemsAvailable(neededItems(p))) {
            return false;
        }
        // "needs": items without which players can't know the answer (a UV lamp to read the code...)
        for (String n : strings(p, "needs")) {
            if (!has(n)) {
                return false;
            }
        }
        return switch (type) {
            case "interact", "keypad", "deliver", "memory", "slide" -> anyUsable(strings(p, "at"));
            case "answer" -> p.get("at") == null || anyUsable(strings(p, "at"));
            case "choice" -> p.get("npc") != null ? npcAt(String.valueOf(p.get("npc"))) != null && usable(npcAt(String.valueOf(p.get("npc"))))
                    : anyUsable(strings(p, "at"));
            case "levers" -> allUsable(strings(p, "levers"));
            case "sequence" -> allUsable(strings(p, "buttons"));
            case "simon" -> allUsable(strings(p, "buttons")) && allUsable(strings(p, "start"));
            case "melody" -> allUsable(strings(p, "keys"));
            case "lightsout" -> allUsable(strings(p, "grid"));
            case "plates" -> {
                for (String r : strings(p, "plates")) {
                    for (int[] c : Refs.points(r, t)) {
                        if (!feetReachable(c[0], c[1], c[2]) && !usable(c[0], c[1], c[2])) {
                            yield false;
                        }
                    }
                }
                yield true;
            }
            case "frames" -> {
                for (Map<String, Object> f : maps(p, "frames")) {
                    if (!usable(String.valueOf(f.get("at")))) {
                        yield false;
                    }
                }
                yield true;
            }
            case "reach" -> regionReachable(String.valueOf(p.getOrDefault("region", p.get("at"))));
            case "obtain", "gate" -> true;
            default -> false;
        };
    }

    private String why(String id, Map<String, Object> p) {
        if (!active.contains(id)) {
            List<String> missing = new ArrayList<>(strings(p, "requires"));
            missing.removeAll(solved);
            return "never active (missing " + missing + ")";
        }
        List<String> items = neededItems(p);
        if (!itemsAvailable(items)) {
            return "items not obtainable " + items + " (have " + inventory + ")";
        }
        return "its blocks/region can't be reached";
    }

    private void consume(String id, Map<String, Object> p) {
        String type = String.valueOf(p.get("type"));
        switch (type) {
            case "interact" -> {
                if (p.get("item") != null && !Boolean.FALSE.equals(p.get("consume"))) {
                    take(String.valueOf(p.get("item")), 1);
                }
            }
            case "deliver" -> strings(p, "items").forEach(i -> take(i, 1));
            case "frames" -> neededItems(p).forEach(i -> take(i, 1));
            case "plates" -> neededItems(p).forEach(i -> take(i, 1));
            default -> {
            }
        }
    }

    private boolean craftNeeded(Map<String, Map<String, Object>> puzzles) {
        Set<String> needed = new HashSet<>();
        for (String id : puzzles.keySet()) {
            if (active.contains(id) && !solved.contains(id)) {
                for (String i : neededItems(puzzles.get(id))) {
                    if (!has(i)) {
                        needed.add(i);
                    }
                }
            }
        }
        for (Map<String, Object> p : props()) {
            Object tool = p.get("requires-item");
            if (tool != null && propWanted(p) && !propsUsed.contains(String.valueOf(p.get("id"))) && !has(String.valueOf(tool))) {
                needed.add(String.valueOf(tool));
            }
        }
        // inputs of needed recipes are needed too (chains)
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map<String, Object> r : maps(room, "recipes")) {
                if (needed.contains(String.valueOf(r.get("output")))) {
                    for (String in : strings(r, "inputs")) {
                        if (!has(in) && needed.add(in)) {
                            grew = true;
                        }
                    }
                }
            }
        }
        for (Map<String, Object> r : maps(room, "recipes")) {
            String out = String.valueOf(r.get("output"));
            if (!needed.contains(out)) {
                continue;
            }
            List<String> inputs = strings(r, "inputs");
            if (itemsAvailable(inputs)) {
                inputs.forEach(i -> take(i, 1));
                give(out, r.get("amount") instanceof Number n ? n.intValue() : 1);
                log.add("crafted " + out);
                applyActions(maps(r, "actions"));
                return true;
            }
        }
        return false;
    }

    // ───────────────────────── actions ─────────────────────────

    void applyActions(List<Map<String, Object>> actions) {
        for (Map<String, Object> a : actions) {
            String type = String.valueOf(a.get("type"));
            List<String> refs = new ArrayList<>(strings(a, "at"));
            if (refs.isEmpty()) {
                refs.addAll(strings(a, "region"));
            }
            switch (type) {
                case "clear" -> refs.forEach(r -> Refs.points(r, t).forEach(c -> set(c[0], c[1], c[2], "minecraft:air")));
                case "blocks", "set-blocks" -> {
                    String b = String.valueOf(a.getOrDefault("block", "minecraft:stone"));
                    String full = b.contains(":") ? b : "minecraft:" + b.toLowerCase();
                    refs.forEach(r -> Refs.points(r, t).forEach(c -> set(c[0], c[1], c[2], full)));
                }
                case "open", "close" -> {
                    boolean open = type.equals("open");
                    for (String r : refs) {
                        for (int[] c : Refs.points(r, t)) {
                            setOpen(c[0], c[1], c[2], open);
                            String d = get(c[0], c[1], c[2]);
                            if (id(d).endsWith("_door")) {
                                int other = d.contains("half=lower") ? 1 : -1;
                                setOpen(c[0], c[1] + other, c[2], open);
                            }
                        }
                    }
                }
                case "give" -> give(String.valueOf(a.get("item")), a.get("amount") instanceof Number n ? n.intValue() : 1);
                case "take" -> take(String.valueOf(a.get("item")), a.get("amount") instanceof Number n ? n.intValue() : Integer.MAX_VALUE);
                case "teleport", "tp" -> starts.add(Refs.point(String.valueOf(a.getOrDefault("to", "@spawn")), t));
                case "spawn-prop", "show" -> {
                    forcedProps.add(String.valueOf(a.getOrDefault("prop", a.get("id"))));
                    refreshProps();
                }
                case "remove-prop", "hide" -> {
                    String pid = String.valueOf(a.getOrDefault("prop", a.get("id")));
                    forcedProps.remove(pid);
                    propsUsed.add(pid);
                }
                default -> {
                }
            }
        }
    }

    private void setOpen(int x, int y, int z, boolean open) {
        String d = get(x, y, z);
        if (d.contains("open=")) {
            set(x, y, z, d.replace("open=" + !open, "open=" + open));
        }
    }
}
