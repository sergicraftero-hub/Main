package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.template.Refs;
import com.sergicraftero.escapes.util.Keys;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Yml;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Item frames that must hold the right item, turned the right way. Covers "put the 4 gems in their sockets",
 * "turn the dials to the right symbols", "rotate the pipes/mirrors".
 * <pre>
 * type: frames
 * frames:
 *   - {at: "@gallery.frame.1", item: ruby, rotation: 2}   # rotation 0-7, omit = any
 *   - {at: "@gallery.frame.2", item: sapphire}
 *   - {at: "@pipes.3", start-item: pipe, start-rotation: 3, rotation: 1, locked-item: true}
 * </pre>
 * Players put items in by right-clicking an empty frame, rotate by right-clicking a full one, and take the
 * item back by left-clicking (unless {@code locked-item}).
 */
public final class FramesPuzzle extends Puzzle {

    private record Slot(ItemFrame frame, String item, int rotation, boolean lockedItem) {
    }

    private final List<Slot> slots = new ArrayList<>();

    public FramesPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        for (ConfigurationSection f : Yml.sections(def.section(), "frames")) {
            String at = f.getString("at");
            Block b = game.block(at);
            String facing = f.getString("facing", Refs.facing(at, game.template()));
            BlockFace face = facing == null ? BlockFace.SOUTH : BlockFace.valueOf(facing.toUpperCase(Locale.ROOT));
            Location l = b.getLocation();
            ItemFrame frame = l.getWorld().spawn(l, ItemFrame.class, fr -> {
                try {
                    fr.setFacingDirection(face, true);
                } catch (IllegalArgumentException ex) {
                    fr.setFacingDirection(BlockFace.SOUTH, true); // only test servers refuse floor/ceiling frames
                }
                fr.setFixed(true);
                fr.setVisible(!f.getBoolean("invisible", false));
                String start = f.getString("start-item");
                if (start != null) {
                    ItemStack it = game.item(start, 1);
                    if (it != null) {
                        fr.setItem(it, false);
                    }
                }
                fr.setRotation(Rotation.values()[Math.floorMod(f.getInt("start-rotation", 0), 8)]);
            });
            game.tagEntity(frame);
            slots.add(new Slot(frame, f.getString("item"), f.getInt("rotation", -1), f.getBoolean("locked-item", false)));
        }
    }

    @Override
    public boolean ownsEntity(Entity e) {
        for (Slot s : slots) {
            if (s.frame().equals(e)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onEntity(Player p, Entity e, boolean right, ItemStack hand) {
        Slot slot = null;
        for (Slot s : slots) {
            if (s.frame().equals(e)) {
                slot = s;
            }
        }
        if (slot == null) {
            return true;
        }
        ItemFrame frame = slot.frame();
        ItemStack current = frame.getItem();
        boolean empty = current == null || current.getType().isAir();
        if (!right) {
            if (!empty && !slot.lockedItem()) {
                frame.setItem(null, false);
                game.give(p, current);
                p.playSound(frame.getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
            }
            return true;
        }
        if (empty) {
            if (hand == null || hand.getType().isAir() || Keys.itemId(hand) == null) {
                Msg.actionBar(p, "puzzle.frame-empty");
                return true;
            }
            ItemStack one = hand.clone();
            one.setAmount(1);
            p.getInventory().setItemInMainHand(com.sergicraftero.escapes.game.Game.minus(hand, 1));
            frame.setItem(one, false);
            frame.setRotation(Rotation.NONE);
            p.playSound(frame.getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
        } else {
            frame.setRotation(frame.getRotation().rotateClockwise());
            p.playSound(frame.getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 1f);
        }
        if (solvedNow()) {
            solve(p);
        }
        return true;
    }

    private boolean solvedNow() {
        for (Slot s : slots) {
            if (!ok(s)) {
                return false;
            }
        }
        return !slots.isEmpty();
    }

    private boolean ok(Slot s) {
        ItemStack it = s.frame().getItem();
        if (s.item() != null && (it == null || !s.item().equals(Keys.itemId(it)))) {
            return false;
        }
        return s.rotation() < 0 || s.frame().getRotation().ordinal() == Math.floorMod(s.rotation(), 8);
    }

    @Override
    public void cleanup() {
        slots.forEach(s -> s.frame().remove());
    }

    @Override
    public double progress() {
        if (slots.isEmpty()) {
            return -1;
        }
        int n = 0;
        for (Slot s : slots) {
            if (ok(s)) {
                n++;
            }
        }
        return (double) n / slots.size();
    }
}
