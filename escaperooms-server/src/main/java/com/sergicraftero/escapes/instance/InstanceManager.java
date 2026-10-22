package com.sergicraftero.escapes.instance;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.template.TemplateStore;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.world.VoidGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Owns the instance world. Every room gets its own row of slots (z axis) and every copy of the room a column
 * (x axis), {@code spacing} blocks apart. The world is recreated on each start so it never accumulates junk.
 */
public final class InstanceManager {

    private final EscapePlugin plugin;
    private final TemplateStore templates;
    private World world;
    private int spacing;
    private int baseY;
    private int budget;
    private int maxPerRoom;
    private int maxTotal;
    private final Map<String, Integer> rows = new HashMap<>();
    private final Map<String, Instance> byKey = new HashMap<>();
    private final Map<String, List<Instance>> byRoom = new HashMap<>();
    private final Deque<Instance> pasteQueue = new ArrayDeque<>();
    private final Map<Instance, List<Consumer<Instance>>> waiting = new HashMap<>();
    private BukkitTask task;

    public InstanceManager(EscapePlugin plugin, TemplateStore templates) {
        this.plugin = plugin;
        this.templates = templates;
    }

    public void start() {
        var c = plugin.getConfig();
        spacing = Math.max(256, c.getInt("instances.spacing", 512));
        baseY = c.getInt("instances.base-y", 64);
        budget = Math.max(1000, c.getInt("instances.blocks-per-tick", 40000));
        maxPerRoom = Math.max(1, c.getInt("instances.max-per-room", 8));
        maxTotal = Math.max(1, c.getInt("instances.max-total", 60));
        String name = c.getString("instances.world", "er_instances");
        world = Bukkit.getWorld(name);
        if (world == null) {
            deleteWorldFolder(Bukkit.getWorldContainer().toPath().resolve(name));
            world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        if (world == null) {
            throw new IllegalStateException("Could not create instance world " + name);
        }
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        world.setTime(18000);
        world.setStorm(false);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private static void deleteWorldFolder(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    public World world() {
        return world;
    }

    /** Stable row per room: the order of the room list. */
    public void assignRows(List<String> roomIds) {
        rows.clear();
        int i = 0;
        for (String id : roomIds) {
            rows.put(id, i++);
        }
    }

    // ───────────────────────── lookup ─────────────────────────

    public Instance at(Location l) {
        if (l == null || l.getWorld() != world) {
            return null;
        }
        int col = Math.floorDiv(l.getBlockX(), spacing);
        int row = Math.floorDiv(l.getBlockZ(), spacing);
        Instance in = byKey.get(row + ":" + col);
        return in != null && in.contains(l) ? in : null;
    }

    public List<Instance> all() {
        return new ArrayList<>(byKey.values());
    }

    public int count(RoomDef room) {
        return byRoom.getOrDefault(room.id, List.of()).size();
    }

    public long ready(RoomDef room) {
        return byRoom.getOrDefault(room.id, List.of()).stream().filter(i -> i.state() == Instance.State.READY).count();
    }

    // ───────────────────────── lifecycle ─────────────────────────

    /** Pre-pastes copies so the first players don't wait. */
    public void warm(RoomDef room, int count) {
        List<Instance> list = byRoom.getOrDefault(room.id, List.of());
        for (int i = list.size(); i < count; i++) {
            create(room);
        }
    }

    /**
     * Gets a copy of the room for a game. {@code ready} runs (on the main thread) when it can be used; returns
     * false when the server is at capacity.
     */
    public boolean acquire(RoomDef room, Consumer<Instance> ready) {
        for (Instance in : byRoom.getOrDefault(room.id, List.of())) {
            if (in.state() == Instance.State.READY && !waiting.containsKey(in)) {
                in.state(Instance.State.IN_USE);
                ticket(in, true);
                ready.accept(in);
                return true;
            }
        }
        // a copy that is already being pasted/reset and nobody is waiting for
        for (Instance in : byRoom.getOrDefault(room.id, List.of())) {
            if ((in.state() == Instance.State.PASTING || in.state() == Instance.State.RESETTING) && !waiting.containsKey(in)) {
                waiting.computeIfAbsent(in, k -> new ArrayList<>()).add(ready);
                return true;
            }
        }
        if (count(room) >= maxPerRoom || byKey.size() >= maxTotal) {
            return false;
        }
        Instance in = create(room);
        if (in == null) {
            return false;
        }
        waiting.computeIfAbsent(in, k -> new ArrayList<>()).add(ready);
        return true;
    }

    private Instance create(RoomDef room) {
        TemplateStore.Loaded loaded = templates.get(room.template);
        if (loaded == null) {
            plugin.getLogger().severe("Room " + room.id + " has no template '" + room.template + "'");
            return null;
        }
        Integer row = rows.get(room.id);
        if (row == null) {
            row = rows.size();
            rows.put(room.id, row);
        }
        List<Instance> list = byRoom.computeIfAbsent(room.id, k -> new ArrayList<>());
        int col = 0;
        while (byKey.containsKey(row + ":" + col)) {
            col++;
        }
        Instance in = new Instance(row, col, room, loaded.template(), world, col * spacing + 16, baseY, row * spacing + 16);
        byKey.put(in.key(), in);
        list.add(in);
        in.state(Instance.State.PASTING);
        in.job(new PasteJob(loaded.template(), loaded.palette(), world, in.originX(), in.originY(), in.originZ(), true,
                spec -> plugin.items().resolve(room, spec), null));
        ticket(in, true);
        pasteQueue.add(in);
        return in;
    }

    /** Game over: clean entities and paste the room again so the next team finds it untouched. */
    public void release(Instance in) {
        if (in.state() != Instance.State.IN_USE) {
            return;
        }
        clearEntities(in);
        TemplateStore.Loaded loaded = templates.get(in.room().template);
        if (loaded == null) {
            remove(in);
            return;
        }
        boolean sizeChanged = loaded.template() != in.template()
                && (loaded.template().sizeX() < in.template().sizeX() || loaded.template().sizeY() < in.template().sizeY()
                || loaded.template().sizeZ() < in.template().sizeZ());
        if (sizeChanged) {
            // The map shrank in the editor: drop this slot (a fresh one is cleaner than clearing the leftovers).
            remove(in);
            return;
        }
        in.template(loaded.template());
        in.state(Instance.State.RESETTING);
        in.job(new PasteJob(loaded.template(), loaded.palette(), world, in.originX(), in.originY(), in.originZ(), false,
                spec -> plugin.items().resolve(in.room(), spec), null));
        pasteQueue.add(in);
    }

    private void remove(Instance in) {
        ticket(in, false);
        byKey.remove(in.key());
        List<Instance> list = byRoom.get(in.room().id);
        if (list != null) {
            list.remove(in);
        }
        pasteQueue.remove(in);
        waiting.remove(in);
        // Note: blocks stay in the world until the next restart wipes it; the slot key is never reused
        // for a different template size because columns only grow.
    }

    /** Drops every copy of a room (after the map was edited/rebuilt). Copies in use are reset when released. */
    public void invalidate(String roomId) {
        for (Instance in : new ArrayList<>(byRoom.getOrDefault(roomId, List.of()))) {
            if (in.state() == Instance.State.READY) {
                in.state(Instance.State.IN_USE);
                release(in);
            }
        }
    }

    public void clearEntities(Instance in) {
        BoundingBox box = in.bounds().expand(4);
        for (Entity e : world.getNearbyEntities(box)) {
            if (e instanceof Player) {
                continue;
            }
            String tag = e.getPersistentDataContainer().get(Keys.INSTANCE, PersistentDataType.STRING);
            if (tag != null || e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.ExperienceOrb
                    || e instanceof org.bukkit.entity.Projectile || e instanceof org.bukkit.entity.FallingBlock) {
                e.remove();
            }
        }
    }

    private void ticket(Instance in, boolean add) {
        BoundingBox b = in.bounds();
        int minCx = (int) Math.floor(b.getMinX()) >> 4;
        int maxCx = (int) Math.floor(b.getMaxX()) >> 4;
        int minCz = (int) Math.floor(b.getMinZ()) >> 4;
        int maxCz = (int) Math.floor(b.getMaxZ()) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (add) {
                    Chunk chunk = world.getChunkAt(cx, cz);
                    chunk.addPluginChunkTicket(plugin);
                } else {
                    world.removePluginChunkTicket(cx, cz, plugin);
                }
            }
        }
    }

    private void tick() {
        int left = budget;
        while (left > 0 && !pasteQueue.isEmpty()) {
            Instance in = pasteQueue.peek();
            PasteJob job = in.job();
            if (job == null) {
                pasteQueue.poll();
                continue;
            }
            int before = (int) (job.progress() * in.template().volume());
            boolean done = job.step(left);
            int after = (int) (job.progress() * in.template().volume());
            left -= Math.max(1, after - before);
            if (done) {
                pasteQueue.poll();
                in.job(null);
                finished(in);
            }
        }
    }

    private void finished(Instance in) {
        List<Consumer<Instance>> list = waiting.remove(in);
        if (list != null && !list.isEmpty()) {
            in.state(Instance.State.IN_USE);
            ticket(in, true);
            list.get(0).accept(in);
            // Extra waiters (should not happen) get another copy.
            for (int i = 1; i < list.size(); i++) {
                Consumer<Instance> c = list.get(i);
                if (!acquire(in.room(), c)) {
                    plugin.getLogger().warning("No free copy of " + in.room().id + " for a waiting game");
                }
            }
        } else {
            in.state(Instance.State.READY);
            ticket(in, false);
        }
    }

    public int queued() {
        return pasteQueue.size();
    }
}
