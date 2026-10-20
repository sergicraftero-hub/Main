package com.sergicraftero.escaperoom.command;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.build.BuildService;
import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.Puzzle;
import com.sergicraftero.escaperoom.room.Room;
import com.sergicraftero.escaperoom.room.RoomManager;
import com.sergicraftero.escaperoom.storage.StatsStorage;
import com.sergicraftero.escaperoom.util.Cuboid;
import com.sergicraftero.escaperoom.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** /escape — every player and admin sub-command, with tab completion. */
public final class EscapeCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "hint", "list", "info", "top", "stats", "help");
    private static final List<String> ADMIN_SUBS = List.of("create", "edit", "delete", "pos1", "pos2", "wand", "start",
            "stop", "reset", "snapshot", "build", "demolish", "puzzles", "setsign", "reload");
    private static final List<String> EDIT_PROPS = List.of("spawn", "exit", "lobby", "leaderboard", "region",
            "time-limit", "min-players", "max-players", "countdown", "hints", "max-hints", "hint-penalty", "night",
            "enabled", "display-name");

    private final EscapeRoomPlugin plugin;

    public EscapeCommand(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        msg().send(sender, "no-permission");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        msg().send(sender, "players-only");
        return null;
    }

    private Room requireRoom(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            msg().send(sender, "usage", Messages.ph("usage", "/escape " + args[0] + " <sala>"));
            return null;
        }
        Room room = plugin.rooms().get(args[index]);
        if (room == null) {
            msg().send(sender, "room-not-found", Messages.ph("room", args[index]));
        }
        return room;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "hint" -> hint(sender);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "top" -> top(sender, args);
            case "stats" -> stats(sender, args);
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "delete" -> delete(sender, args);
            case "pos1", "pos2" -> pos(sender, sub);
            case "wand" -> wand(sender);
            case "start", "stop", "reset" -> control(sender, sub, args);
            case "snapshot" -> snapshot(sender, args);
            case "build" -> build(sender, args);
            case "demolish" -> demolish(sender, args);
            case "puzzles" -> puzzles(sender, args);
            case "setsign" -> setSign(sender, args);
            case "reload" -> reload(sender);
            default -> msg().send(sender, "unknown-command");
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg().getList("help.player").forEach(sender::sendMessage);
        if (sender.hasPermission("escaperoom.admin.edit") || sender.hasPermission("escaperoom.admin.build")) {
            msg().getList("help.admin").forEach(sender::sendMessage);
        }
    }

    // ───────────────────────── player ─────────────────────────

    private void join(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !check(sender, "escaperoom.play")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        if (plugin.getConfig().getBoolean("per-room-permissions", false)
                && !player.hasPermission("escaperoom.join." + room.id())) {
            msg().send(sender, "no-permission");
            return;
        }
        plugin.games().join(player, room);
    }

    private void leave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (plugin.games().gameOf(player) == null) {
            msg().send(sender, "not-in-game");
            return;
        }
        plugin.games().leave(player, false);
    }

    private void hint(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !check(sender, "escaperoom.hint")) {
            return;
        }
        Game game = plugin.games().gameOf(player);
        if (game == null || !game.isPlaying(player.getUniqueId())) {
            msg().send(sender, "hint-not-playing");
            return;
        }
        game.useHint(player);
    }

    private void list(CommandSender sender) {
        if (!check(sender, "escaperoom.play")) {
            return;
        }
        if (plugin.rooms().all().isEmpty()) {
            msg().send(sender, "list-empty");
            return;
        }
        msg().send(sender, "list-header", Messages.ph("count", plugin.rooms().all().size()));
        for (Room room : plugin.rooms().all()) {
            Game game = plugin.games().game(room);
            sender.sendMessage(msg().get("list-line", Messages.ph("id", room.id()), Messages.phm("room", room.displayName()),
                    Messages.ph("state", msg().raw("state." + game.state().name().toLowerCase(Locale.ROOT))),
                    Messages.ph("players", game.isRunning() ? game.playerCount() : game.queueSize()),
                    Messages.ph("max", room.maxPlayers())));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.play")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        Game game = plugin.games().game(room);
        List<StatsStorage.TopEntry> top = plugin.stats().top(room.id());
        msg().getList("info", Messages.ph("id", room.id()), Messages.phm("room", room.displayName()),
                Messages.ph("state", msg().raw("state." + game.state().name().toLowerCase(Locale.ROOT))),
                Messages.ph("time", Text.formatTime(room.timeLimit() * 1000L)),
                Messages.ph("min", room.minPlayers()), Messages.ph("max", room.maxPlayers()),
                Messages.ph("puzzles", room.puzzles().size()),
                Messages.ph("hints", room.hintsAllowed() ? msg().raw("yes") : msg().raw("no")),
                Messages.ph("penalty", room.hintPenalty()),
                Messages.ph("best", top.isEmpty() ? msg().raw("placeholder-none") : Text.formatTimePrecise(top.get(0).timeMillis())),
                Messages.ph("snapshot", plugin.snapshots().has(room.id()) ? msg().raw("yes") : msg().raw("no")))
                .forEach(sender::sendMessage);
    }

    private void top(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.play")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        plugin.stats().refreshTop(room.id(), () -> {
            List<StatsStorage.TopEntry> top = plugin.stats().top(room.id());
            sender.sendMessage(msg().get("leaderboard-header", Messages.phm("room", room.displayName())));
            if (top.isEmpty()) {
                sender.sendMessage(msg().get("leaderboard-empty"));
            }
            int size = Math.min(top.size(), plugin.getConfig().getInt("leaderboard.size", 10));
            for (int i = 0; i < size; i++) {
                StatsStorage.TopEntry e = top.get(i);
                sender.sendMessage(msg().get("leaderboard-line", Messages.ph("rank", i + 1),
                        Messages.ph("players", e.players()), Messages.ph("time", Text.formatTimePrecise(e.timeMillis())),
                        Messages.ph("hints", e.hints())));
            }
        });
    }

    private void stats(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.play")) {
            return;
        }
        String name = args.length > 1 ? args[1] : sender.getName();
        plugin.stats().lookupByName(name, stats -> {
            String bests = stats.personalBest().entrySet().stream()
                    .map(e -> e.getKey() + " " + Text.formatTimePrecise(e.getValue()))
                    .collect(Collectors.joining(", "));
            msg().getList("stats", Messages.ph("player", name), Messages.ph("plays", stats.plays()),
                    Messages.ph("wins", stats.wins()), Messages.ph("losses", stats.plays() - stats.wins()),
                    Messages.ph("bests", bests.isEmpty() ? msg().raw("placeholder-none") : bests))
                    .forEach(sender::sendMessage);
        });
    }

    // ───────────────────────── admin: rooms ─────────────────────────

    private void create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !check(sender, "escaperoom.admin.create")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "usage", Messages.ph("usage", "/escape create <id>"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!RoomManager.ID_PATTERN.matcher(id).matches()) {
            msg().send(sender, "invalid-id");
            return;
        }
        if (plugin.rooms().get(id) != null) {
            msg().send(sender, "room-exists", Messages.ph("room", id));
            return;
        }
        Cuboid region = plugin.selections().get(player);
        if (region == null) {
            msg().send(sender, "no-selection");
            return;
        }
        Room room = Room.create(id, plugin.rooms().fileFor(id), player.getWorld(), region, player.getLocation());
        try {
            room.save();
        } catch (IOException e) {
            msg().send(sender, "save-failed", Messages.ph("error", e.getMessage()));
            return;
        }
        plugin.rooms().register(room);
        msg().send(sender, "room-created", Messages.ph("room", id), Messages.ph("volume", region.volume()));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.admin.delete")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            msg().send(sender, "delete-confirm", Messages.ph("room", room.id()));
            return;
        }
        plugin.games().remove(room.id());
        plugin.leaderboards().remove(room.id());
        plugin.snapshots().delete(room.id());
        plugin.rooms().delete(room.id());
        msg().send(sender, "room-deleted", Messages.ph("room", room.id()));
    }

    private void edit(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.admin.edit")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        if (args.length < 3) {
            msg().send(sender, "usage", Messages.ph("usage", "/escape edit <sala> <" + String.join("|", EDIT_PROPS) + "> [valor]"));
            return;
        }
        String prop = args[2].toLowerCase(Locale.ROOT);
        String value = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        Player player = sender instanceof Player p ? p : null;
        Game game = plugin.games().game(room);
        if (game.isRunning() && !prop.equals("enabled")) {
            msg().send(sender, "edit-running");
            return;
        }
        try {
            switch (prop) {
                case "spawn", "exit", "lobby", "leaderboard" -> {
                    if (player == null) {
                        msg().send(sender, "players-only");
                        return;
                    }
                    Location loc = player.getLocation();
                    switch (prop) {
                        case "spawn" -> room.spawn(loc);
                        case "exit" -> room.exit(loc);
                        case "lobby" -> room.lobby(loc);
                        default -> room.leaderboard("none".equalsIgnoreCase(value) ? null : loc.clone().add(0, 2, 0));
                    }
                }
                case "region" -> {
                    if (player == null) {
                        msg().send(sender, "players-only");
                        return;
                    }
                    Cuboid region = plugin.selections().get(player);
                    if (region == null) {
                        msg().send(sender, "no-selection");
                        return;
                    }
                    room.region(region);
                    room.world(player.getWorld());
                }
                case "time-limit" -> room.timeLimit(Integer.parseInt(value));
                case "min-players" -> room.minPlayers(Integer.parseInt(value));
                case "max-players" -> room.maxPlayers(Integer.parseInt(value));
                case "countdown" -> room.countdown(Integer.parseInt(value));
                case "hints" -> room.hintsAllowed(Boolean.parseBoolean(value));
                case "max-hints" -> room.maxHints(Integer.parseInt(value));
                case "hint-penalty" -> room.hintPenalty(Integer.parseInt(value));
                case "night" -> room.night(Boolean.parseBoolean(value));
                case "display-name" -> room.displayName(value == null ? room.id() : value);
                case "enabled" -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    room.enabled(enabled);
                    game.setEnabled(enabled);
                    if (enabled && !plugin.snapshots().has(room.id())) {
                        msg().send(sender, "snapshot-started", Messages.ph("room", room.id()));
                        plugin.snapshots().captureRoom(room, ok -> msg().send(sender, ok ? "snapshot-done" : "snapshot-failed",
                                Messages.ph("room", room.id())));
                    }
                }
                default -> {
                    msg().send(sender, "edit-unknown", Messages.ph("prop", prop));
                    return;
                }
            }
        } catch (NumberFormatException | NullPointerException e) {
            msg().send(sender, "edit-invalid-value", Messages.ph("prop", prop));
            return;
        }
        try {
            room.save();
        } catch (IOException e) {
            msg().send(sender, "save-failed", Messages.ph("error", e.getMessage()));
            return;
        }
        if (prop.equals("leaderboard")) {
            plugin.leaderboards().refresh(room.id());
        }
        msg().send(sender, "edit-done", Messages.ph("room", room.id()), Messages.ph("prop", prop),
                Messages.ph("value", value == null ? "✔" : value));
    }

    private void pos(CommandSender sender, String which) {
        Player player = requirePlayer(sender);
        if (player == null || !check(sender, "escaperoom.admin.create")) {
            return;
        }
        Block target = player.getTargetBlockExact(6);
        Location loc = target != null ? target.getLocation() : player.getLocation();
        if (which.equals("pos1")) {
            plugin.selections().pos1(player, loc);
        } else {
            plugin.selections().pos2(player, loc);
        }
        msg().send(sender, "wand-" + which, Messages.ph("pos", loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()));
    }

    private void wand(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !check(sender, "escaperoom.admin.create")) {
            return;
        }
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(msg().get("wand-name"));
        meta.lore(msg().getList("wand-lore"));
        meta.getPersistentDataContainer().set(plugin.keys().wand, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        msg().send(sender, "wand-given");
    }

    private void control(CommandSender sender, String action, String[] args) {
        if (!check(sender, "escaperoom.admin.control")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        Game game = plugin.games().game(room);
        switch (action) {
            case "start" -> msg().send(sender, game.forceStart() ? "force-started" : "force-start-failed",
                    Messages.ph("room", room.id()));
            case "stop" -> {
                game.stop();
                msg().send(sender, "force-stopped", Messages.ph("room", room.id()));
            }
            default -> {
                if (game.isRunning()) {
                    msg().send(sender, "edit-running");
                    return;
                }
                game.resetRoom();
                msg().send(sender, "room-reset", Messages.ph("room", room.id()));
            }
        }
    }

    private void snapshot(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.admin.edit")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        if (plugin.games().game(room).isRunning()) {
            msg().send(sender, "edit-running");
            return;
        }
        msg().send(sender, "snapshot-started", Messages.ph("room", room.id()));
        plugin.snapshots().captureRoom(room, ok -> msg().send(sender, ok ? "snapshot-done" : "snapshot-failed",
                Messages.ph("room", room.id())));
    }

    private void puzzles(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.admin.edit")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        msg().send(sender, "puzzles-header", Messages.phm("room", room.displayName()),
                Messages.ph("count", room.puzzles().size()));
        for (Puzzle puzzle : room.puzzles().values()) {
            sender.sendMessage(msg().get("puzzles-line", Messages.ph("id", puzzle.id()), Messages.ph("type", puzzle.type()),
                    Messages.phm("name", puzzle.displayName()), Messages.ph("state", puzzle.state().name()),
                    Messages.ph("requires", puzzle.requires().isEmpty() ? "-" : String.join(", ", puzzle.requires())),
                    Messages.ph("hints", puzzle.hints().size())));
        }
    }

    private void setSign(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !check(sender, "escaperoom.admin.edit")) {
            return;
        }
        Room room = requireRoom(sender, args, 1);
        if (room == null) {
            return;
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null || !(block.getState() instanceof Sign sign)) {
            msg().send(sender, "setsign-not-sign");
            return;
        }
        sign.getPersistentDataContainer().set(plugin.keys().joinSign, PersistentDataType.STRING, room.id());
        List<Component> lines = msg().getList("join-sign", Messages.phm("room", room.displayName()), Messages.ph("id", room.id()));
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            sign.getSide(Side.FRONT).line(i, lines.get(i));
        }
        sign.setWaxed(true);
        sign.update();
        msg().send(sender, "setsign-done", Messages.ph("room", room.id()));
    }

    private void reload(CommandSender sender) {
        if (!check(sender, "escaperoom.admin.reload")) {
            return;
        }
        for (Game game : new ArrayList<>(plugin.games().games())) {
            if (game.isRunning()) {
                msg().send(sender, "reload-running");
                return;
            }
        }
        plugin.reloadConfig();
        msg().load();
        for (Room room : new ArrayList<>(plugin.rooms().all())) {
            plugin.games().remove(room.id());
        }
        plugin.rooms().loadAll();
        plugin.leaderboards().removeAll();
        plugin.leaderboards().refreshAll();
        msg().send(sender, "reloaded", Messages.ph("count", plugin.rooms().all().size()));
    }

    // ───────────────────────── admin: generated maps ─────────────────────────

    private void build(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.admin.build")) {
            return;
        }
        if (args.length < 2 || !BuildService.TEMPLATES.contains(args[1].toLowerCase(Locale.ROOT))) {
            msg().send(sender, "usage", Messages.ph("usage", "/escape build <" + String.join("|", BuildService.TEMPLATES) + "> [x y z]"));
            return;
        }
        World world;
        int x;
        int y;
        int z;
        if (args.length >= 5) {
            try {
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
                z = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                msg().send(sender, "usage", Messages.ph("usage", "/escape build " + args[1] + " <x> <y> <z>"));
                return;
            }
            world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
        } else if (sender instanceof Player p) {
            world = p.getWorld();
            x = p.getLocation().getBlockX();
            y = p.getLocation().getBlockY();
            z = p.getLocation().getBlockZ();
        } else {
            msg().send(sender, "usage", Messages.ph("usage", "/escape build " + args[1] + " <x> <y> <z>"));
            return;
        }
        plugin.builds().build(sender, args[1].toLowerCase(Locale.ROOT), world, x, y, z);
    }

    private void demolish(CommandSender sender, String[] args) {
        if (!check(sender, "escaperoom.admin.build")) {
            return;
        }
        if (args.length < 2 || !BuildService.TEMPLATES.contains(args[1].toLowerCase(Locale.ROOT))) {
            msg().send(sender, "usage", Messages.ph("usage", "/escape demolish <" + String.join("|", BuildService.TEMPLATES) + ">"));
            return;
        }
        plugin.builds().demolish(sender, args[1].toLowerCase(Locale.ROOT));
    }

    // ───────────────────────── tab completion ─────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_SUBS);
            if (sender.hasPermission("escaperoom.admin.edit") || sender.hasPermission("escaperoom.admin.build")
                    || sender.hasPermission("escaperoom.admin.control") || sender.hasPermission("escaperoom.admin.create")) {
                options.addAll(ADMIN_SUBS);
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join", "info", "top", "edit", "delete", "start", "stop", "reset", "snapshot", "puzzles", "setsign" ->
                        plugin.rooms().all().forEach(r -> options.add(r.id()));
                case "build", "demolish" -> options.addAll(BuildService.TEMPLATES);
                case "stats" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            options.addAll(EDIT_PROPS);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("delete")) {
            options.add("confirm");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("edit")) {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "hints", "night", "enabled" -> options.addAll(List.of("true", "false"));
                case "time-limit" -> options.addAll(List.of("600", "900", "1200", "1800", "2700", "3600"));
                case "leaderboard" -> options.add("none");
                default -> {
                }
            }
        } else if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("build") && sender instanceof Player p) {
            Block target = p.getTargetBlockExact(10);
            Location loc = target != null ? target.getLocation().add(0, 1, 0) : p.getLocation();
            int[] coords = {loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()};
            options.add(String.valueOf(coords[args.length - 3]));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
