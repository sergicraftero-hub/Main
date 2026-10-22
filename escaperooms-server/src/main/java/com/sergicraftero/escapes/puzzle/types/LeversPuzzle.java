package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Levers that must end up in a pattern. Solved as soon as the pattern matches.
 * <pre>
 * type: levers
 * levers: "@obs.lever.*"
 * pattern: "10110"        # 1 = up/on, in lever order
 * lamps: "@obs.lamp.*"     # optional: lamp i lights when lever i is right (feedback)
 * </pre>
 */
public final class LeversPuzzle extends Puzzle {

    private List<Block> levers;
    private List<Block> lamps;
    private String pattern;

    public LeversPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        levers = game.blocks(list("levers"));
        lamps = game.blocks(list("lamps"));
        pattern = str("pattern", "1".repeat(levers.size()));
        own(levers);
        for (Block b : levers) {
            set(b, false);
        }
        updateLamps();
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right || !(b.getBlockData() instanceof Powerable pw)) {
            return true;
        }
        set(b, !pw.isPowered());
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, pw.isPowered() ? 0.5f : 0.6f);
        updateLamps();
        if (matches()) {
            solve(p);
        } else if (bool("show-count", false)) {
            Msg.actionBar(p, "puzzle.levers-count", "right", correct(), "total", levers.size());
        }
        return true;
    }

    private void set(Block b, boolean on) {
        if (b.getBlockData() instanceof Powerable pw) {
            pw.setPowered(on);
            b.setBlockData(pw, false);
        }
    }

    private boolean on(Block b) {
        return b.getBlockData() instanceof Powerable pw && pw.isPowered();
    }

    private int correct() {
        int n = 0;
        for (int i = 0; i < levers.size() && i < pattern.length(); i++) {
            if (on(levers.get(i)) == (pattern.charAt(i) == '1')) {
                n++;
            }
        }
        return n;
    }

    private boolean matches() {
        return correct() == Math.min(levers.size(), pattern.length());
    }

    private void updateLamps() {
        if (lamps.isEmpty()) {
            return;
        }
        boolean perLever = bool("lamp-per-lever", true);
        int right = correct();
        for (int i = 0; i < lamps.size(); i++) {
            boolean lit;
            if (perLever && i < levers.size() && i < pattern.length()) {
                lit = on(levers.get(i)) == (pattern.charAt(i) == '1');
            } else {
                lit = i < right;
            }
            com.sergicraftero.escapes.action.Actions.setLit(lamps.get(i), lit);
        }
    }

    @Override
    public double progress() {
        return levers == null || levers.isEmpty() ? -1 : (double) correct() / levers.size();
    }
}
