package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;
import com.sergicraftero.escapes.util.Msg;
import com.sergicraftero.escapes.util.Text;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A riddle answered in chat. Clicking the block (a talking door, a sphinx, a terminal) shows the question and
 * captures the next chat line of that player as the answer (hidden from the rest). Without {@code at}, any team
 * message containing the answer solves it.
 * <pre>
 * type: answer
 * at: "@library.sphinx"
 * question: "Tengo ciudades, pero no casas..."
 * answers: ["mapa", "un mapa"]
 * contains: false      # true: the answer may appear inside a longer sentence
 * </pre>
 */
public final class AnswerPuzzle extends Puzzle {

    private List<Block> blocks;
    private final List<String> answers = new ArrayList<>();

    public AnswerPuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
        blocks = game.blocks(list("at"));
        own(blocks);
        for (String a : list("answers")) {
            answers.add(Text.normalize(a));
        }
        if (answers.isEmpty()) {
            answers.add(Text.normalize(str("answer", "")));
        }
    }

    @Override
    public boolean onInteract(Player p, Block b, boolean right, ItemStack hand) {
        if (!right) {
            return false;
        }
        for (String line : list("question")) {
            p.sendMessage(Msg.parse(line));
        }
        Msg.send(p, "puzzle.answer-prompt", "seconds", 60);
        p.playSound(b.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        game.enterAnswerMode(p, id(), 60);
        return true;
    }

    @Override
    public boolean onChat(Player p, String message, boolean captured) {
        if (!captured && !blocks.isEmpty()) {
            return false; // must be asked at its block
        }
        String n = Text.normalize(message);
        boolean contains = bool("contains", false);
        for (String a : answers) {
            if (a.isEmpty()) {
                continue;
            }
            if (n.equals(a) || ((contains || !captured) && (" " + n + " ").contains(" " + a + " "))) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
                solve(p);
                return true;
            }
        }
        if (captured) {
            Msg.send(p, "puzzle.answer-wrong", "answer", Msg.escape(message));
            fail(p);
        }
        return false;
    }
}
