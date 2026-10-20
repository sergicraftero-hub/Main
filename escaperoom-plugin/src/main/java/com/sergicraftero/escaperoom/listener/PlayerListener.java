package com.sergicraftero.escaperoom.listener;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.Puzzle;
import com.sergicraftero.escaperoom.puzzle.PuzzleState;
import com.sergicraftero.escaperoom.puzzle.types.ChatRiddlePuzzle;
import com.sergicraftero.escaperoom.room.Room;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Player lifecycle inside games: join/quit recovery, death, chat riddles, region borders. */
public final class PlayerListener implements Listener {

    private final EscapeRoomPlugin plugin;
    private final Map<UUID, Long> warnCooldown = new HashMap<>();

    public PlayerListener(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
    }

    // ───────────────────────── connection ─────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.stats().refreshPlayer(player.getUniqueId());
        if (!plugin.inventories().has(player.getUniqueId())) {
            // Logged in inside a room without saved data (e.g. room created around them): move out.
            Room room = plugin.rooms().roomAt(player.getLocation());
            if (room != null && !player.hasPermission("escaperoom.bypass") && room.lobby() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> player.teleport(room.lobby()));
            }
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.resetPlayerTime();
            Location back = plugin.inventories().restore(player);
            if (back != null) {
                player.teleport(back);
            }
            plugin.messages().send(player, "inventory-restored");
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.games().leave(event.getPlayer(), true);
        plugin.stats().forgetPlayer(event.getPlayer().getUniqueId());
        warnCooldown.remove(event.getPlayer().getUniqueId());
    }

    // ───────────────────────── survival ─────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.games().isPlaying(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Game game = plugin.games().gameOf(player);
            event.setCancelled(true);
            player.setFallDistance(0);
            player.teleport(game.room().spawn());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && plugin.games().isPlaying(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.games().isPlaying(player)) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        plugin.messages().send(player, "death-respawn");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Game game = plugin.games().gameOf(event.getPlayer());
        if (game != null && game.isPlaying(event.getPlayer().getUniqueId()) && game.room().spawn() != null) {
            event.setRespawnLocation(game.room().spawn());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.games().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.games().isPlaying(player) || player.hasPermission("escaperoom.bypass")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        List<String> allowed = plugin.getConfig().getStringList("game.allowed-commands");
        if (!allowed.contains(label)) {
            event.setCancelled(true);
            plugin.messages().send(player, "command-blocked");
        }
    }

    // ───────────────────────── chat ─────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.games().gameOf(player.getUniqueId());
        if (game == null || !game.isPlaying(player.getUniqueId()) || !game.isRunning()) {
            return;
        }
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        for (Puzzle puzzle : game.room().puzzles().values()) {
            if (puzzle instanceof ChatRiddlePuzzle riddle && riddle.state() == PuzzleState.ACTIVE && riddle.accepts(message)) {
                event.setCancelled(true);
                int runId = game.runId();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (game.runId() == runId && player.isOnline()) {
                        game.solve(riddle, player);
                    }
                });
                return;
            }
        }
        if (plugin.getConfig().getBoolean("game.team-chat", true)) {
            event.viewers().removeIf(audience -> audience instanceof Player);
            event.viewers().addAll(game.participants());
            event.viewers().add(Bukkit.getConsoleSender());
        }
    }

    // ───────────────────────── borders ─────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !plugin.getConfig().getBoolean("protection.block-outsiders", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.games().isPlaying(player) || player.hasPermission("escaperoom.bypass")) {
            return;
        }
        Room room = plugin.rooms().roomAt(event.getTo());
        if (room != null && plugin.rooms().roomAt(event.getFrom()) != room) {
            event.setCancelled(true);
            warn(player);
        } else if (room != null) {
            // Already inside (joined there, region resized...): send to the lobby.
            Location lobby = room.lobby();
            if (lobby != null) {
                event.setTo(lobby);
            }
            warn(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("escaperoom.bypass")) {
            return;
        }
        Room target = plugin.rooms().roomAt(event.getTo());
        Game game = plugin.games().gameOf(player);
        boolean playing = game != null && game.isPlaying(player.getUniqueId());
        if (target != null && !playing && plugin.getConfig().getBoolean("protection.block-outsiders", true)) {
            event.setCancelled(true);
            warn(player);
        } else if (playing && target != game.room()
                && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                && event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            event.setCancelled(true);
        }
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        Long last = warnCooldown.get(player.getUniqueId());
        if (last == null || now - last > 3000) {
            warnCooldown.put(player.getUniqueId(), now);
            plugin.messages().send(player, "region-blocked");
        }
    }
}
