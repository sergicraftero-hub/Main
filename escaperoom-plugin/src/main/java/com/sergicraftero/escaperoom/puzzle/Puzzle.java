package com.sergicraftero.escaperoom.puzzle;

import com.sergicraftero.escaperoom.action.ActionList;
import com.sergicraftero.escaperoom.game.Game;
import com.sergicraftero.escaperoom.util.BlockPos;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.Set;

/**
 * Common contract for every puzzle. A puzzle definition lives in a room; its runtime state is
 * reset at the start of each game with {@link #reset(Game)}.
 *
 * <p>To add a new puzzle type implement this interface (usually by extending
 * {@link AbstractPuzzle}) and register a factory in {@link PuzzleRegistry}.</p>
 */
public interface Puzzle {

    String id();

    String type();

    String displayName();

    /** Ids of puzzles that must be solved before this one becomes active. */
    List<String> requires();

    /** Progressive hints, from vague to explicit. */
    List<String> hints();

    ActionList onSolve();

    PuzzleState state();

    void state(PuzzleState state);

    /** Blocks players may right-click; interactions on them are routed to this puzzle. */
    Set<BlockPos> interactables();

    /** Blocks where players may place blocks while this puzzle is active. */
    default Set<BlockPos> placeables() {
        return Set.of();
    }

    /** Clears runtime state for a new game. */
    void reset(Game game);

    default void onActivate(Game game) {
    }

    default void onInteract(Game game, Player player, PlayerInteractEvent event) {
    }

    default void onBlockPlace(Game game, Player player, BlockPlaceEvent event) {
    }

    /** Called every tick while the puzzle is active. */
    default void tick(Game game, long tick) {
    }

    /** Called when the game switches between solo and team mode. */
    default void onModeChange(Game game) {
    }
}
