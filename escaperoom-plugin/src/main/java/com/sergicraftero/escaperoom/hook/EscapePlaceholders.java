package com.sergicraftero.escaperoom.hook;

import com.sergicraftero.escaperoom.EscapeRoomPlugin;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.room.Room;
import com.sergicraftero.escaperoom.storage.StatsStorage;
import com.sergicraftero.escaperoom.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PlaceholderAPI expansion. Available placeholders:
 * <ul>
 *   <li>%escaperoom_wins% · %escaperoom_plays% · %escaperoom_losses%</li>
 *   <li>%escaperoom_best_&lt;room&gt;% · %escaperoom_best_&lt;room&gt;_players%</li>
 *   <li>%escaperoom_pb_&lt;room&gt;% (personal best)</li>
 *   <li>%escaperoom_top_&lt;room&gt;_&lt;n&gt;% · _time · _players</li>
 *   <li>%escaperoom_room% · %escaperoom_time_left% · %escaperoom_hints_used%</li>
 *   <li>%escaperoom_status_&lt;room&gt;% · %escaperoom_queue_&lt;room&gt;% · %escaperoom_playing_&lt;room&gt;%</li>
 * </ul>
 */
public final class EscapePlaceholders extends PlaceholderExpansion {

    private final EscapeRoomPlugin plugin;

    public EscapePlaceholders(EscapeRoomPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "escaperoom";
    }

    @Override
    public @NotNull String getAuthor() {
        return "sergicraftero";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String none = plugin.messages().raw("placeholder-none");
        StatsStorage.PlayerStats stats = player == null ? StatsStorage.PlayerStats.EMPTY
                : plugin.stats().player(player.getUniqueId());
        switch (params) {
            case "wins":
                return String.valueOf(stats.wins());
            case "plays":
                return String.valueOf(stats.plays());
            case "losses":
                return String.valueOf(stats.plays() - stats.wins());
            case "room":
            case "time_left":
            case "hints_used": {
                Game game = player == null ? null : plugin.games().gameOf(player.getUniqueId());
                if (game == null) {
                    return none;
                }
                return switch (params) {
                    case "room" -> game.room().id();
                    case "time_left" -> game.isRunning() ? Text.formatTime(game.remainingMillis()) : none;
                    default -> String.valueOf(game.hintsUsed());
                };
            }
            default:
                break;
        }
        if (params.startsWith("best_")) {
            String rest = params.substring(5);
            boolean names = rest.endsWith("_players");
            String room = names ? rest.substring(0, rest.length() - 8) : rest;
            List<StatsStorage.TopEntry> top = plugin.stats().top(room);
            if (top.isEmpty()) {
                return none;
            }
            return names ? top.get(0).players() : Text.formatTimePrecise(top.get(0).timeMillis());
        }
        if (params.startsWith("pb_")) {
            Long best = stats.personalBest().get(params.substring(3));
            return best == null ? none : Text.formatTimePrecise(best);
        }
        if (params.startsWith("top_")) {
            String[] parts = params.substring(4).split("_");
            if (parts.length < 2) {
                return null;
            }
            String room = parts[0];
            int n;
            try {
                n = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            List<StatsStorage.TopEntry> top = plugin.stats().top(room);
            if (n < 1 || n > top.size()) {
                return none;
            }
            StatsStorage.TopEntry e = top.get(n - 1);
            String field = parts.length > 2 ? parts[2] : "";
            return switch (field) {
                case "time" -> Text.formatTimePrecise(e.timeMillis());
                case "players" -> e.players();
                default -> e.players() + " - " + Text.formatTimePrecise(e.timeMillis());
            };
        }
        if (params.startsWith("status_") || params.startsWith("queue_") || params.startsWith("playing_")) {
            String room = params.substring(params.indexOf('_') + 1);
            Room r = plugin.rooms().get(room);
            if (r == null) {
                return none;
            }
            Game game = plugin.games().game(r);
            if (params.startsWith("status_")) {
                return plugin.messages().raw("state." + game.state().name().toLowerCase());
            }
            return String.valueOf(params.startsWith("queue_") ? game.queueSize() : game.playerCount());
        }
        return null;
    }
}
