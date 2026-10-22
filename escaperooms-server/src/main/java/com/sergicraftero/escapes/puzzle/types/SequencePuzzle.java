package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Press blocks (buttons, bells, statues...) in a given order. A wrong press resets the sequence.
 * <pre>
 * type: sequence
 * buttons: "@hall.statue.*"
 * order: [3, 1, 4, 2]      # 1-based indexes into buttons (may repeat)
 * </pre>
 */
public final class SequencePuzzle extends Puzzle {

    private List<Block> buttons;
    private List<Integer> order;
    private int progress;

    public SequencePuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        buttons = game.blocks(list("buttons"));
        own(buttons);
        order = new ArrayList<>();
        for (String s : list("order")) {
            order.add(Integer.parseInt(s.trim()) - 1);
        }
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        int index = buttons.indexOf(b);
        if (index < 0) {
            return true;
        }
        if (order.isEmpty()) {
            return true;
        }
        float pitch = 0.6f + index * 0.15f;
        b.getWorld().playSound(b.getLocation(), soundFor(), 1f, pitch);
        if (order.get(progress) == index) {
            progress++;
            b.getWorld().spawnParticle(Particle.NOTE, b.getLocation().add(0.5, 1.1, 0.5), 1, 0, 0, 0, index / 24.0);
            if (progress >= order.size()) {
                solve(p);
            } else if (bool("show-progress", true)) {
                Msg.actionBar(p, "puzzle.progress", "done", progress, "total", order.size());
            }
        } else {
            boolean restartAtThis = order.get(0) == index;
            progress = restartAtThis ? 1 : 0;
            tell(p, "wrong-message", "puzzle.sequence-wrong");
            fail(p);
        }
        // buttons pop back by themselves; we keep the vanilla press animation for buttons only
        return !b.getType().name().endsWith("_BUTTON");
    }

    private Sound soundFor() {
        Sound s = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(str("sound", "block.note_block.bell")));
        return s == null ? Sound.BLOCK_NOTE_BLOCK_BELL : s;
    }

    @Override
    public double progress() {
        return order == null || order.isEmpty() ? -1 : (double) progress / order.size();
    }
}
