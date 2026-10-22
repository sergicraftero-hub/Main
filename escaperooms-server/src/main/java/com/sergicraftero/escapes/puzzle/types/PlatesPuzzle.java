package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pressure plates that must all be held down at once — by players standing on them or by weights.
 * Small teams use weights (right-click a plate holding the weight item; right-click again to take it back),
 * so the puzzle works from 1 to 10 players.
 * <pre>
 * type: plates
 * plates: "@vault.plate.*"
 * weight-item: weight        # room item usable as a weight (optional)
 * hold: 3s                   # how long they must stay pressed
 * </pre>
 */
public final class PlatesPuzzle extends Puzzle {

    private List<Block> plates;
    private final Map<Block, BlockDisplay> weights = new HashMap<>();
    private long pressedSince;

    public PlatesPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        plates = game.blocks(list("plates"));
        own(plates);
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right) {
            return false; // stepping (PHYSICAL) is not routed here
        }
        String weight = str("weight-item", null);
        if (weight == null) {
            return true;
        }
        BlockDisplay placed = weights.remove(b);
        if (placed != null) {
            placed.remove();
            game.giveItem(p, weight, 1);
            p.playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 0.8f, 1f);
            return true;
        }
        if (!weight.equals(Keys.itemId(hand))) {
            Msg.actionBar(p, "puzzle.plate-need-weight");
            return true;
        }
        game.consume(p, weight);
        Location l = b.getLocation().add(0.2, 0.05, 0.2);
        BlockDisplay d = l.getWorld().spawn(l, BlockDisplay.class, e -> {
            e.setBlock(Material.valueOf(str("weight-block", "IRON_BLOCK")).createBlockData());
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
        game.tagEntity(d);
        weights.put(b, d);
        p.playSound(b.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.2f);
        return true;
    }

    private int pressed() {
        int n = 0;
        for (Block plate : plates) {
            if (weights.containsKey(plate) || occupied(plate)) {
                n++;
            }
        }
        return n;
    }

    private boolean occupied(Block plate) {
        for (Player p : game.players()) {
            Block feet = p.getLocation().getBlock();
            org.bukkit.entity.Entity body = p; // Entity#isOnGround: the Player override is deprecated
            if (feet.equals(plate) && body.isOnGround()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        int n = pressed();
        long hold = com.sergicraftero.escapes.util.Yml.ticks(def.section().get("hold"), 40) * 50;
        if (n >= plates.size()) {
            if (pressedSince == 0) {
                pressedSince = System.currentTimeMillis();
                game.players().forEach(pl -> pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f));
            } else if (System.currentTimeMillis() - pressedSince >= hold) {
                Player any = game.players().isEmpty() ? null : game.players().get(0);
                solve(any);
            }
        } else {
            if (pressedSince != 0) {
                game.players().forEach(pl -> pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f));
            }
            pressedSince = 0;
        }
        if (n > 0 && n < plates.size()) {
            for (Player p : game.players()) {
                for (Block plate : plates) {
                    if (p.getLocation().distanceSquared(plate.getLocation()) < 64) {
                        Msg.actionBar(p, "puzzle.plates-count", "pressed", n, "total", plates.size());
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onSolved() {
        // weights stay on the plates (they're part of the scenery now)
    }

    @Override
    public void cleanup() {
        weights.values().forEach(BlockDisplay::remove);
        weights.clear();
    }

    @Override
    public double progress() {
        return plates == null || plates.isEmpty() ? -1 : (double) pressed() / plates.size();
    }
}
