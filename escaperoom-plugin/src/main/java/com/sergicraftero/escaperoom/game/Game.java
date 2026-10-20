package com.sergicraftero.escaperoom.game;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.action.Actions;
import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.puzzle.Puzzle;
import com.sergicraftero.escaperoom.puzzle.PuzzleState;
import com.sergicraftero.escaperoom.room.Room;
import com.sergicraftero.escaperoom.room.Trigger;
import com.sergicraftero.escaperoom.util.BlockPos;
import com.sergicraftero.escaperoom.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime of one room: queue, countdown, the running game and the reset afterwards. There is
 * exactly one Game per room (a room is a physical place, so only one group can play at a time).
 */
public final class Game {

    public enum State { WAITING, COUNTDOWN, RUNNING, ENDING, RESETTING, DISABLED }

    public enum EndReason { WIN, TIME_UP, ABORT, SHUTDOWN }

    private final EscapeRoomPlugin plugin;
    private final GameManager manager;
    private Room room;

    private volatile State state = State.WAITING;
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> players = new LinkedHashSet<>();
    private final Map<UUID, String> roster = new LinkedHashMap<>();
    private final Map<String, Integer> hintLevels = new LinkedHashMap<>();
    private final Set<String> firedTriggers = new HashSet<>();
    private final Set<Integer> warned = new HashSet<>();
    private final List<Entity> tempEntities = new ArrayList<>();

    private int runId;
    private long startMillis;
    private long timeAdjustMillis;
    private int hintsUsed;
    private boolean solo;
    private boolean winScheduled;
    private long ticks;
    private int countdown;
    private BukkitTask countdownTask;
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_20);

    Game(EscapeRoomPlugin plugin, GameManager manager, Room room) {
        this.plugin = plugin;
        this.manager = manager;
        this.room = room;
        if (!room.enabled()) {
            state = State.DISABLED;
        }
    }

    // ───────────────────────── accessors ─────────────────────────

    public EscapeRoomPlugin plugin() {
        return plugin;
    }

    public Room room() {
        return room;
    }

    void room(Room room) {
        this.room = room;
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public int runId() {
        return runId;
    }

    public World world() {
        return room.world();
    }

    public boolean isSolo() {
        return solo;
    }

    public boolean isPlaying(UUID uuid) {
        return players.contains(uuid);
    }

    public boolean isQueued(UUID uuid) {
        return queue.contains(uuid);
    }

    public int playerCount() {
        return players.size();
    }

    public int queueSize() {
        return queue.size();
    }

    public int hintsUsed() {
        return hintsUsed;
    }

    /** Online players currently inside the game. */
    public List<Player> participants() {
        List<Player> list = new ArrayList<>(players.size());
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    private List<Player> queued() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : queue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    public long remainingMillis() {
        if (state != State.RUNNING && state != State.ENDING) {
            return room.timeLimit() * 1000L;
        }
        return room.timeLimit() * 1000L - (System.currentTimeMillis() - startMillis) + timeAdjustMillis;
    }

    /** Elapsed time including hint penalties (what goes into the ranking). */
    public long effectiveMillis() {
        return (System.currentTimeMillis() - startMillis) - timeAdjustMillis;
    }

    // ───────────────────────── messaging ─────────────────────────

    public void broadcast(Component component) {
        participants().forEach(p -> p.sendMessage(component));
    }

    public void msg(String key, TagResolver... resolvers) {
        participants().forEach(p -> plugin.messages().send(p, key, resolvers));
    }

    private void msgQueue(String key, TagResolver... resolvers) {
        queued().forEach(p -> plugin.messages().send(p, key, resolvers));
    }

    public void actionBar(Component component) {
        participants().forEach(p -> p.sendActionBar(component));
    }

    private void sound(Iterable<Player> to, String key, float volume, float pitch) {
        Sound sound = Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch);
        to.forEach(p -> p.playSound(sound, Sound.Emitter.self()));
    }

    private void title(Iterable<Player> to, Component title, Component subtitle, int stayTicks) {
        Title t = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(250),
                Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(500)));
        to.forEach(p -> p.showTitle(t));
    }

    public void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    public void trackTemp(Entity entity) {
        entity.getPersistentDataContainer().set(plugin.keys().temp,
                org.bukkit.persistence.PersistentDataType.STRING, room.id());
        tempEntities.add(entity);
    }

    public void adjustTime(long millis) {
        timeAdjustMillis += millis;
        updateBossBar();
    }

    // ───────────────────────── queue & countdown ─────────────────────────

    public boolean join(Player player) {
        UUID uuid = player.getUniqueId();
        TagResolver roomName = Messages.phm("room", room.displayName());
        if (state == State.DISABLED || !room.enabled()) {
            plugin.messages().send(player, "join-disabled", roomName);
            return false;
        }
        if (queue.contains(uuid) || players.contains(uuid)) {
            plugin.messages().send(player, "join-already", roomName);
            return false;
        }
        if (queue.size() >= room.maxPlayers()) {
            plugin.messages().send(player, "join-full", roomName);
            return false;
        }
        queue.add(uuid);
        manager.index(uuid, this);
        Location lobby = room.lobby();
        if (lobby != null && lobby.getWorld() != null) {
            player.teleport(lobby);
        }
        if (state == State.WAITING || state == State.COUNTDOWN) {
            plugin.messages().send(player, "join-success", roomName,
                    Messages.ph("count", queue.size()), Messages.ph("min", room.minPlayers()),
                    Messages.ph("max", room.maxPlayers()));
            queued().stream().filter(p -> !p.equals(player)).forEach(p -> plugin.messages().send(p, "join-other",
                    Messages.ph("player", player.getName()), Messages.ph("count", queue.size()),
                    Messages.ph("max", room.maxPlayers())));
        } else {
            plugin.messages().send(player, "join-queued", roomName);
        }
        checkStart();
        return true;
    }

    private void checkStart() {
        if (state == State.WAITING && queue.size() >= room.minPlayers()) {
            startCountdown();
        } else if (state == State.COUNTDOWN && queue.size() >= room.maxPlayers()) {
            countdown = Math.min(countdown, plugin.getConfig().getInt("game.full-countdown", 5));
        }
    }

    private void startCountdown() {
        state = State.COUNTDOWN;
        countdown = room.countdown() >= 0 ? room.countdown() : plugin.getConfig().getInt("game.default-countdown", 15);
        if (queue.size() >= room.maxPlayers()) {
            countdown = Math.min(countdown, plugin.getConfig().getInt("game.full-countdown", 5));
        }
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queue.size() < room.minPlayers()) {
                cancelCountdown();
                msgQueue("countdown-cancelled");
                return;
            }
            if (countdown <= 0) {
                cancelCountdownTask();
                start();
                return;
            }
            List<Player> waiting = queued();
            if (countdown <= 5 || countdown % 10 == 0) {
                title(waiting, plugin.messages().get("countdown-title", Messages.ph("seconds", countdown)),
                        plugin.messages().get("countdown-subtitle", Messages.phm("room", room.displayName())), 22);
                sound(waiting, "block.note_block.hat", 1f, countdown <= 3 ? 1.6f : 1f);
            }
            Component bar = plugin.messages().get("countdown-actionbar", Messages.ph("seconds", countdown),
                    Messages.ph("count", queue.size()), Messages.ph("max", room.maxPlayers()));
            waiting.forEach(p -> p.sendActionBar(bar));
            countdown--;
        }, 0L, 20L);
    }

    private void cancelCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelCountdown() {
        cancelCountdownTask();
        if (state == State.COUNTDOWN) {
            state = State.WAITING;
        }
    }

    /** Forces the start (admin), ignoring the minimum player count. */
    public boolean forceStart() {
        if ((state != State.WAITING && state != State.COUNTDOWN) || queue.isEmpty()) {
            return false;
        }
        cancelCountdownTask();
        start();
        return true;
    }

    // ───────────────────────── start ─────────────────────────

    private void start() {
        World world = world();
        Location spawn = room.spawn();
        if (world == null || spawn == null) {
            plugin.getLogger().severe("Room " + room.id() + " has no valid world/spawn.");
            state = State.WAITING;
            return;
        }
        state = State.RUNNING;
        runId++;
        players.clear();
        roster.clear();
        hintLevels.clear();
        firedTriggers.clear();
        warned.clear();
        timeAdjustMillis = 0;
        hintsUsed = 0;
        winScheduled = false;
        ticks = 0;

        List<UUID> starting = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(queue)) {
            if (starting.size() >= room.maxPlayers()) {
                break;
            }
            if (Bukkit.getPlayer(uuid) != null) {
                starting.add(uuid);
            }
            queue.remove(uuid);
        }
        if (starting.isEmpty()) {
            state = State.WAITING;
            return;
        }
        plugin.snapshots().markDirty(room.id(), true);
        players.addAll(starting);
        solo = players.size() == 1;

        for (Puzzle puzzle : room.puzzles().values()) {
            puzzle.state(PuzzleState.LOCKED);
        }
        setupContainers(world);
        for (Puzzle puzzle : room.puzzles().values()) {
            puzzle.reset(this);
        }
        startMillis = System.currentTimeMillis();

        for (Player p : participants()) {
            roster.put(p.getUniqueId(), p.getName());
            preparePlayer(p, spawn);
        }
        updateBossBar();
        participants().forEach(p -> p.showBossBar(bossBar));
        title(participants(), plugin.messages().get("start-title", Messages.phm("room", room.displayName())),
                plugin.messages().get(solo ? "start-subtitle-solo" : "start-subtitle-team",
                        Messages.ph("time", Text.formatTime(room.timeLimit() * 1000L))), 70);
        sound(participants(), "block.end_portal_frame.fill", 1f, 0.6f);
        msg(solo ? "start-message-solo" : "start-message-team", Messages.phm("room", room.displayName()),
                Messages.ph("time", Text.formatTime(room.timeLimit() * 1000L)));

        for (String itemId : room.startItems()) {
            for (Player p : participants()) {
                ItemStack stack = Actions.createItem(this, itemId, 1);
                if (stack != null) {
                    give(p, stack);
                }
            }
        }
        room.startActions().run(this, null);
        activateReady();
        // in case a 0-requirement trigger region contains the spawn
        checkTriggers();
    }

    private void preparePlayer(Player p, Location spawn) {
        plugin.inventories().save(p, room.lobby());
        p.getInventory().clear();
        p.setItemOnCursor(null);
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFoodLevel(20);
        p.setSaturation(10f);
        p.setFireTicks(0);
        p.setLevel(0);
        p.setExp(0f);
        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }
        if (room.night()) {
            p.setPlayerTime(18000L, false);
        }
        p.teleport(spawn);
    }

    private void setupContainers(World world) {
        for (Map.Entry<BlockPos, String> e : room.lecterns().entrySet()) {
            Block block = e.getKey().block(world);
            ItemStack book = Actions.createItem(this, e.getValue(), 1);
            if (book != null && block.getState() instanceof Lectern lectern) {
                lectern.getSnapshotInventory().setItem(0, book);
                lectern.update(true, false);
            }
        }
        for (Map.Entry<BlockPos, List<String>> e : room.containers().entrySet()) {
            Block block = e.getKey().block(world);
            if (block.getState() instanceof Container container) {
                container.getSnapshotInventory().clear();
                for (String itemId : e.getValue()) {
                    String[] parts = itemId.split("\\*");
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                    ItemStack stack = Actions.createItem(this, parts[0].trim(), amount);
                    if (stack != null) {
                        container.getSnapshotInventory().addItem(stack);
                    }
                }
                container.update(true, false);
            }
        }
    }

    // ───────────────────────── ticking ─────────────────────────

    /** Called every server tick by the GameManager while running. */
    void tick() {
        if (state != State.RUNNING) {
            return;
        }
        ticks++;
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.state() == PuzzleState.ACTIVE) {
                try {
                    puzzle.tick(this, ticks);
                } catch (Exception e) {
                    plugin.getLogger().warning("Puzzle " + puzzle.id() + " tick failed: " + e);
                }
                if (state != State.RUNNING) {
                    return;
                }
            }
        }
        if (ticks % 10 == 0) {
            checkTriggers();
        }
        if (ticks % 20 == 0) {
            secondTick();
        }
    }

    private void secondTick() {
        long remaining = remainingMillis();
        updateBossBar();
        int seconds = (int) Math.ceil(remaining / 1000.0);
        for (int warning : plugin.getConfig().getIntegerList("game.time-warnings")) {
            if (seconds <= warning && warned.add(warning)) {
                msg("time-warning", Messages.ph("time", Text.formatTime(remaining)));
                sound(participants(), "block.bell.use", 1f, warning <= 30 ? 1.4f : 0.8f);
            }
        }
        if (remaining <= 0) {
            end(EndReason.TIME_UP);
            return;
        }
        for (Player p : participants()) {
            if (!room.contains(p.getLocation())) {
                p.teleport(room.spawn());
                plugin.messages().send(p, "left-region");
            }
        }
    }

    private void checkTriggers() {
        for (Trigger trigger : room.triggers()) {
            if (firedTriggers.contains(trigger.id())) {
                continue;
            }
            for (Player p : participants()) {
                if (trigger.region().contains(p.getLocation())) {
                    firedTriggers.add(trigger.id());
                    trigger.actions().run(this, p);
                    break;
                }
            }
        }
    }

    private void updateBossBar() {
        long remaining = Math.max(0, remainingMillis());
        float progress = Math.max(0f, Math.min(1f, remaining / (room.timeLimit() * 1000f)));
        bossBar.progress(progress);
        bossBar.color(progress > 0.5f ? BossBar.Color.YELLOW : progress > 0.2f ? BossBar.Color.PINK : BossBar.Color.RED);
        bossBar.name(plugin.messages().get("bossbar", Messages.ph("time", Text.formatTime(remaining)),
                Messages.phm("room", room.displayName()), Messages.ph("solved", solvedCount()),
                Messages.ph("total", room.puzzles().size())));
    }

    private long solvedCount() {
        return room.puzzles().values().stream().filter(p -> p.state() == PuzzleState.SOLVED).count();
    }

    // ───────────────────────── puzzles ─────────────────────────

    public void solve(Puzzle puzzle, Player who) {
        if (state != State.RUNNING || puzzle.state() != PuzzleState.ACTIVE) {
            return;
        }
        puzzle.state(PuzzleState.SOLVED);
        msg("puzzle-solved", Messages.phm("puzzle", puzzle.displayName()),
                Messages.ph("player", who == null ? "?" : who.getName()),
                Messages.ph("solved", solvedCount()), Messages.ph("total", room.puzzles().size()));
        sound(participants(), "entity.player.levelup", 0.7f, 1.3f);
        puzzle.onSolve().run(this, who);
        if (state != State.RUNNING) {
            return;
        }
        activateReady();
        boolean finished = room.finalPuzzle() != null
                ? room.finalPuzzle().equals(puzzle.id())
                : room.puzzles().values().stream().allMatch(p -> p.state() == PuzzleState.SOLVED);
        if (finished && !winScheduled) {
            winScheduled = true;
            int id = runId;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (runId == id && state == State.RUNNING) {
                    win();
                }
            }, Math.max(1, room.winDelay()));
        }
    }

    private void activateReady() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Puzzle puzzle : room.puzzles().values()) {
                if (puzzle.state() != PuzzleState.LOCKED) {
                    continue;
                }
                boolean ready = puzzle.requires().stream().allMatch(req -> {
                    Puzzle r = room.puzzles().get(req);
                    return r == null || r.state() == PuzzleState.SOLVED;
                });
                if (ready) {
                    puzzle.state(PuzzleState.ACTIVE);
                    puzzle.onActivate(this);
                    changed = true;
                }
            }
        }
    }

    public void useHint(Player player) {
        if (state != State.RUNNING) {
            plugin.messages().send(player, "hint-not-playing");
            return;
        }
        if (!room.hintsAllowed()) {
            plugin.messages().send(player, "hint-disabled");
            return;
        }
        if (room.maxHints() >= 0 && hintsUsed >= room.maxHints()) {
            plugin.messages().send(player, "hint-limit", Messages.ph("max", room.maxHints()));
            return;
        }
        Puzzle target = null;
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.state() == PuzzleState.ACTIVE
                    && hintLevels.getOrDefault(puzzle.id(), 0) < puzzle.hints().size()) {
                target = puzzle;
                break;
            }
        }
        if (target == null) {
            plugin.messages().send(player, "hint-none");
            return;
        }
        int level = hintLevels.getOrDefault(target.id(), 0);
        hintLevels.put(target.id(), level + 1);
        hintsUsed++;
        timeAdjustMillis -= room.hintPenalty() * 1000L;
        msg("hint", Messages.phm("puzzle", target.displayName()), Messages.ph("level", level + 1),
                Messages.ph("levels", target.hints().size()), Messages.phm("hint", target.hints().get(level)),
                Messages.ph("penalty", room.hintPenalty()), Messages.ph("player", player.getName()));
        sound(participants(), "block.amethyst_block.resonate", 1f, 1.2f);
        updateBossBar();
    }

    // ───────────────────────── leaving ─────────────────────────

    /**
     * Removes a player from the queue or the game.
     *
     * @param quitting true when the player is disconnecting
     */
    public void leave(Player player, boolean quitting) {
        UUID uuid = player.getUniqueId();
        if (queue.remove(uuid)) {
            manager.unindex(uuid);
            if (!quitting) {
                plugin.messages().send(player, "leave-queue", Messages.phm("room", room.displayName()));
            }
            return;
        }
        if (!players.remove(uuid)) {
            return;
        }
        manager.unindex(uuid);
        restorePlayer(player, room.lobby());
        if (!quitting) {
            plugin.messages().send(player, "leave-game", Messages.phm("room", room.displayName()));
        }
        msg(quitting ? "player-disconnected" : "player-left", Messages.ph("player", player.getName()));
        if (players.isEmpty() && state == State.RUNNING) {
            end(EndReason.ABORT);
        } else if (players.size() == 1 && !solo && state == State.RUNNING) {
            solo = true;
            msg("mode-solo");
            room.puzzles().values().stream().filter(p -> p.state() == PuzzleState.ACTIVE)
                    .forEach(p -> p.onModeChange(this));
        }
    }

    private void restorePlayer(Player player, Location destination) {
        player.hideBossBar(bossBar);
        player.resetPlayerTime();
        player.getInventory().clear();
        plugin.inventories().restore(player);
        if (destination != null && destination.getWorld() != null) {
            player.teleport(destination);
        }
        player.setFallDistance(0);
        player.setFireTicks(0);
    }

    // ───────────────────────── ending ─────────────────────────

    public void win() {
        end(EndReason.WIN);
    }

    public void end(EndReason reason) {
        if (state != State.RUNNING) {
            return;
        }
        state = State.ENDING;
        List<Player> online = participants();
        long time = effectiveMillis();
        boolean won = reason == EndReason.WIN;

        if (reason == EndReason.WIN || reason == EndReason.TIME_UP) {
            plugin.stats().recordRun(room.id(), new LinkedHashMap<>(roster), time, hintsUsed, won, rank -> {
                if (won && rank > 0) {
                    online.stream().filter(Player::isOnline).forEach(p -> plugin.messages().send(p, "victory-rank",
                            Messages.ph("rank", rank), Messages.phm("room", room.displayName())));
                }
            });
        }

        if (won) {
            title(online, plugin.messages().get("victory-title"),
                    plugin.messages().get("victory-subtitle", Messages.ph("time", Text.formatTimePrecise(time))), 100);
            msg("victory-message", Messages.phm("room", room.displayName()),
                    Messages.ph("time", Text.formatTimePrecise(time)), Messages.ph("hints", hintsUsed));
            sound(online, "ui.toast.challenge_complete", 1f, 1f);
            for (Player p : online) {
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.4);
            }
            giveRewards(online, time);
        } else if (reason == EndReason.TIME_UP) {
            title(online, plugin.messages().get("defeat-title"), plugin.messages().get("defeat-subtitle"), 80);
            msg("defeat-message", Messages.phm("room", room.displayName()));
            sound(online, "entity.wither.death", 0.6f, 0.6f);
        } else if (reason == EndReason.ABORT) {
            msg("game-aborted");
        }

        if (reason == EndReason.SHUTDOWN) {
            finishPlayers(room.lobby());
            cleanupTemp();
            return;
        }
        long delay = switch (reason) {
            case WIN -> plugin.getConfig().getInt("game.victory-teleport-delay", 6) * 20L;
            case TIME_UP -> 80L;
            default -> 1L;
        };
        Location destination = won ? room.exit() : room.lobby();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finishPlayers(destination);
            resetRoom();
        }, delay);
    }

    private void finishPlayers(Location destination) {
        for (UUID uuid : new ArrayList<>(players)) {
            manager.unindex(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                restorePlayer(p, destination);
            }
        }
        players.clear();
    }

    private void giveRewards(List<Player> online, long time) {
        for (Player p : online) {
            for (String command : room.rewardCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", p.getName())
                        .replace("%time%", Text.formatTime(time)).replace("%room%", room.id()));
            }
            if (room.rewardMoney() > 0 && plugin.vault() != null && plugin.vault().deposit(p, room.rewardMoney())) {
                plugin.messages().send(p, "reward-money", Messages.ph("amount", room.rewardMoney()));
            }
        }
    }

    private void cleanupTemp() {
        tempEntities.forEach(Entity::remove);
        tempEntities.clear();
    }

    /** Restores the room blocks and gets ready for the next group. */
    public void resetRoom() {
        cleanupTemp();
        state = State.RESETTING;
        for (Puzzle puzzle : room.puzzles().values()) {
            puzzle.state(PuzzleState.LOCKED);
        }
        msgQueue("room-resetting");
        plugin.snapshots().restoreRoom(room, success -> {
            plugin.snapshots().markDirty(room.id(), false);
            state = room.enabled() ? State.WAITING : State.DISABLED;
            if (state == State.WAITING) {
                msgQueue("room-ready", Messages.phm("room", room.displayName()));
                checkStart();
            }
        });
    }

    /** Admin stop or room removal. */
    public void stop() {
        cancelCountdownTask();
        if (state == State.RUNNING) {
            end(EndReason.ABORT);
        } else if (state == State.COUNTDOWN) {
            state = State.WAITING;
        }
    }

    /** Server shutdown: restore everyone synchronously; the room is reset on next startup. */
    void shutdown() {
        cancelCountdownTask();
        if (state == State.RUNNING) {
            end(EndReason.SHUTDOWN);
        } else {
            finishPlayers(room.lobby());
        }
        for (UUID uuid : new ArrayList<>(queue)) {
            manager.unindex(uuid);
        }
        queue.clear();
        cleanupTemp();
    }

    public void setEnabled(boolean enabled) {
        if (!enabled) {
            stop();
            for (Player p : queued()) {
                leave(p, false);
            }
            state = State.DISABLED;
        } else if (state == State.DISABLED) {
            state = State.WAITING;
        }
    }
}
