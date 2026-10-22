package com.sergicraftero.escapes.action;

import com.sergicraftero.escapes.game.Game;
import org.bukkit.entity.Player;

/** Something a room does: open a door, play a sound, give an item... Defined in room YAML. */
@FunctionalInterface
public interface Action {

    /**
     * @param trigger the player who caused it, or {@code null} (timeline, start of game)
     */
    void run(Game game, Player trigger);
}
