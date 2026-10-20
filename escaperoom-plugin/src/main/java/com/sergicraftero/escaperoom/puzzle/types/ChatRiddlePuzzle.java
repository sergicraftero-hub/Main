package com.sergicraftero.escaperoom.puzzle.types;

import com.sergicraftero.escaperoom.config.Messages;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.puzzle.AbstractPuzzle;
import com.sergicraftero.escaperoom.util.BlockPos;
import com.sergicraftero.escaperoom.util.Text;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Riddle answered through chat. Answers are compared ignoring case, accents and punctuation.
 *
 * <pre>
 * type: riddle
 * question: "<i>Soy plata que corre...</i>"
 * answers: [mercurio, azogue]
 * announce: true
 * </pre>
 */
public final class ChatRiddlePuzzle extends AbstractPuzzle {

    private final String question;
    private final Set<String> answers = new HashSet<>();
    private final boolean announce;

    public ChatRiddlePuzzle(String roomId, String id, ConfigurationSection s, Logger logger) {
        super(id, "riddle", s, logger);
        this.question = s.getString("question", "");
        this.announce = s.getBoolean("announce", false);
        s.getStringList("answers").forEach(a -> answers.add(Text.normalize(a)));
    }

    /** Thread-safe: called from the async chat thread. */
    public boolean accepts(String message) {
        String normalized = Text.normalize(message);
        if (answers.contains(normalized)) {
            return true;
        }
        for (String answer : answers) {
            if ((" " + normalized + " ").contains(" " + answer + " ") && normalized.split(" ").length <= 4) {
                return true;
            }
        }
        return false;
    }

    public String question() {
        return question;
    }

    @Override
    public Set<BlockPos> interactables() {
        return Set.of();
    }

    @Override
    public void reset(Game game) {
    }

    @Override
    public void onActivate(Game game) {
        if (announce && !question.isEmpty()) {
            game.msg("puzzle-riddle", Messages.phc("question", Messages.parse(question)));
        }
    }
}
