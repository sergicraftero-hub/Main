package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.template.Template;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
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
        for (Block[] row : cells) {
            for (Block b : row) {
                Actions.setLit(b, true);
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
        Block b = cells[r][c];
        Actions.setLit(b, !lit(b));
    }

    private static boolean lit(Block b) {
        return b.getBlockData() instanceof Lightable l && l.isLit();
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
        for (Block[] row : cells) {
            for (Block b : row) {
                if (!lit(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public double progress() {
        if (cells == null) {
            return -1;
        }
        int n = 0;
        for (Block[] row : cells) {
            for (Block b : row) {
                if (lit(b)) {
                    n++;
                }
            }
        }
        return (double) n / (w * h);
    }
}
