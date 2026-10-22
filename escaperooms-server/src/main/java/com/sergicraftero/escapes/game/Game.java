package com.sergicraftero.escapes.game;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.action.Action;
import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.instance.Instance;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.puzzle.PuzzleTypes;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Sidebar;
import com.sergicraftero.escapes.util.Text;
import com.sergicraftero.escapes.util.Yml;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One team playing one copy of a room. */
public final class Game {

    public enum State { INTRO, RUNNING, ENDED }

    private final EscapePlugin plugin;
    private final UUID id = UUID.randomUUID();
    private final RoomDef room;
    private final Instance instance;
    private final boolean privateGame;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, Puzzle> puzzles = new LinkedHashMap<>();
    private final Map<String, Integer> hintLevel = new HashMap<>();
    private final Map<UUID, Integer> solvedBy = new HashMap<>();
    private final Map<UUID, Set<String>> zonesSeen = new HashMap<>();
    private final Map<UUID, String> answerMode = new HashMap<>();
    private final Map<UUID, Long> answerUntil = new HashMap<>();
    private final Set<Integer> timelineFired = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private final Map<UUID, Long> offlineSince = new HashMap<>();
    private final PropManager props;
    private final BossBar bar;
    private State state = State.INTRO;
    private long startedAt;
    private long endedAt;
    private long penaltyMs;
    private int hintsUsed;
    private int wrongAttempts;
    private Location checkpoint;
    private boolean won;
    private long tickCount;
    private boolean winScheduled;

    public Game(EscapePlugin plugin, RoomDef room, Instance instance, List<Player> players, boolean privateGame) {
        this.plugin = plugin;
        this.room = room;
        this.instance = instance;
        this.privateGame = privateGame;
        for (Player p : players) {
            members.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
        }
        this.props = new PropManager(this);
        this.bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_20);
    }

    // ───────────────────────── accessors ─────────────────────────

    public EscapePlugin plugin() {
        return plugin;
    }

    public UUID id() {
        return id;
    }

    public RoomDef room() {
        return room;
    }

    public Instance instance() {
        return instance;
    }

    public Template template() {
        return instance.template();
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isPrivate() {
        return privateGame;
    }

    public PropManager props() {
        return props;
    }

    public Set<UUID> members() {
        return members;
    }

    public boolean isMember(Player p) {
        return members.contains(p.getUniqueId());
    }

    public Map<String, Puzzle> puzzles() {
        return puzzles;
    }

    public int hintsUsed() {
        return hintsUsed;
    }

    public long penaltyMs() {
        return penaltyMs;
    }

    /** Online members inside the instance. */
    public List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.getWorld() == instance.world()) {
                out.add(p);
            }
        }
        return out;
    }

    public int teamSize() {
        return Math.max(1, players().size());
    }

    // ───────────────────────── positions ─────────────────────────

    public List<Block> blocks(String ref) {
        List<Block> out = new ArrayList<>();
        for (int[] p : Refs.points(ref, template())) {
            out.add(instance.block(p));
        }
        return out;
    }

    public List<Block> blocks(List<String> refs) {
        List<Block> out = new ArrayList<>();
        refs.forEach(r -> out.addAll(blocks(r)));
        return out;
    }

    public Block block(String ref) {
        return instance.block(Refs.point(ref, template()));
    }

    public Location center(String ref) {
        return instance.center(Refs.point(ref, template()));
    }

    /** Where a player stands for a marker: block centre, feet on the block, looking the marker's facing. */
    public Location standLocation(String ref) {
        int[] p = Refs.point(ref, template());
        Location l = new Location(instance.world(), instance.originX() + p[0] + 0.5, instance.originY() + p[1],
                instance.originZ() + p[2] + 0.5);
        String facing = Refs.facing(ref, template());
        if (facing != null) {
            l.setYaw(switch (facing) {
                case "north" -> 180f;
                case "east" -> -90f;
                case "west" -> 90f;
                default -> 0f;
            });
        }
        return l;
    }

    public BoundingBox box(String ref) {
        Template.Marker m = Refs.box(ref, template());
        return m == null ? null : instance.box(m);
    }

    public void tagEntity(Entity e) {
        e.getPersistentDataContainer().set(Keys.INSTANCE, PersistentDataType.STRING, instance.key());
        e.setPersistent(false);
    }

    // ───────────────────────── scheduling ─────────────────────────

    public BukkitTask later(Runnable r, long ticks) {
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!finished) {
                r.run();
            }
        }, Math.max(1, ticks));
        tasks.add(t);
        return t;
    }

    public BukkitTask every(Runnable r, long period) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, r, period, period);
        tasks.add(t);
        return t;
    }


    // ───────────────────────── messaging ─────────────────────────

    public void broadcast(Component c) {
        players().forEach(p -> p.sendMessage(c));
    }

    public void broadcast(String key, Object... pairs) {
        Component c = Msg.get(key, pairs);
        broadcast(c);
    }

    public void sound(String key, float volume, float pitch) {
        Sound s = Actions.sound(key, volume, pitch);
        players().forEach(p -> p.playSound(s, Sound.Emitter.self()));
    }

    public void broadcastObjective(String text) {
        Component c = Msg.parse(text);
        players().forEach(p -> {
            p.sendMessage(Msg.get("game.new-objective", "objective", c));
        });
    }

    // ───────────────────────── items ─────────────────────────

    public ItemStack item(String id, int amount) {
        return plugin.items().create(room, id, amount);
    }

    public void giveItem(Player p, String id, int amount) {
        ItemStack stack = item(id, amount);
        if (stack == null) {
            plugin.getLogger().warning("[" + room.id + "] Unknown item '" + id + "'");
            return;
        }
        give(p, stack);
    }

    public void give(Player p, ItemStack stack) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            // full inventory: drop at their feet (pickup is allowed, drop by hand is not)
            p.getWorld().dropItem(p.getLocation(), rest, i -> i.setOwner(p.getUniqueId()));
        }
        p.playSound(Actions.sound("entity.item.pickup", 0.8f, 1.2f), Sound.Emitter.self());
        inventoryChanged(p);
    }

    /** Removes up to {@code amount} of a room item from the whole team. */
    public int takeItem(String id, int amount) {
        int removed = 0;
        for (Player p : players()) {
            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length && removed < amount; i++) {
                ItemStack it = contents[i];
                if (it == null || !id.equals(Keys.itemId(it))) {
                    continue;
                }
                int take = Math.min(it.getAmount(), amount - removed);
                removed += take;
                if (take >= it.getAmount()) {
                    p.getInventory().setItem(i, null);
                } else {
                    it.setAmount(it.getAmount() - take);
                }
            }
        }
        return removed;
    }

    public boolean teamHas(String id) {
        for (Player p : players()) {
            if (has(p, id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean has(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && id.equals(Keys.itemId(it))) {
                return true;
            }
        }
        return false;
    }

    /** Removes one of the held item (or of any stack with that id). */
    public boolean consume(Player p, String id) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (id.equals(Keys.itemId(hand))) {
            hand.setAmount(hand.getAmount() - 1);
            return true;
        }
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && id.equals(Keys.itemId(it))) {
                it.setAmount(it.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    public void inventoryChanged(Player p) {
        if (state == State.ENDED) {
            return;
        }
        for (Puzzle pz : puzzles.values()) {
            if (pz.isActive()) {
                pz.onInventory(p);
            }
        }
    }

    // ───────────────────────── lifecycle ─────────────────────────

    /** Room pasted, players teleported: set everything up and play the intro. */
    public void start(List<Player> starting) {
        for (PuzzleDef def : room.puzzles.values()) {
            Puzzle pz = PuzzleTypes.create(def);
            if (pz == null) {
                plugin.getLogger().warning("[" + room.id + "] Unknown puzzle type '" + def.type() + "' (" + def.id() + ")");
                continue;
            }
            puzzles.put(def.id(), pz);
            try {
                pz.attach(this);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("[" + room.id + "] Puzzle " + def.id() + " failed to set up: " + e);
                e.printStackTrace();
            }
        }
        props.setup();
        checkpoint = standLocation(room.spawn);
        for (Player p : starting) {
            prepare(p);
            p.teleport(checkpoint);
        }
        bar.name(Msg.get("game.bar-intro", "room", Msg.parse(room.name)));
        players().forEach(p -> p.showBossBar(bar));

        long introTicks = Math.max(40, room.intro.size() * room.introInterval);
        for (int i = 0; i < room.intro.size(); i++) {
            String line = room.intro.get(i);
            long at = i * room.introInterval;
            later(() -> {
                Component c = Msg.parse(line);
                Title t = Title.title(Component.empty(), c, Title.Times.times(Duration.ofMillis(400),
                        Duration.ofMillis(Math.max(500, room.introInterval * 50 - 800)), Duration.ofMillis(400)));
                players().forEach(pl -> {
                    pl.showTitle(t);
                    pl.sendMessage(Msg.get("game.intro-line", "line", c));
                });
            }, at + 1);
        }
        later(this::begin, introTicks + 20);
        every(this::tick, 5);
    }

    private void begin() {
        state = State.RUNNING;
        startedAt = System.currentTimeMillis();
        Title t = Title.title(Msg.get("game.start-title"), Msg.get("game.start-subtitle",
                "time", Text.duration(room.timeLimitMs)), Title.Times.times(Duration.ofMillis(200),
                Duration.ofMillis(2000), Duration.ofMillis(600)));
        players().forEach(p -> {
            p.showTitle(t);
            p.playSound(Actions.sound("block.end_portal_frame.fill", 1f, 0.8f), Sound.Emitter.self());
        });
        runActions(room.onStart, null);
        updateActivation();
        broadcastObjectives();
    }

    /** Puts a player in game state: adventure, clean inventory, room items, notebook. */
    public void prepare(Player p) {
        p.closeInventory();
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHealth == null ? 20 : maxHealth.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setLevel(0);
        p.setExp(0);
        for (String item : room.startItems) {
            ItemStack it = item(item, 1);
            if (it != null) {
                p.getInventory().addItem(it);
            }
        }
        p.getInventory().setItem(8, plugin.hotbar().gameNotebook());
        if (room.playerTime >= 0) {
            p.setPlayerTime(room.playerTime, false);
        } else {
            p.resetPlayerTime();
        }
        p.setPlayerWeather(room.rain ? WeatherType.DOWNFALL : WeatherType.CLEAR);
        Sidebar sb = new Sidebar(p, Msg.get("game.sidebar-title"));
        sidebars.put(p.getUniqueId(), sb);
        p.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    private void tick() {
        if (state == State.ENDED) {
            return;
        }
        tickCount++;
        if (state == State.RUNNING) {
            for (Puzzle pz : puzzles.values()) {
                if (pz.isActive()) {
                    try {
                        pz.tick();
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("[" + room.id + "] " + pz.id() + " tick: " + e);
                    }
                }
            }
        }
        props.tick();
        expireAnswers();
        if (tickCount % 4 == 0) {
            second();
        }
    }

    private void second() {
        if (state == State.RUNNING) {
            long elapsed = elapsed();
            long remaining = room.timeLimitMs - elapsed;
            if (remaining <= 0) {
                lose("game.lose-reason-time");
                return;
            }
            float progress = (float) Math.max(0, Math.min(1, remaining / (double) room.timeLimitMs));
            bar.progress(progress);
            bar.color(remaining < 5 * 60_000 ? BossBar.Color.RED : remaining < 15 * 60_000 ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
            bar.name(Msg.get("game.bar", "room", Msg.parse(room.name), "time", Text.time(remaining),
                    "solved", solvedCount(), "total", room.mainPuzzles().size()));
            if (remaining <= 60_000 && remaining > 59_000) {
                broadcast("game.one-minute");
                sound("block.note_block.bell", 1f, 0.5f);
            } else if (remaining <= 5 * 60_000 && remaining > 5 * 60_000 - 1000) {
                broadcast("game.five-minutes");
                sound("block.note_block.bell", 1f, 0.7f);
            }
            timeline(elapsed, remaining);
            ambience();
        }
        updateSidebars();
        checkOffline();
    }

    public long elapsed() {
        if (startedAt == 0) {
            return 0;
        }
        long end = endedAt > 0 ? endedAt : System.currentTimeMillis();
        return end - startedAt + penaltyMs;
    }

    public void addPenalty(long ms) {
        penaltyMs += ms;
        if (ms > 0) {
            broadcast("game.penalty", "seconds", ms / 1000);
        } else if (ms < 0) {
            broadcast("game.bonus", "seconds", -ms / 1000);
        }
    }

    private void timeline(long elapsed, long remaining) {
        for (int i = 0; i < room.timeline.size(); i++) {
            if (timelineFired.contains(i)) {
                continue;
            }
            ConfigurationSection s = room.timeline.get(i);
            boolean due;
            if (s.contains("remaining")) {
                due = remaining <= Yml.ticks(s.get("remaining"), 0) * 50;
            } else {
                due = elapsed >= Yml.ticks(s.get("at"), 0) * 50;
            }
            String unless = s.getString("unless-solved");
            if (due) {
                timelineFired.add(i);
                if (unless != null && isSolved(unless)) {
                    continue;
                }
                runActions(Actions.parseList(s, "actions", plugin.getLogger(), room.id + "/timeline"), null);
            }
        }
    }

    private final Map<Integer, Long> ambienceNext = new HashMap<>();

    private void ambience() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < room.ambience.size(); i++) {
            ConfigurationSection s = room.ambience.get(i);
            long next = ambienceNext.getOrDefault(i, now + (long) (Math.random() * 10_000));
            if (now < next) {
                ambienceNext.putIfAbsent(i, next);
                continue;
            }
            long interval = Yml.ticks(s.get("interval"), 600) * 50;
            ambienceNext.put(i, now + interval / 2 + (long) (Math.random() * interval));
            String sound = s.getString("sound");
            String at = s.getString("at");
            String zone = s.getString("zone");
            float volume = (float) s.getDouble("volume", 0.6);
            float pitch = (float) (s.getDouble("pitch", 1.0) + (Math.random() - 0.5) * s.getDouble("pitch-variance", 0.2));
            if (sound == null) {
                continue;
            }
            Sound snd = Actions.sound(sound, volume, pitch);
            BoundingBox zoneBox = zone == null ? null : box(zone);
            for (Player p : players()) {
                if (zoneBox != null && !zoneBox.contains(p.getLocation().toVector())) {
                    continue;
                }
                if (at != null) {
                    Location l = center(at);
                    p.playSound(snd, l.getX(), l.getY(), l.getZ());
                } else {
                    p.playSound(snd, Sound.Emitter.self());
                }
            }
        }
    }

    // ───────────────────────── puzzles ─────────────────────────

    public boolean isSolved(String puzzleId) {
        Puzzle p = puzzles.get(puzzleId);
        return p != null && p.isSolved();
    }

    public int solvedCount() {
        int n = 0;
        for (PuzzleDef d : room.mainPuzzles()) {
            if (isSolved(d.id())) {
                n++;
            }
        }
        return n;
    }

    /** Activates every locked puzzle whose requirements are now solved. */
    private void updateActivation() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Puzzle pz : puzzles.values()) {
                if (pz.state() != Puzzle.State.LOCKED) {
                    continue;
                }
                boolean ready = true;
                for (String req : pz.def().requires()) {
                    if (!isSolved(req)) {
                        ready = false;
                        break;
                    }
                }
                if (ready) {
                    pz.setState(Puzzle.State.ACTIVE);
                    try {
                        pz.onActivate();
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("[" + room.id + "] " + pz.id() + " activate: " + e);
                    }
                    runActions(pz.def().onActivate(), null);
                    changed = true;
                    if ("gate".equals(pz.def().type())) {
                        pz.setState(Puzzle.State.SOLVED);
                        afterSolve(pz, null);
                    }
                }
            }
        }
        props.refresh();
    }

    public void solve(String puzzleId, Player by) {
        Puzzle pz = puzzles.get(puzzleId);
        if (pz == null || pz.isSolved() || state == State.ENDED) {
            return;
        }
        pz.setState(Puzzle.State.SOLVED);
        afterSolve(pz, by);
        updateActivation();
        if (!winScheduled && solvedCount() >= room.mainPuzzles().size() && room.yml.getBoolean("auto-win", true)) {
            winScheduled = true;
            later(this::win, Yml.ticks(room.yml.get("win-delay"), 80));
        }
    }

    private void afterSolve(Puzzle pz, Player by) {
        try {
            pz.onSolved();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[" + room.id + "] " + pz.id() + " solved hook: " + e);
        }
        if (by != null) {
            solvedBy.merge(by.getUniqueId(), 1, Integer::sum);
            plugin.progress().puzzleSolved(by, room, pz.def());
        }
        if (!pz.def().hidden() && !"gate".equals(pz.def().type())) {
            Component name = Msg.parse(pz.def().name());
            broadcast("game.puzzle-solved", "puzzle", name, "player", by == null ? "?" : by.getName(),
                    "solved", solvedCount(), "total", room.mainPuzzles().size());
            sound("entity.player.levelup", 0.7f, 1.4f);
        }
        runActions(pz.def().onSolve(), by);
    }

    public void puzzleFailed(Puzzle pz, Player p) {
        wrongAttempts++;
        p.playSound(Actions.sound("block.note_block.bass", 1f, 0.5f), Sound.Emitter.self());
        runActions(pz.def().onFail(), p);
        long penalty = pz.def().section().contains("penalty")
                ? Yml.ticks(pz.def().section().get("penalty"), 0) * 50 : room.wrongPenaltyMs;
        if (penalty > 0) {
            addPenalty(penalty);
        }
    }

    public void runActions(List<Action> actions, Player trigger) {
        for (Action a : actions) {
            try {
                a.run(this, trigger);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[" + room.id + "] Action failed: " + e);
            }
        }
    }

    /** Active puzzles shown as objectives (visible, not gates). */
    public List<Puzzle> objectives() {
        List<Puzzle> out = new ArrayList<>();
        for (Puzzle pz : puzzles.values()) {
            if (pz.isActive() && !pz.def().hidden() && !"gate".equals(pz.def().type())
                    && !pz.def().objective().isEmpty()) {
                out.add(pz);
            }
        }
        return out;
    }

    private void broadcastObjectives() {
        List<Puzzle> obj = objectives();
        if (obj.isEmpty()) {
            return;
        }
        broadcast("game.objectives-header");
        for (Puzzle pz : obj) {
            broadcast("game.objective-line", "objective", Msg.parse(pz.def().objective()));
        }
    }

    // ───────────────────────── hints ─────────────────────────

    public int hintLevel(String puzzleId) {
        return hintLevel.getOrDefault(puzzleId, 0);
    }

    public void useHint(Player p, String puzzleId) {
        Puzzle pz = puzzles.get(puzzleId);
        if (pz == null || !pz.isActive() || state != State.RUNNING) {
            Msg.send(p, "hints.not-available");
            return;
        }
        List<String> hints = pz.def().hints();
        int level = hintLevel(puzzleId);
        if (level >= hints.size()) {
            Msg.send(p, "hints.no-more");
            return;
        }
        if (hintsUsed >= room.maxHints) {
            Msg.send(p, "hints.limit", "max", room.maxHints);
            return;
        }
        hintLevel.put(puzzleId, level + 1);
        hintsUsed++;
        penaltyMs += room.hintPenaltyMs;
        broadcast("hints.used", "player", p.getName(), "puzzle", Msg.parse(pz.def().name()),
                "level", level + 1, "max", hints.size(), "seconds", room.hintPenaltyMs / 1000);
        broadcast("hints.text", "hint", Msg.parse(hints.get(level)));
        sound("block.amethyst_block.chime", 1f, 1.2f);
    }

    // ───────────────────────── chat answers ─────────────────────────

    public void enterAnswerMode(Player p, String puzzleId, long seconds) {
        answerMode.put(p.getUniqueId(), puzzleId);
        answerUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000);
    }

    public String answerMode(Player p) {
        return answerMode.get(p.getUniqueId());
    }

    public void clearAnswerMode(Player p) {
        answerMode.remove(p.getUniqueId());
        answerUntil.remove(p.getUniqueId());
    }

    private void expireAnswers() {
        long now = System.currentTimeMillis();
        answerUntil.entrySet().removeIf(e -> {
            if (e.getValue() < now) {
                answerMode.remove(e.getKey());
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    Msg.send(p, "puzzle.answer-timeout");
                }
                return true;
            }
            return false;
        });
    }

    /** Team chat routed here (main thread). Returns true when the message must not be shown to the team. */
    public boolean chat(Player p, String message) {
        if (state != State.RUNNING) {
            return false;
        }
        String mode = answerMode.get(p.getUniqueId());
        if (mode != null) {
            Puzzle pz = puzzles.get(mode);
            clearAnswerMode(p);
            if (pz != null && pz.isActive()) {
                pz.onChat(p, message, true);
            }
            return true;
        }
        for (Puzzle pz : puzzles.values()) {
            if (pz.isActive() && pz.onChat(p, message, false)) {
                return false; // solved through open chat: still show what was said
            }
        }
        return false;
    }

    // ───────────────────────── movement ─────────────────────────

    public void moved(Player p, Location to) {
        if (to.getY() < instance.originY() + room.voidY) {
            p.teleport(checkpoint == null ? standLocation(room.spawn) : checkpoint);
            p.setFallDistance(0);
            Msg.send(p, "game.fell");
            return;
        }
        Block b = to.getBlock();
        if (state == State.RUNNING) {
            for (Puzzle pz : puzzles.values()) {
                if (pz.isActive()) {
                    pz.onMove(p, b);
                }
            }
        }
        props.moved(p, to);
        zones(p, to);
    }

    private void zones(Player p, Location to) {
        for (ConfigurationSection z : room.zones) {
            String region = z.getString("region");
            BoundingBox box = region == null ? null : box(region);
            if (box == null || !box.contains(to.toVector())) {
                continue;
            }
            String zid = z.getString("id", region);
            Set<String> seen = zonesSeen.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
            if (seen.add(zid)) {
                Title t = Title.title(Msg.parse(z.getString("name", "")), Msg.parse(z.getString("subtitle", "")),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700)));
                p.showTitle(t);
                p.playSound(Actions.sound(z.getString("sound", "block.beacon.ambient"), 0.8f, 1.2f), Sound.Emitter.self());
                if (z.isLong("time") || z.isInt("time")) {
                    p.setPlayerTime(z.getLong("time"), false);
                }
            }
        }
    }

    public void setCheckpoint(String ref) {
        checkpoint = standLocation(ref);
    }

    public Location checkpoint() {
        return checkpoint;
    }

    // ───────────────────────── interaction routing ─────────────────────────

    /** Returns true when the click was handled by the room (the vanilla action is cancelled). */
    public boolean interact(Player p, Block b, boolean right, ItemStack hand) {
        if (state == State.ENDED) {
            return true;
        }
        for (Puzzle pz : puzzles.values()) {
            if (!pz.owns(b)) {
                continue;
            }
            if (pz.isActive() && state == State.RUNNING) {
                if (pz.onInteract(p, b, right, hand)) {
                    return true;
                }
            } else if (pz.state() == Puzzle.State.LOCKED) {
                String msg = pz.def().section().getString("locked-message");
                if (msg == null) {
                    Msg.actionBar(p, "puzzle.locked");
                } else if (!msg.isEmpty()) {
                    p.sendActionBar(Msg.parse(msg));
                }
                return true;
            } else if (pz.isSolved() && pz.def().section().getBoolean("lock-when-solved", true)) {
                return true;
            }
        }
        return state == State.RUNNING && props.interact(p, b, right, hand);
    }

    public boolean interactEntity(Player p, Entity e, boolean right, ItemStack hand) {
        if (state == State.ENDED) {
            return true;
        }
        // Active puzzles first (a suspect to accuse, a frame to fill); otherwise the prop behaviour
        // (an NPC keeps talking before and after its puzzle).
        for (Puzzle pz : puzzles.values()) {
            if (pz.ownsEntity(e) && pz.isActive() && state == State.RUNNING && pz.onEntity(p, e, right, hand)) {
                return true;
            }
        }
        props.interactEntity(p, e, right, hand);
        return true; // nothing else in a room may be touched (frames, stands, displays)
    }

    // ───────────────────────── sidebar ─────────────────────────

    private void updateSidebars() {
        List<Component> lines = new ArrayList<>();
        long remaining = state == State.RUNNING ? room.timeLimitMs - elapsed() : room.timeLimitMs;
        lines.add(Component.empty());
        lines.add(Msg.get("game.sidebar-room", "room", Msg.parse(room.name)));
        lines.add(Msg.get("game.sidebar-time", "time", Text.time(Math.max(0, remaining))));
        lines.add(Msg.get("game.sidebar-progress", "solved", solvedCount(), "total", room.mainPuzzles().size()));
        lines.add(Msg.get("game.sidebar-hints", "used", hintsUsed, "max", room.maxHints));
        lines.add(Component.empty());
        List<Puzzle> obj = objectives();
        if (!obj.isEmpty()) {
            lines.add(Msg.get("game.sidebar-objectives"));
            for (int i = 0; i < Math.min(5, obj.size()); i++) {
                String text = Msg.strip(obj.get(i).def().objective());
                if (text.length() > 28) {
                    text = text.substring(0, 27) + "…";
                }
                lines.add(Msg.get("game.sidebar-objective", "objective", Msg.escape(text)));
            }
            lines.add(Component.empty());
        }
        lines.add(Msg.get("game.sidebar-footer"));
        for (Player p : players()) {
            Sidebar sb = sidebars.get(p.getUniqueId());
            if (sb != null) {
                sb.lines(lines);
            }
        }
    }

    // ───────────────────────── players coming and going ─────────────────────────

    /** A disconnected member logged back in while the game still runs. */
    public void rejoin(Player p) {
        offlineSince.remove(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state == State.ENDED) {
                plugin.lobby().send(p);
                return;
            }
            p.teleport(checkpoint);
            Sidebar sb = new Sidebar(p, Msg.get("game.sidebar-title"));
            sidebars.put(p.getUniqueId(), sb);
            p.showBossBar(bar);
            if (room.playerTime >= 0) {
                p.setPlayerTime(room.playerTime, false);
            }
            p.setPlayerWeather(room.rain ? WeatherType.DOWNFALL : WeatherType.CLEAR);
            broadcast("game.rejoined", "player", p.getName());
        });
    }

    public void disconnected(Player p) {
        offlineSince.put(p.getUniqueId(), System.currentTimeMillis());
        clearAnswerMode(p);
        broadcast("game.disconnected", "player", p.getName());
    }

    private void checkOffline() {
        long grace = plugin.getConfig().getLong("game.reconnect-grace-seconds", 180) * 1000;
        long now = System.currentTimeMillis();
        offlineSince.entrySet().removeIf(e -> {
            if (now - e.getValue() > grace) {
                members.remove(e.getKey());
                return true;
            }
            return false;
        });
        if (players().isEmpty() && state != State.ENDED) {
            boolean anyoneComing = !offlineSince.isEmpty();
            if (!anyoneComing) {
                abandon();
            }
        }
    }

    /** Leaves voluntarily (or kicked out by a command). */
    public void leave(Player p, boolean toLobby) {
        members.remove(p.getUniqueId());
        offlineSince.remove(p.getUniqueId());
        cleanupPlayer(p);
        if (toLobby) {
            plugin.lobby().send(p);
        }
        broadcast("game.left", "player", p.getName());
        plugin.games().forget(p);
        if (members.isEmpty()) {
            abandon();
        }
    }

    private void cleanupPlayer(Player p) {
        p.hideBossBar(bar);
        sidebars.remove(p.getUniqueId());
        clearAnswerMode(p);
        p.resetPlayerTime();
        p.resetPlayerWeather();
    }

    // ───────────────────────── ending ─────────────────────────

    public int stars() {
        long t = elapsed();
        if (t <= room.threeStarMs && hintsUsed <= room.threeStarHints) {
            return 3;
        }
        if (t <= room.twoStarMs && hintsUsed <= room.twoStarHints) {
            return 2;
        }
        return 1;
    }

    public void win() {
        if (state != State.RUNNING) {
            return;
        }
        won = true;
        endedAt = System.currentTimeMillis();
        state = State.ENDED;
        long time = elapsed();
        int stars = stars();
        runActions(room.onWin, null);
        Title t = Title.title(Msg.get("game.win-title"), Msg.get("game.win-subtitle", "time", Text.time(time),
                "stars", Text.stars(stars, 3)), Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(5000), Duration.ofMillis(1000)));
        List<Player> team = players();
        List<String> teamNames = new ArrayList<>();
        team.forEach(p -> teamNames.add(p.getName()));
        for (Player p : team) {
            p.showTitle(t);
            p.playSound(Actions.sound("ui.toast.challenge_complete", 1f, 1f), Sound.Emitter.self());
            plugin.cosmetics().victory(p);
        }
        broadcast("game.win-summary", "room", Msg.parse(room.name), "time", Text.time(time), "stars", Text.stars(stars, 3),
                "hints", hintsUsed, "penalty", Text.time(penaltyMs));
        plugin.progress().roomCompleted(this, team, time, stars, solvedBy);
        plugin.storage().recordRun(room.id, teamNames, time, hintsUsed, stars, privateGame);
        finishLater(plugin.getConfig().getLong("game.end-delay-seconds", 12) * 20);
    }

    public void lose(String reasonKey) {
        if (state != State.RUNNING) {
            return;
        }
        endedAt = System.currentTimeMillis();
        state = State.ENDED;
        Title t = Title.title(Msg.get("game.lose-title"), Msg.get(reasonKey), Title.Times.times(Duration.ofMillis(300),
                Duration.ofMillis(4000), Duration.ofMillis(1000)));
        for (Player p : players()) {
            p.showTitle(t);
            p.playSound(Actions.sound("entity.wither.death", 0.5f, 1.2f), Sound.Emitter.self());
        }
        broadcast("game.lose-summary", "room", Msg.parse(room.name), "solved", solvedCount(),
                "total", room.mainPuzzles().size());
        plugin.progress().roomFailed(this, players(), solvedBy);
        finishLater(plugin.getConfig().getLong("game.end-delay-seconds", 12) * 20);
    }

    /** Everybody left: close the game without rewards. */
    public void abandon() {
        if (finishing) {
            return;
        }
        state = State.ENDED;
        if (endedAt == 0) {
            endedAt = System.currentTimeMillis();
        }
        finishLater(1);
    }

    private boolean finishing;
    private boolean finished;

    private void finishLater(long ticks) {
        if (finishing) {
            return;
        }
        finishing = true;
        later(this::finish, ticks);
    }

    private void finish() {
        finished = true;
        for (Player p : players()) {
            cleanupPlayer(p);
            plugin.games().forget(p);
            plugin.lobby().send(p);
        }
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                plugin.games().forget(p);
            }
        }
        for (Puzzle pz : puzzles.values()) {
            try {
                pz.cleanup();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[" + room.id + "] cleanup " + pz.id() + ": " + e);
            }
        }
        props.cleanup();
        for (BukkitTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
        plugin.games().ended(this);
        plugin.instances().release(instance);
    }

    public boolean won() {
        return won;
    }

    public String name(UUID u) {
        return names.getOrDefault(u, "?");
    }
}
