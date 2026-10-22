package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * "Lights out" on a wall/floor grid of lamps: clicking a lamp toggles it and its 4 neighbours; turn them all on.
 * The start pattern is made by pressing cells of a solved board, so it is always solvable.
 * <pre>
 * type: lightsout
 * grid: "@lab.grid"        # a flat box marker of lamps (copper bulbs or redstone lamps)
 * scramble: [1, 5, 9, 13]  # 1-based cells pressed to build the start pattern (row by row)
 * reset: "@lab.grid.reset" # optional button that restores the start pattern
 * </pre>
 */
public final class LightsOutPuzzle extends Puzzle {

    private Block[][] cells;
    /** The puzzle's own state: the lamps only display it, so nothing else touching them can break it. */
    private boolean[][] on;
    private int w;
    private int h;
    private List<Block> reset;

    public LightsOutPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        Template.Marker m = Refs.box(str("grid", ""), game.template());
        if (m == null) {
            throw new IllegalArgumentException("lightsout needs a grid box marker");
        }
        int dx = m.x2() - m.x() + 1;
        int dy = m.y2() - m.y() + 1;
        int dz = m.z2() - m.z() + 1;
        // the flat axis is ignored; rows run top→bottom (walls) or north→south (floors)
        List<List<Block>> rows = new ArrayList<>();
        if (dy == 1) {
            for (int z = m.z(); z <= m.z2(); z++) {
                List<Block> row = new ArrayList<>();
                for (int x = m.x(); x <= m.x2(); x++) {
                    row.add(game.instance().block(new int[]{x, m.y(), z}));
                }
                rows.add(row);
            }
        } else {
            boolean alongX = dx >= dz;
            for (int y = m.y2(); y >= m.y(); y--) {
                List<Block> row = new ArrayList<>();
                if (alongX) {
                    for (int x = m.x(); x <= m.x2(); x++) {
                        row.add(game.instance().block(new int[]{x, y, m.z()}));
                    }
                } else {
                    for (int z = m.z(); z <= m.z2(); z++) {
                        row.add(game.instance().block(new int[]{m.x(), y, z}));
                    }
                }
                rows.add(row);
            }
        }
        h = rows.size();
        w = rows.get(0).size();
        cells = new Block[h][w];
        on = new boolean[h][w];
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                cells[r][c] = rows.get(r).get(c);
                own(cells[r][c]);
            }
        }
        reset = game.blocks(list("reset"));
        own(reset);
        scramble();
    }

    private void scramble() {
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                on[r][c] = true;
                Actions.setLit(cells[r][c], true);
            }
        }
        for (String s : list("scramble")) {
            int i = Integer.parseInt(s.trim()) - 1;
            if (i >= 0 && i < w * h) {
                press(i / w, i % w);
            }
        }
    }

    private void press(int r, int c) {
        toggle(r, c);
        toggle(r - 1, c);
        toggle(r + 1, c);
        toggle(r, c - 1);
        toggle(r, c + 1);
    }

    private void toggle(int r, int c) {
        if (r < 0 || c < 0 || r >= h || c >= w) {
            return;
        }
        on[r][c] = !on[r][c];
        Actions.setLit(cells[r][c], on[r][c]);
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (reset.contains(b)) {
            scramble();
            p.playSound(b.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.7f, 1.2f);
            return !b.getType().name().endsWith("_BUTTON");
        }
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                if (cells[r][c].equals(b)) {
                    press(r, c);
                    p.playSound(b.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, 0.8f, 1f);
                    if (allLit()) {
                        solve(p);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private boolean allLit() {
        return litCount() == w * h;
    }

    private int litCount() {
        int n = 0;
        for (boolean[] row : on) {
            for (boolean b : row) {
                if (b) {
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    public double progress() {
        return on == null ? -1 : (double) litCount() / (w * h);
    }
}
