package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.game.Game;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Click a block. Optionally with an item in hand (a key in a lock, a crowbar on a crate), sneaking, or
 * several blocks that all have to be clicked (in any order).
 * <pre>
 * type: interact
 * at: "@study.lock"          # one or more refs
 * item: brass_key            # optional: must hold it
 * consume: true              # the item is used up
 * all: false                 # true: every block must be clicked once
 * wrong-item-message: "..."
 * </pre>
 */
public final class InteractPuzzle extends Puzzle {

    private List<Block> blocks;
    private final java.util.Set<Block> clicked = new java.util.HashSet<>();

    public InteractPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        blocks = game.blocks(list("at"));
        own(blocks);
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right && !bool("left-click", false)) {
            return false;
        }
        String item = str("item", null);
        if (item != null && !item.equals(Keys.itemId(hand))) {
            if (Game.has(p, item)) {
                Msg.actionBar(p, "puzzle.hold-item");
            } else {
                tell(p, "wrong-item-message", "puzzle.need-item");
            }
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 0.8f, 1f);
            return true;
        }
        if (bool("sneak", false) && !p.isSneaking()) {
            tell(p, "sneak-message", "puzzle.need-sneak");
            return true;
        }
        if (bool("all", false)) {
            if (clicked.add(b)) {
                p.playSound(b.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.8f + clicked.size() * 0.15f);
            }
            if (clicked.size() < blocks.size()) {
                Msg.actionBar(p, "puzzle.progress", "done", clicked.size(), "total", blocks.size());
                return true;
            }
        }
        if (item != null && bool("consume", true)) {
            game.consume(p, item);
        }
        solve(p);
        return true;
    }

    @Override
    public double progress() {
        return bool("all", false) && blocks != null && !blocks.isEmpty() ? (double) clicked.size() / blocks.size() : -1;
    }
}
