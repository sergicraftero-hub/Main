package com.sergicraftero.escapes.command;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.build.Builders;
import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.instance.Instance;
import com.sergicraftero.escapes.profile.PlayerProfile;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.template.Template;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /eradmin — server management. The give/grant/revoke sub-commands are what the Tebex store runs
 * after a purchase (they also work for offline players).
 */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("reload", "validate", "build", "editor", "marker", "pos1", "pos2",
            "capture", "start", "stop", "solve", "give", "take", "grant", "revoke", "info", "instances", "lobby");

    private final EscapePlugin plugin;

    public AdminCommand(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Msg.send(sender, "admin.help");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "reload" -> {
                plugin.reloadEverything();
                Msg.send(sender, "admin.reloaded", "rooms", plugin.rooms().all().size());
            }
            case "validate" -> validate(sender, args);
            case "build" -> {
                if (args.length < 2 || !Builders.has(args[1])) {
                    Msg.send(sender, "admin.builders", "list", String.join(", ", Builders.ids()));
                    return true;
                }
                Template t = plugin.templates().generate(args[1]);
                try {
                    plugin.templates().replace(args[1], t);
                } catch (IOException e) {
                    Msg.send(sender, "admin.error", "error", e.getMessage());
                    return true;
                }
                for (RoomDef r : plugin.rooms().all()) {
                    if (r.template.equals(args[1])) {
                        plugin.instances().invalidate(r.id);
                    }
                }
                if (args[1].equals(plugin.lobbyConfig().getString("template", "lobby"))) {
                    plugin.markLobbyRepaste();
                    Msg.send(sender, "admin.lobby-restart");
                }
                Msg.send(sender, "admin.built", "id", args[1]);
            }
            case "editor" -> {
                if (p == null) {
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "admin.editor-usage");
                } else if (args[1].equalsIgnoreCase("save")) {
                    plugin.editor().save(p);
                } else if (args[1].equalsIgnoreCase("exit")) {
                    plugin.editor().close(p);
                } else {
                    RoomDef r = plugin.rooms().get(args[1]);
                    plugin.editor().open(p, r != null ? r.template : args[1]);
                }
            }
            case "marker" -> {
                if (p == null || args.length < 2) {
                    Msg.send(sender, "admin.marker-usage");
                    return true;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "set" -> {
                        if (args.length < 3) {
                            Msg.send(sender, "admin.marker-usage");
                            return true;
                        }
                        String facing = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : null;
                        if ("aqui".equals(facing) || "here".equals(facing)) {
                            facing = cardinal(p.getLocation().getYaw());
                        }
                        plugin.editor().setMarker(p, args[2], facing, args.length > 4 && args[4].equalsIgnoreCase("bloque"));
                    }
                    case "target" -> {
                        if (args.length < 3) {
                            Msg.send(sender, "admin.marker-usage");
                            return true;
                        }
                        plugin.editor().setMarker(p, args[2], args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : null, true);
                    }
                    case "box" -> plugin.editor().setBoxMarker(p, args.length > 2 ? args[2] : "box");
                    case "del" -> plugin.editor().deleteMarker(p, args.length > 2 ? args[2] : "");
                    case "list" -> Msg.send(p, "admin.marker-list", "list", String.join(", ", plugin.editor().markers(p)));
                    default -> Msg.send(sender, "admin.marker-usage");
                }
            }
            case "pos1", "pos2" -> {
                if (p != null) {
                    plugin.editor().select(p, sub.equals("pos1") ? 0 : 1);
                }
            }
            case "capture" -> {
                if (p != null && args.length > 1) {
                    plugin.editor().captureSelection(p, args[1].toLowerCase(Locale.ROOT));
                }
            }
            case "start" -> {
                if (args.length < 2) {
                    Msg.send(sender, "admin.start-usage");
                    return true;
                }
                RoomDef r = plugin.rooms().get(args[1]);
                if (r == null) {
                    Msg.send(sender, "room.unknown", "room", args[1]);
                    return true;
                }
                Player who = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : p;
                if (who != null) {
                    plugin.games().playPrivate(who, r);
                }
            }
            case "stop" -> {
                Player who = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : p;
                Game g = who == null ? null : plugin.games().of(who);
                if (g != null) {
                    g.lose("game.lose-reason-admin");
                    Msg.send(sender, "admin.stopped");
                }
            }
            case "solve" -> {
                Game g = p == null ? null : plugin.games().of(p);
                if (g == null || args.length < 2) {
                    Msg.send(sender, "admin.solve-usage");
                    return true;
                }
                if (args[1].equals("*")) {
                    for (var pz : new ArrayList<>(g.objectives())) {
                        g.solve(pz.id(), p);
                    }
                } else {
                    g.solve(args[1], p);
                }
            }
            case "give", "take" -> currency(sender, args, sub.equals("give") ? 1 : -1);
            case "grant", "revoke" -> grant(sender, args, sub.equals("grant"));
            case "info" -> info(sender, args);
            case "instances" -> {
                Msg.send(sender, "admin.instances-header", "games", plugin.games().summary(), "queue", plugin.instances().queued());
                for (Instance in : plugin.instances().all()) {
                    sender.sendMessage(Msg.parse("<gray>- <white>" + in.room().id + " <gray>#" + in.column() + " <yellow>" + in.state()
                            + (in.state() == Instance.State.PASTING || in.state() == Instance.State.RESETTING
                            ? " <gray>" + (int) (in.pasteProgress() * 100) + "%" : "")));
                }
            }
            case "lobby" -> {
                if (p != null) {
                    plugin.lobby().send(p);
                }
            }
            default -> Msg.send(sender, "admin.help");
        }
        return true;
    }

    private static String cardinal(float yaw) {
        float y = (yaw % 360 + 360) % 360;
        if (y >= 45 && y < 135) {
            return "west";
        }
        if (y >= 135 && y < 225) {
            return "north";
        }
        if (y >= 225 && y < 315) {
            return "east";
        }
        return "south";
    }

    private void validate(CommandSender sender, String[] args) {
        List<RoomDef> rooms = new ArrayList<>();
        if (args.length > 1) {
            RoomDef r = plugin.rooms().get(args[1]);
            if (r != null) {
                rooms.add(r);
            }
        } else {
            rooms.addAll(plugin.rooms().all());
        }
        int problems = 0;
        for (RoomDef r : rooms) {
            List<String> list = plugin.rooms().validate(r, plugin.templates().peek(r.template));
            if (list.isEmpty()) {
                sender.sendMessage(Msg.parse("<green>✔ " + r.id));
            } else {
                problems += list.size();
                sender.sendMessage(Msg.parse("<red>✘ " + r.id + " (" + list.size() + ")"));
                for (String s : list) {
                    sender.sendMessage(Msg.parse("<gray>  - " + Msg.escape(s)));
                }
            }
        }
        Msg.send(sender, "admin.validate-done", "problems", problems);
    }

    /** /eradmin give <player> <keys|gems|xp> <amount> */
    private void currency(CommandSender sender, String[] args, int sign) {
        if (args.length < 4) {
            Msg.send(sender, "admin.give-usage");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]) * sign;
        } catch (NumberFormatException e) {
            Msg.send(sender, "admin.give-usage");
            return;
        }
        String what = args[2].toLowerCase(Locale.ROOT);
        long finalAmount = amount;
        withProfile(sender, args[1], prof -> {
            switch (what) {
                case "keys", "llaves" -> prof.keys = Math.max(0, prof.keys + finalAmount);
                case "gems", "gemas" -> prof.gems = Math.max(0, prof.gems + finalAmount);
                case "xp" -> prof.xp = Math.max(0, prof.xp + finalAmount);
                default -> {
                }
            }
        }, () -> {
            Msg.send(sender, "admin.given", "player", args[1], "amount", finalAmount, "what", what);
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null && finalAmount > 0) {
                Msg.send(online, "shop.received", "amount", finalAmount, "what", Msg.raw("currency." + what));
                plugin.lobby().refresh(online);
            }
        });
    }

    /** /eradmin grant <player> <room|cosmetic> <id> */
    private void grant(CommandSender sender, String[] args, boolean grant) {
        if (args.length < 4) {
            Msg.send(sender, "admin.grant-usage");
            return;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        String id = args[3];
        withProfile(sender, args[1], prof -> {
            if (type.equals("room") || type.equals("sala")) {
                if (grant) {
                    prof.ownedRooms.add(id.toLowerCase(Locale.ROOT));
                } else {
                    prof.ownedRooms.remove(id.toLowerCase(Locale.ROOT));
                }
            } else if (type.equals("cosmetic") || type.equals("cosmetico")) {
                // id format: type:id  (e.g. trails:flames)
                if (grant) {
                    prof.ownedCosmetics.add(id);
                } else {
                    prof.ownedCosmetics.remove(id);
                }
            } else if (type.equals("rooms-all")) {
                for (RoomDef r : plugin.rooms().all()) {
                    if (r.isPaid()) {
                        if (grant) {
                            prof.ownedRooms.add(r.id);
                        } else {
                            prof.ownedRooms.remove(r.id);
                        }
                    }
                }
            }
        }, () -> {
            Msg.send(sender, grant ? "admin.granted" : "admin.revoked", "player", args[1], "what", type + " " + id);
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null && grant) {
                Msg.send(online, "shop.unlocked", "what", id);
            }
        });
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "admin.info-usage");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online == null) {
            Msg.send(sender, "admin.offline");
            return;
        }
        PlayerProfile prof = plugin.profiles().get(online);
        sender.sendMessage(Msg.parse("<gold>" + online.getName() + " <gray>nivel <white>" + plugin.progress().level(prof.xp)
                + " <gray>xp <white>" + prof.xp + " <gray>llaves <white>" + prof.keys + " <gray>gemas <white>" + prof.gems
                + " <gray>salas <white>" + prof.roomsCompleted() + " <gray>compradas <white>" + prof.ownedRooms));
    }

    @SuppressWarnings("deprecation")
    private void withProfile(CommandSender sender, String name, Consumer<PlayerProfile> change, Runnable done) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            plugin.profiles().edit(online, change, done);
            return;
        }
        plugin.storage().runAsync(() -> {
            UUID uuid = plugin.storage().findByName(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                OfflinePlayer target = uuid != null ? Bukkit.getOfflinePlayer(uuid) : Bukkit.getOfflinePlayer(name);
                plugin.profiles().edit(target, change, done);
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "build" -> out.addAll(Builders.ids());
                case "editor" -> {
                    out.add("save");
                    out.add("exit");
                    out.addAll(plugin.rooms().ids());
                    out.add("lobby");
                }
                case "validate", "start" -> out.addAll(plugin.rooms().ids());
                case "marker" -> out.addAll(List.of("set", "target", "box", "del", "list"));
                case "give", "take", "grant", "revoke", "info", "stop" -> Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
                case "solve" -> {
                    if (sender instanceof Player p && plugin.games().of(p) != null) {
                        out.add("*");
                        out.addAll(plugin.games().of(p).puzzles().keySet());
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3) {
            switch (sub) {
                case "give", "take" -> out.addAll(List.of("keys", "gems", "xp"));
                case "grant", "revoke" -> out.addAll(List.of("room", "cosmetic", "rooms-all"));
                case "marker" -> {
                    if (args[1].equalsIgnoreCase("del") && sender instanceof Player p) {
                        out.addAll(plugin.editor().markers(p));
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 4) {
            if (sub.equals("grant") || sub.equals("revoke")) {
                if (args[2].equalsIgnoreCase("room")) {
                    out.addAll(plugin.rooms().ids());
                } else if (args[2].equalsIgnoreCase("cosmetic")) {
                    for (String type : com.sergicraftero.escapes.cosmetic.CosmeticService.TYPES) {
                        for (String id : plugin.cosmetics().ids(type)) {
                            out.add(type + ":" + id);
                        }
                    }
                }
            } else if (sub.equals("marker")) {
                out.addAll(List.of("north", "south", "east", "west", "aqui"));
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
