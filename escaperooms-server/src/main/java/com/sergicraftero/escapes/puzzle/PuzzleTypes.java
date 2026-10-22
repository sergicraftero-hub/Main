package com.sergicraftero.escapes.puzzle;

import com.sergicraftero.escapes.puzzle.types.AnswerPuzzle;
import com.sergicraftero.escapes.puzzle.types.ChoicePuzzle;
import com.sergicraftero.escapes.puzzle.types.DeliverPuzzle;
import com.sergicraftero.escapes.puzzle.types.FramesPuzzle;
import com.sergicraftero.escapes.puzzle.types.GatePuzzle;
import com.sergicraftero.escapes.puzzle.types.InteractPuzzle;
import com.sergicraftero.escapes.puzzle.types.KeypadPuzzle;
import com.sergicraftero.escapes.puzzle.types.LeversPuzzle;
import com.sergicraftero.escapes.puzzle.types.LightsOutPuzzle;
import com.sergicraftero.escapes.puzzle.types.MelodyPuzzle;
import com.sergicraftero.escapes.puzzle.types.MemoryPuzzle;
import com.sergicraftero.escapes.puzzle.types.ObtainPuzzle;
import com.sergicraftero.escapes.puzzle.types.PlatesPuzzle;
import com.sergicraftero.escapes.puzzle.types.ReachPuzzle;
import com.sergicraftero.escapes.puzzle.types.SequencePuzzle;
import com.sergicraftero.escapes.puzzle.types.SimonPuzzle;
import com.sergicraftero.escapes.puzzle.types.SlidePuzzle;
import com.sergicraftero.escapes.room.PuzzleDef;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** Puzzle type registry (the {@code type:} key in room YAML). */
public final class PuzzleTypes {

    private static final Map<String, Function<PuzzleDef, Puzzle>> TYPES = new TreeMap<>();

    static {
        TYPES.put("interact", InteractPuzzle::new);
        TYPES.put("keypad", KeypadPuzzle::new);
        TYPES.put("levers", LeversPuzzle::new);
        TYPES.put("sequence", SequencePuzzle::new);
        TYPES.put("simon", SimonPuzzle::new);
        TYPES.put("plates", PlatesPuzzle::new);
        TYPES.put("frames", FramesPuzzle::new);
        TYPES.put("deliver", DeliverPuzzle::new);
        TYPES.put("answer", AnswerPuzzle::new);
        TYPES.put("reach", ReachPuzzle::new);
        TYPES.put("obtain", ObtainPuzzle::new);
        TYPES.put("gate", GatePuzzle::new);
        TYPES.put("lightsout", LightsOutPuzzle::new);
        TYPES.put("melody", MelodyPuzzle::new);
        TYPES.put("choice", ChoicePuzzle::new);
        TYPES.put("memory", MemoryPuzzle::new);
        TYPES.put("slide", SlidePuzzle::new);
    }

    private PuzzleTypes() {
    }

    public static Puzzle create(PuzzleDef def) {
        Function<PuzzleDef, Puzzle> f = TYPES.get(def.type());
        return f == null ? null : f.apply(def);
    }

    public static Set<String> names() {
        return TYPES.keySet();
    }
}
