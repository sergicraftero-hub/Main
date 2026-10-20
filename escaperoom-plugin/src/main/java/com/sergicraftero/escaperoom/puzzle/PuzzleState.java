package com.sergicraftero.escaperoom.puzzle;

public enum PuzzleState {
    /** Requirements not met yet. */
    LOCKED,
    /** Can be solved now. */
    ACTIVE,
    /** Done. */
    SOLVED
}
