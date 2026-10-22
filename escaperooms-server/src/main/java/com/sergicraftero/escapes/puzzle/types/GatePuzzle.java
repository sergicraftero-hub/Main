package com.sergicraftero.escapes.puzzle.types;

import com.sergicraftero.escapes.puzzle.Puzzle;
import com.sergicraftero.escapes.room.PuzzleDef;

/**
 * No interaction: solves itself as soon as its {@code requires} are solved. Used to join branches
 * ("the three seals are broken → the crypt opens") and run actions at that moment.
 */
public final class GatePuzzle extends Puzzle {

    public GatePuzzle(PuzzleDef def) {
        super(def);
    }

    @Override
    protected void setup() {
    }
}
