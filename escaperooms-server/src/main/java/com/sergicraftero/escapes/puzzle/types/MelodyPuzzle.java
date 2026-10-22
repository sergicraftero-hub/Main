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
 * Play a melody on blocks (bells, note blocks, crystals). A "listen" block plays the target tune.
 * <pre>
 * type: melody
 * keys: "@music.key.*"        # the playable blocks, low to high
 * notes: [0, 2, 4, 5, 7, 9, 11, 12]   # pitch of each key in semitones (0-24)
 * melody: [1, 3, 5, 3, 1]     # 1-based key indexes
 * listen: "@music.box"         # optional: plays the melody
 * sound: block.note_block.harp
 * </pre>
 */
public final class MelodyPuzzle extends Puzzle {

    private List<Block> keys;
    private List<Block> listen;
    private final List<Integer> notes = new ArrayList<>();
    private final List<Integer> melody = new ArrayList<>();
    private int progress;
    private boolean playing;

    public MelodyPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        keys = game.blocks(list("keys"));
        listen = game.blocks(list("listen"));
        own(keys);
        own(listen);
        List<String> n = list("notes");
        for (int i = 0; i < keys.size(); i++) {
            notes.add(i < n.size() ? Integer.parseInt(n.get(i).trim()) : i * 2);
        }
        for (String s : list("melody")) {
            melody.add(Integer.parseInt(s.trim()) - 1);
        }
    }

    private Sound sound() {
        Sound s = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(str("sound", "block.note_block.harp")));
        return s == null ? Sound.BLOCK_NOTE_BLOCK_HARP : s;
    }

    private static float pitch(int semitones) {
        return (float) Math.pow(2.0, (Math.max(0, Math.min(24, semitones)) - 12) / 12.0);
    }

    private void playKey(int index) {
        Block b = keys.get(index);
        b.getWorld().playSound(b.getLocation(), sound(), 1f, pitch(notes.get(index)));
        b.getWorld().spawnParticle(Particle.NOTE, b.getLocation().add(0.5, 1.2, 0.5), 1, 0, 0, 0, notes.get(index) / 24.0);
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (listen.contains(b)) {
            if (!playing) {
                playing = true;
                for (int i = 0; i < melody.size(); i++) {
                    int key = melody.get(i);
                    game.later(() -> playKey(key), 10L * i + 2);
                }
                game.later(() -> playing = false, 10L * melody.size() + 5);
            }
            return true;
        }
        int index = keys.indexOf(b);
        if (index < 0 || melody.isEmpty()) {
            return true;
        }
        playKey(index);
        if (melody.get(progress) == index) {
            progress++;
            if (progress >= melody.size()) {
                solve(p);
            }
        } else {
            progress = melody.get(0) == index ? 1 : 0;
            if (bool("show-mistakes", true)) {
                Msg.actionBar(p, "puzzle.melody-wrong");
            }
        }
        return true;
    }

    @Override
    public double progress() {
        return melody.isEmpty() ? -1 : (double) progress / melody.size();
    }
}
