package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.action.Actions;
import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Msg;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Simon says": lamps flash a sequence that grows every round; players repeat it on the buttons.
 * <pre>
 * type: simon
 * buttons: "@lab.simon.button.*"
 * lamps: "@lab.simon.lamp.*"     # same count and order as buttons
 * start: "@lab.simon.start"       # block that plays the sequence (again)
 * rounds: 5
 * seed: 7                         # fixed sequence for a given seed (hints can rely on it)
 * </pre>
 */
public final class SimonPuzzle extends Puzzle {

    private List<Block> buttons;
    private List<Block> lamps;
    private List<Block> start;
    private final List<Integer> sequence = new ArrayList<>();
    private int round = 1;
    private int input;
    private boolean playing;

    public SimonPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        buttons = game.blocks(list("buttons"));
        lamps = game.blocks(list("lamps"));
        start = game.blocks(list("start"));
        own(buttons);
        own(start);
        int rounds = Math.max(1, integer("rounds", 5));
        Random r = new Random(integer("seed", 7));
        for (int i = 0; i < rounds; i++) {
            sequence.add(r.nextInt(Math.max(1, buttons.size())));
        }
        lamps.forEach(l -> Actions.setLit(l, false));
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (start.contains(b)) {
            play();
            return !b.getType().name().endsWith("_BUTTON");
        }
        int index = buttons.indexOf(b);
        if (index < 0 || playing) {
            return true;
        }
        flash(index, 6);
        if (sequence.get(input) == index) {
            input++;
            if (input >= round) {
                round++;
                input = 0;
                if (round > sequence.size()) {
                    solve(p);
                    return !b.getType().name().endsWith("_BUTTON");
                }
                Msg.actionBar(p, "puzzle.simon-round", "round", round, "total", sequence.size());
                game.later(this::play, 30);
            }
        } else {
            input = 0;
            round = 1;
            tell(p, "wrong-message", "puzzle.simon-wrong");
            fail(p);
        }
        return !b.getType().name().endsWith("_BUTTON");
    }

    private void play() {
        if (playing || !isActive()) {
            return;
        }
        playing = true;
        input = 0;
        for (int i = 0; i < round; i++) {
            int idx = sequence.get(i);
            game.later(() -> flash(idx, 10), 16L * i + 5);
        }
        game.later(() -> playing = false, 16L * round + 10);
    }

    private void flash(int index, long ticks) {
        if (index < lamps.size()) {
            Block lamp = lamps.get(index);
            Actions.setLit(lamp, true);
            game.later(() -> Actions.setLit(lamp, false), ticks);
        }
        Block b = index < buttons.size() ? buttons.get(index) : null;
        if (b != null) {
            float[] pitches = {0.7f, 0.9f, 1.05f, 1.25f, 1.4f, 1.6f, 1.8f, 2.0f};
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitches[index % pitches.length]);
        }
    }

    @Override
    public double progress() {
        return sequence.isEmpty() ? -1 : (double) (round - 1) / sequence.size();
    }
}
