package com.sergicraftero.escaperoom.action;

import com.sergicraftero.escaperoom.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Ordered list of actions, each with an optional delay in ticks. */
public final class ActionList {

    public static final ActionList EMPTY = new ActionList(List.of());

    private record Entry(long delay, RoomAction action) {
    }

    private final List<Entry> entries;

    private ActionList(List<Entry> entries) {
        this.entries = entries;
    }

    public static ActionList parse(List<ConfigurationSection> sections, Logger logger, String context) {
        List<Entry> list = new ArrayList<>();
        for (ConfigurationSection s : sections) {
            try {
                RoomAction action = Actions.parse(s);
                list.add(new Entry(Math.max(0, s.getLong("delay", 0)), action));
            } catch (Exception e) {
                logger.warning("[" + context + "] Invalid action " + s.getValues(false) + ": " + e.getMessage());
            }
        }
        return new ActionList(list);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void run(Game game, @Nullable Player trigger) {
        int runId = game.runId();
        for (Entry entry : entries) {
            if (entry.delay <= 0) {
                safeRun(game, trigger, entry.action);
            } else {
                Bukkit.getScheduler().runTaskLater(game.plugin(), () -> {
                    if (game.runId() == runId && game.isRunning()) {
                        safeRun(game, trigger, entry.action);
                    }
                }, entry.delay);
            }
        }
    }

    private static void safeRun(Game game, Player trigger, RoomAction action) {
        try {
            action.execute(game, trigger != null && trigger.isOnline() ? trigger : null);
        } catch (Exception e) {
            game.plugin().getLogger().warning("Action failed in room " + game.room().id() + ": " + e);
        }
    }
}
