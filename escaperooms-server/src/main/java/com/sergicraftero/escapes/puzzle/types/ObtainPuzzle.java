package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Have items: solved when the team holds every listed item (or {@code count} of them). Good for
 * "find the 4 pages of the diary" without having to deliver them anywhere.
 * <pre>
 * type: obtain
 * items: [page_1, page_2, page_3, page_4]
 * count: 4            # default: all
 * </pre>
 */
public final class ObtainPuzzle extends Puzzle {

    private List<String> items;

    public ObtainPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        items = list("items");
        if (items.isEmpty()) {
            items = list("item");
        }
    }

    @Override
    public void onActivate() {
        check(null);
    }

    @Override
    public void onInventory(Player p) {
        check(p);
    }

    @Override
    public void tick() {
        check(null);
    }

    private int held() {
        int n = 0;
        for (String id : items) {
            if (game.teamHas(id)) {
                n++;
            }
        }
        return n;
    }

    private void check(Player p) {
        int need = Math.min(items.size(), integer("count", items.size()));
        if (need > 0 && held() >= need) {
            Player by = p;
            if (by == null) {
                for (Player pl : game.players()) {
                    for (String id : items) {
                        if (Game.has(pl, id)) {
                            by = pl;
                        }
                    }
                }
            }
            solve(by);
        }
    }

    @Override
    public double progress() {
        return items == null || items.isEmpty() ? -1 : (double) held() / items.size();
    }
}
