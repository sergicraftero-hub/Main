package com.sergicraftero.escaperoom.action;

import com.sergicraftero.escaperoom.game.Game;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Something that happens in a room: open a door, play a sound, give an item... */
@FunctionalInterface
public interface RoomAction {

    /**
     * @param game    running game
     * @param trigger player that caused the action, if any
     */
    void execute(Game game, @Nullable Player trigger);
}
