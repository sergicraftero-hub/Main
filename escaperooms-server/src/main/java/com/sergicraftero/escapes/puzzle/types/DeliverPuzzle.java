package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Put items into something: ingredients in a cauldron, gems on an altar, fuses in a panel.
 * <pre>
 * type: deliver
 * at: "@lab.cauldron"
 * items: [salt, sulfur, mercury]
 * ordered: true          # wrong order → everything comes back out
 * </pre>
 * Players right-click the block while holding an item.
 */
public final class DeliverPuzzle extends Puzzle {

    private List<Block> targets;
    private List<String> items;
    private final List<String> delivered = new ArrayList<>();
    private final List<Player> deliveredBy = new ArrayList<>();

    public DeliverPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        targets = game.blocks(list("at"));
        own(targets);
        items = list("items");
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right) {
            return false;
        }
        String id = Keys.itemId(hand);
        if (id == null) {
            if (delivered.isEmpty()) {
                tell(p, "empty-hand-message", "puzzle.deliver-hint");
            } else {
                Msg.actionBar(p, "puzzle.progress", "done", delivered.size(), "total", items.size());
            }
            return true;
        }
        boolean ordered = bool("ordered", false);
        List<String> missing = new ArrayList<>(items);
        delivered.forEach(missing::remove);
        if (!missing.contains(id)) {
            tell(p, "wrong-item-message", "puzzle.deliver-wrong");
            p.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return true;
        }
        if (ordered && !items.get(delivered.size()).equals(id)) {
            // wrong order: the mixture spoils, everything comes back
            game.consume(p, id);
            game.giveItem(p, id, 1);
            for (int i = 0; i < delivered.size(); i++) {
                Player owner = deliveredBy.get(i).isOnline() ? deliveredBy.get(i) : p;
                game.giveItem(owner, delivered.get(i), 1);
            }
            delivered.clear();
            deliveredBy.clear();
            b.getWorld().spawnParticle(Particle.LARGE_SMOKE, b.getLocation().add(0.5, 1, 0.5), 20, 0.3, 0.3, 0.3, 0.02);
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.8f);
            tell(p, "wrong-order-message", "puzzle.deliver-order");
            fail(p);
            return true;
        }
        game.consume(p, id);
        delivered.add(id);
        deliveredBy.add(p);
        b.getWorld().spawnParticle(Particle.WITCH, b.getLocation().add(0.5, 1, 0.5), 15, 0.3, 0.3, 0.3, 0.02);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.2f);
        if (delivered.size() >= items.size()) {
            solve(p);
        } else {
            Msg.actionBar(p, "puzzle.progress", "done", delivered.size(), "total", items.size());
        }
        return true;
    }

    @Override
    public double progress() {
        return items == null || items.isEmpty() ? -1 : (double) delivered.size() / items.size();
    }
}
