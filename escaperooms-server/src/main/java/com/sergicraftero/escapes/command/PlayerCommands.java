package com.sergicraftero.escapes.command;

import com.sergicraftero.escapes.EscapePlugin;
import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.game.PartyManager;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.RoomDef;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Player commands: /menu, /salas, /jugar, /perfil, /tienda, /diario, /misiones, /cosmeticos, /top, /pista, /salir, /lobby, /grupo. */
public final class PlayerCommands implements TabExecutor {

    private final EscapePlugin plugin;

    public PlayerCommands(EscapePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "menu" -> plugin.ui().main(p);
            case "salas" -> plugin.ui().wings(p);
            case "jugar" -> {
                if (args.length == 0) {
                    plugin.ui().wings(p);
                    return true;
                }
                RoomDef r = plugin.rooms().get(args[0]);
                if (r == null) {
                    Msg.send(p, "room.unknown", "room", args[0]);
                } else {
                    plugin.ui().room(p, r);
                }
            }
            case "perfil" -> plugin.ui().profile(p);
            case "tienda" -> plugin.ui().shop(p);
            case "diario" -> plugin.ui().daily(p);
            case "misiones" -> plugin.ui().quests(p);
            case "cosmeticos" -> plugin.ui().cosmetics(p);
            case "top" -> {
                RoomDef r = args.length > 0 ? plugin.rooms().get(args[0]) : null;
                if (r == null) {
                    plugin.ui().leaderboards(p);
                } else {
                    plugin.ui().roomTop(p, r);
                }
            }
            case "pista" -> hint(p, args);
            case "salir" -> leave(p);
            case "lobby" -> {
                if (plugin.games().of(p) != null) {
                    leave(p);
                } else {
                    plugin.lobby().send(p);
                }
            }
            case "grupo" -> party(p, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void hint(Player p, String[] args) {
        Game g = plugin.games().of(p);
        if (g == null) {
            Msg.send(p, "game.not-in-game");
            return;
        }
        if (args.length > 0) {
            g.useHint(p, args[0]);
            return;
        }
        List<Puzzle> obj = g.objectives();
        if (obj.size() == 1) {
            g.useHint(p, obj.get(0).id());
        } else {
            plugin.ui().notebook(p);
        }
    }

    private void leave(Player p) {
        Game g = plugin.games().of(p);
        if (g != null) {
            g.leave(p, true);
            return;
        }
        if (plugin.games().queueOf(p) != null) {
            plugin.games().leaveQueue(p, true);
            return;
        }
        Msg.send(p, "game.not-in-game");
    }

    private void party(Player p, String[] args) {
        if (args.length == 0) {
            plugin.ui().party(p);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invitar", "invite" -> {
                if (args.length < 2) {
                    Msg.send(p, "party.usage");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Msg.send(p, "party.offline", "player", args[1]);
                    return;
                }
                plugin.parties().invite(p, target);
            }
            case "aceptar", "accept" -> plugin.parties().accept(p);
            case "salir", "leave" -> plugin.parties().leave(p, true);
            case "expulsar", "kick" -> {
                if (args.length < 2) {
                    Msg.send(p, "party.usage");
                    return;
                }
                plugin.parties().kick(p, args[1]);
            }
            case "lista", "list" -> {
                PartyManager.Party party = plugin.parties().of(p);
                if (party == null) {
                    Msg.send(p, "party.none");
                    return;
                }
                List<String> names = new ArrayList<>();
                for (UUID u : party.members()) {
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    names.add((u.equals(party.leader()) ? "★" : "") + n);
                }
                Msg.send(p, "party.list", "members", String.join(", ", names), "size", names.size(), "max", plugin.parties().max());
            }
            default -> Msg.send(p, "party.usage");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if ((name.equals("jugar") || name.equals("top")) && args.length == 1) {
            for (RoomDef r : plugin.rooms().all()) {
                if (r.id.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(r.id);
                }
            }
        } else if (name.equals("grupo")) {
            if (args.length == 1) {
                for (String s : List.of("invitar", "aceptar", "salir", "expulsar", "lista")) {
                    if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        out.add(s);
                    }
                }
            } else if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(p.getName());
                    }
                }
            }
        } else if (name.equals("pista") && args.length == 1 && sender instanceof Player p) {
            Game g = plugin.games().of(p);
            if (g != null) {
                g.objectives().forEach(pz -> out.add(pz.id()));
            }
        }
        return out;
    }
}
