package com.sergicraftero.escapes.util;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;

/** A per-player sidebar whose lines are components (no flicker: only changed lines are rewritten). */
public final class Sidebar {

    private static final String[] ENTRIES = new String[15];

    static {
        String codes = "0123456789abcdef";
        for (int i = 0; i < ENTRIES.length; i++) {
            ENTRIES[i] = "§" + codes.charAt(i) + "§r";
        }
    }

    private final Scoreboard board;
    private final Objective objective;
    private int lines;

    public Sidebar(Player player, Component title) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("er_side", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Cosmetic.apply(() -> objective.numberFormat(NumberFormat.blank())); // hide the red score numbers
        player.setScoreboard(board);
    }

    public Scoreboard board() {
        return board;
    }

    public void title(Component title) {
        objective.displayName(title);
    }

    public void lines(List<Component> content) {
        int n = Math.min(content.size(), ENTRIES.length);
        for (int i = 0; i < n; i++) {
            String entry = ENTRIES[i];
            Team team = board.getTeam("l" + i);
            if (team == null) {
                team = board.registerNewTeam("l" + i);
                team.addEntry(entry);
            }
            Component line = content.get(i);
            if (!line.equals(team.prefix())) {
                team.prefix(line);
            }
            objective.getScore(entry).setScore(n - i);
        }
        for (int i = n; i < lines; i++) {
            board.resetScores(ENTRIES[i]);
        }
        lines = n;
    }
}
