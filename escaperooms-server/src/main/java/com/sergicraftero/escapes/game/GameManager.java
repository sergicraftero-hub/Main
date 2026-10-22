package com.sergicraftero.escapes.game;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Starts games: private ones (solo or a party) right away, public ones through a per-room queue with a
 * countdown. Tracks which player is in which game.
 */
public final class GameManager {

    private final EscapePlugin plugin;
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private final Set<Game> games = new LinkedHashSet<>();
    /** Players whose room is being prepared. */
    private final Map<UUID, RoomDef> starting = new HashMap<>();
    private final Map<String, PublicQueue> queues = new LinkedHashMap<>();

    public GameManager(EscapePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    // ───────────────────────── lookup ─────────────────────────

    public Game of(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public Game of(UUID u) {
        return byPlayer.get(u);
    }

    public boolean busy(Player p) {
        return byPlayer.containsKey(p.getUniqueId()) || starting.containsKey(p.getUniqueId());
    }

    public Set<Game> games() {
        return games;
    }

    public PublicQueue queueOf(Player p) {
        for (PublicQueue q : queues.values()) {
            if (q.players.contains(p.getUniqueId())) {
                return q;
            }
        }
        return null;
    }

    public int queued(RoomDef room) {
        PublicQueue q = queues.get(room.id);
        return q == null ? 0 : q.players.size();
    }

    public int playing(RoomDef room) {
        int n = 0;
        for (Game g : games) {
            if (g.room() == room) {
                n += g.players().size();
            }
        }
        return n;
    }

    void forget(Player p) {
        byPlayer.remove(p.getUniqueId());
    }

    void ended(Game g) {
        games.remove(g);
        byPlayer.values().removeIf(x -> x == g);
    }

    // ───────────────────────── starting ─────────────────────────

    /** Solo or party game, no strangers. Checks access for everyone involved. */
    public void playPrivate(Player leader, RoomDef room) {
        PartyManager.Party party = plugin.parties().of(leader);
        List<Player> team = new ArrayList<>();
        if (party == null) {
            team.add(leader);
        } else {
            if (!party.isLeader(leader)) {
                Msg.send(leader, "party.only-leader-starts");
                return;
            }
            team.addAll(party.online());
        }
        if (!checkTeam(leader, room, team)) {
            return;
        }
        launch(room, team, true);
    }

    /** Joins the public queue of a room (with the party, if leader). */
    public void joinQueue(Player p, RoomDef room) {
        PartyManager.Party party = plugin.parties().of(p);
        List<Player> group = new ArrayList<>();
        if (party != null) {
            if (!party.isLeader(p)) {
                Msg.send(p, "party.only-leader-starts");
                return;
            }
            group.addAll(party.online());
        } else {
            group.add(p);
        }
        if (!checkTeam(p, room, group)) {
            return;
        }
        for (Player g : group) {
            leaveQueue(g, false);
        }
        PublicQueue q = queues.computeIfAbsent(room.id, k -> new PublicQueue(room));
        if (q.players.size() + group.size() > room.maxPlayers) {
            Msg.send(p, "queue.full");
            return;
        }
        for (Player g : group) {
            q.players.add(g.getUniqueId());
            Msg.send(g, "queue.joined", "room", Msg.parse(room.name), "count", q.players.size(), "max", room.maxPlayers);
        }
        q.announce();
    }

    public void leaveQueue(Player p, boolean message) {
        for (PublicQueue q : queues.values()) {
            if (q.players.remove(p.getUniqueId())) {
                if (message) {
                    Msg.send(p, "queue.left");
                }
                q.announce();
            }
        }
    }

    private boolean checkTeam(Player leader, RoomDef room, List<Player> team) {
        if (!room.enabled) {
            Msg.send(leader, "room.disabled");
            return false;
        }
        if (team.size() > room.maxPlayers) {
            Msg.send(leader, "room.too-many", "max", room.maxPlayers);
            return false;
        }
        for (Player p : team) {
            if (busy(p)) {
                Msg.send(leader, "room.member-busy", "player", p.getName());
                return false;
            }
        }
        String mode = plugin.getConfig().getString("access.requirements-check", "host");
        for (Player p : team) {
            if (!p.equals(leader) && mode.equalsIgnoreCase("host")) {
                continue;
            }
            String denied = plugin.access().denyReason(p, room);
            if (denied != null) {
                Msg.send(leader, denied, "player", p.getName(), "room", Msg.parse(room.name),
                        "level", room.unlockLevel);
                return false;
            }
        }
        return true;
    }

    private void launch(RoomDef room, List<Player> team, boolean privateGame) {
        for (Player p : team) {
            starting.put(p.getUniqueId(), room);
            leaveQueue(p, false);
            Msg.send(p, "game.preparing", "room", Msg.parse(room.name));
        }
        List<UUID> ids = new ArrayList<>();
        team.forEach(p -> ids.add(p.getUniqueId()));
        boolean ok = plugin.instances().acquire(room, instance -> {
            List<Player> online = new ArrayList<>();
            for (UUID u : ids) {
                starting.remove(u);
                Player p = Bukkit.getPlayer(u);
                if (p != null && !byPlayer.containsKey(u)) {
                    online.add(p);
                }
            }
            if (online.isEmpty()) {
                plugin.instances().release(instance);
                return;
            }
            Game game = new Game(plugin, room, instance, online, privateGame);
            games.add(game);
            for (Player p : online) {
                byPlayer.put(p.getUniqueId(), game);
            }
            game.start(online);
        });
        if (!ok) {
            for (Player p : team) {
                starting.remove(p.getUniqueId());
                Msg.send(p, "game.no-free-room");
            }
        }
    }

    // ───────────────────────── public queues ─────────────────────────

    public final class PublicQueue {
        final RoomDef room;
        final Set<UUID> players = new LinkedHashSet<>();
        int countdown = -1;

        PublicQueue(RoomDef room) {
            this.room = room;
        }

        public Set<UUID> players() {
            return players;
        }

        public RoomDef room() {
            return room;
        }

        public int countdown() {
            return countdown;
        }

        void announce() {
            int min = Math.min(room.maxPlayers, Math.max(room.minPlayers, plugin.getConfig().getInt("queue.min-players", 2)));
            if (players.size() >= min && countdown < 0) {
                countdown = plugin.getConfig().getInt("queue.countdown-seconds", 45);
            } else if (players.size() < min) {
                countdown = -1;
            }
            if (players.size() >= room.maxPlayers) {
                countdown = Math.min(countdown, plugin.getConfig().getInt("queue.full-countdown-seconds", 8));
            }
        }

        void second() {
            players.removeIf(u -> Bukkit.getPlayer(u) == null);
            if (players.isEmpty()) {
                countdown = -1;
                return;
            }
            announce();
            for (UUID u : players) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) {
                    continue;
                }
                Component bar = countdown < 0
                        ? Msg.get("queue.waiting-bar", "room", Msg.parse(room.name), "count", players.size(), "max", room.maxPlayers)
                        : Msg.get("queue.countdown-bar", "room", Msg.parse(room.name), "seconds", countdown,
                        "count", players.size(), "max", room.maxPlayers);
                p.sendActionBar(bar);
                if (countdown > 0 && (countdown <= 5 || countdown % 15 == 0)) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
                }
            }
            if (countdown == 0) {
                List<Player> team = new ArrayList<>();
                for (UUID u : players) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && !busy(p)) {
                        team.add(p);
                    }
                }
                players.clear();
                countdown = -1;
                if (!team.isEmpty()) {
                    launch(room, team, false);
                }
                return;
            }
            if (countdown > 0) {
                countdown--;
            }
        }
    }

    private void second() {
        for (PublicQueue q : new ArrayList<>(queues.values())) {
            q.second();
        }
        // players waiting for a room being pasted
        for (Map.Entry<UUID, RoomDef> e : starting.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.sendActionBar(Msg.get("game.preparing-bar", "room", Msg.parse(e.getValue().name)));
            }
        }
    }

    // ───────────────────────── server events ─────────────────────────

    public void quit(Player p) {
        leaveQueue(p, false);
        starting.remove(p.getUniqueId());
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null) {
            g.disconnected(p);
        }
    }

    /** Returns true when the player was put back into their running game. */
    public boolean join(Player p) {
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null && g.state() != Game.State.ENDED && g.members().contains(p.getUniqueId())) {
            g.rejoin(p);
            return true;
        }
        byPlayer.remove(p.getUniqueId());
        return false;
    }

    public void shutdown() {
        for (Game g : new ArrayList<>(games)) {
            for (Player p : g.players()) {
                Msg.send(p, "game.server-restart");
                plugin.lobby().send(p);
            }
            g.abandon();
        }
        games.clear();
        byPlayer.clear();
    }

    public String summary() {
        return games.size() + " partidas, " + byPlayer.size() + " jugando, "
                + queues.values().stream().mapToInt(q -> q.players.size()).sum() + " en cola";
    }

    public static String time(long ms) {
        return Text.time(ms);
    }

    public Set<UUID> allPlaying() {
        return new HashSet<>(byPlayer.keySet());
    }
}
