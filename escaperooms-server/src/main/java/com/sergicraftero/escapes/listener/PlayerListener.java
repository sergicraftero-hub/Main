package com.sergicraftero.escapes.listener;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.List;
import java.util.Locale;

/** Join/quit, chat routing and command limits during games. */
public final class PlayerListener implements Listener {

    private final EscapePlugin plugin;

    public PlayerListener(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        plugin.profiles().preload(e.getUniqueId(), e.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.joinMessage(null);
        plugin.profiles().joined(p);
        plugin.pack().send(p);
        if (!plugin.games().join(p)) {
            plugin.lobby().send(p);
        }
        boolean first = plugin.profiles().get(p).lastJoin - plugin.profiles().get(p).firstJoin < 5000;
        if (plugin.getConfig().getBoolean("chat.join-messages", true)) {
            Bukkit.getServer().sendMessage(Msg.get(first ? "lobby.first-join" : "lobby.join", "player", p.getName()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Msg.send(p, "lobby.welcome", "player", p.getName(), "server", plugin.getConfig().getString("server-name", "EscapeRooms"));
            if (plugin.progress().canClaimDaily(p)) {
                Msg.send(p, "daily.reminder");
            }
            plugin.progress().ensureQuests(p);
            plugin.progress().checkAchievements(p);
            for (Player other : Bukkit.getOnlinePlayers()) {
                plugin.lobby().updateVisibility(other);
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.quitMessage(null);
        plugin.games().quit(p);
        plugin.parties().quit(p);
        plugin.lobby().quit(p);
        plugin.pack().quit(p);
        plugin.cosmetics().quit(p);
        plugin.profiles().quit(p);
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        plugin.pack().status(e.getPlayer(), e.getStatus());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(e.message());
        e.setCancelled(true);
        // everything below touches game state: hop to the main thread
        Bukkit.getScheduler().runTask(plugin, () -> route(p, message));
    }

    private void route(Player p, String message) {
        if (!p.isOnline()) {
            return;
        }
        Game g = plugin.games().of(p);
        Component body = Component.text(message);
        if (g != null) {
            if (message.startsWith("!") && plugin.getConfig().getBoolean("chat.global-prefix", true) && message.length() > 1) {
                Component line = Msg.get("chat.global-from-game", "player", p.getName(), "room", Msg.parse(g.room().name),
                        "message", Component.text(message.substring(1).trim()));
                Bukkit.getServer().sendMessage(line);
                return;
            }
            boolean hidden = g.chat(p, message);
            if (hidden) {
                Msg.send(p, "chat.answer-sent", "message", body);
                return;
            }
            Component line = Msg.get("chat.team", "player", p.getName(), "message", body);
            g.players().forEach(pl -> pl.sendMessage(line));
            return;
        }
        int level = plugin.progress().level(p);
        Component line = Msg.get("chat.lobby", "player", p.getName(), "level", level,
                "rank", Msg.parse(plugin.progress().rankPrefix(p)), "title", Msg.parse(plugin.cosmetics().title(p)),
                "vip", Msg.parse(vipPrefix(p)), "message", body);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (plugin.games().of(other) == null) {
                other.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private String vipPrefix(Player p) {
        var ranks = plugin.getConfig().getConfigurationSection("chat.vip-prefixes");
        if (ranks == null) {
            return "";
        }
        for (String perm : ranks.getKeys(false)) {
            if (p.hasPermission("escaperooms.rank." + perm)) {
                return ranks.getString(perm, "");
            }
        }
        return "";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.games().of(p) == null || p.hasPermission("escaperooms.bypass.commands")) {
            return;
        }
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        List<String> allowed = plugin.getConfig().getStringList("game.allowed-commands");
        if (!allowed.contains(label)) {
            e.setCancelled(true);
            Msg.send(p, "game.command-blocked");
        }
    }
}
